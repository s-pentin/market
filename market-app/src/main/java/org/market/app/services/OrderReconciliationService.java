package org.market.app.services;

import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.payment.model.PaymentRecordResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сверка незавершённых заказов: если заказ застрял в PENDING_PAYMENT (процесс упал
 * между платёжом и обновлением статуса), по idempotency key выясняем реальный исход
 * платежа в журнале payment-service и доводим заказ до финального состояния.
 */
@Service
public class OrderReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciliationService.class);

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final PurchaseService purchaseService;
    private final Duration grace;
    private final Duration hardTimeout;

    public OrderReconciliationService(OrderRepository orderRepository,
                                      CartItemRepository cartItemRepository,
                                      PurchaseService purchaseService,
                                      @Value("${app.order.reconciliation.grace:60s}") Duration grace,
                                      @Value("${app.order.reconciliation.hard-timeout:10m}") Duration hardTimeout) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.purchaseService = purchaseService;
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
                .onErrorResume(e -> Mono.just(Optional.empty())) // сервис недоступен — отложим на следующий проход
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return handleNotFound(order);
                    }
                    PaymentRecordResponse record = optional.get();
                    if (record.getStatus() == PaymentRecordResponse.StatusEnum.SUCCEEDED) {
                        return markPaidAndClearCart(order);
                    }
                    return orderRepository.markPaymentFailed(order.getId()).then();
                });
    }

    private Mono<Void> markPaidAndClearCart(Orders order) {
        return orderRepository.markPaid(order.getId(), LocalDateTime.now(), null)
                .then(cartItemRepository.deleteAllByUserId(order.getUserId()))
                .then();
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