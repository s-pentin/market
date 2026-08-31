package org.market.app.services;

import org.market.app.models.OrderItems;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.payment.model.PaymentRecordResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Сверка незавершённых заказов: если заказ застрял в PENDING_PAYMENT (процесс упал
 * между платёжом и обновлением статуса, либо платёж-сервис не ответил при checkout),
 * по idempotency key выясняем реальный платеж в журнале payment-service и
 * доводим заказ до финального состояния. Но только когда исход однозначен
 * ({@link PaymentStatusResult}). Неопределённый результат оставляет
 * заказ в PENDING_PAYMENT до следующего прохода.
 */
@Service
public class OrderReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciliationService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final PurchaseService purchaseService;
    private final TransactionalOperator transactionalOperator;
    private final Duration grace;
    private final Duration hardTimeout;

    public OrderReconciliationService(OrderRepository orderRepository,
                                      OrderItemRepository orderItemRepository,
                                      CartItemRepository cartItemRepository,
                                      PurchaseService purchaseService,
                                      TransactionalOperator transactionalOperator,
                                      @Value("${app.order.reconciliation.grace:60s}") Duration grace,
                                      @Value("${app.order.reconciliation.hard-timeout:10m}") Duration hardTimeout) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.purchaseService = purchaseService;
        this.transactionalOperator = transactionalOperator;
        this.grace = grace;
        this.hardTimeout = hardTimeout;
    }

    @Scheduled(fixedDelayString = "${app.order.reconciliation.interval:30000}")
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minus(grace);
        orderRepository.findAllByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff)
                .flatMap(this::reconcileOne)
                .subscribe(null, e -> log.warn("Order reconciliation failed: {}", e.getMessage()));
    }

    Mono<Void> reconcileOne(Orders order) {
        return purchaseService.checkPaymentStatus(order.getIdempotencyKey())
                .flatMap(result -> {
                    if (result instanceof PaymentStatusResult.Found(PaymentRecordResponse record)) {
                        return handleFound(order, record);
                    }
                    if (result instanceof PaymentStatusResult.NotFound) {
                        return handleNotFound(order);
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> handleFound(Orders order, PaymentRecordResponse record) {
        if (record.getStatus() == PaymentRecordResponse.StatusEnum.SUCCEEDED) {
            return markPaidAndClearCart(order, record.getPaymentId());
        }
        return orderRepository.markPaymentFailed(order.getId()).then();
    }

    /**
     * Смена статуса на PAID и очистка корзины — одна локальная транзакция (обе таблицы в БД
     * market-app). Если очистка корзины упадёт, откатится и markPaid.
     */
    private Mono<Void> markPaidAndClearCart(Orders order, Long paymentId) {
        return orderRepository.markPaid(order.getId(), LocalDateTime.now(), paymentId)
                .flatMap(rowsUpdated -> rowsUpdated > 0 ? clearOrderedItemsFromCart(order) : Mono.<Void>empty())
                .as(transactionalOperator::transactional);
    }

    /**
     * Удаляем из корзины только те позиции, что вошли в заказ
     */
    private Mono<Void> clearOrderedItemsFromCart(Orders order) {
        return orderItemRepository.findAllByOrderId(order.getId())
                .map(OrderItems::getProductId)
                .filter(Objects::nonNull)
                .collectList()
                .flatMap(productIds -> productIds.isEmpty()
                        ? Mono.empty()
                        : cartItemRepository.deleteAllByUserIdAndProductIdIn(order.getUserId(), List.copyOf(productIds)));
    }

    private Mono<Void> handleNotFound(Orders order) {
        LocalDateTime hardCutoff = LocalDateTime.now().minus(hardTimeout);
        if (order.getCreatedAt() != null && order.getCreatedAt().isBefore(hardCutoff)) {
            // Платёж так и не был зафиксирован — считаем заказ несостоявшимся, корзина остаётся.
            return orderRepository.markPaymentFailed(order.getId()).then();
        }
        return Mono.empty();
    }
}
