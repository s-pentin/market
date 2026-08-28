package org.market.app.usecases;

import org.market.app.dto.ProductsInCart;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.models.OrderItems;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Оркестрация оформления заказа в два этапа, устойчивая к сбоям между платёжом
 * и обновлением статуса заказа (см. {@link org.market.app.services.OrderReconciliationService}).
 */
@Component
public class CheckoutUseCase {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final PurchaseService purchaseService;
    private final TransactionalOperator transactionalOperator;

    public CheckoutUseCase(CartService cartService,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           CartItemRepository cartItemRepository,
                           PurchaseService purchaseService,
                           TransactionalOperator transactionalOperator) {
        this.cartService = cartService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.purchaseService = purchaseService;
        this.transactionalOperator = transactionalOperator;
    }

    public Mono<Long> execute(Long userId) {
        return cartService.getAllProductsInCart(userId)
                .flatMap(cart -> {
                    if (cart.getItems().isEmpty()) {
                        return Mono.error(new EmptyCartException());
                    }
                    return createPendingOrder(userId, cart)
                            .flatMap(order -> payAndFinalize(order, cart.getTotalCost()));
                });
    }

    /** сохраняем заказ в статусе PENDING_PAYMENT и снимок позиций. */
    private Mono<Orders> createPendingOrder(Long userId, ProductsInCart cart) {
        List<OrderItems> items = cart.getItems().stream()
                .map(item -> OrderItems.builder()
                        .title(item.getTitle())
                        .price(item.getPrice())
                        .count(item.getCount())
                        .build())
                .toList();

        Orders order = Orders.builder()
                .userId(userId)
                .totalSum(cart.getTotalCost())
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID())
                .build();

        return orderRepository.save(order)
                .flatMap(saved -> {
                    List<OrderItems> withOrderId = items.stream()
                            .map(item -> OrderItems.builder()
                                    .orderId(saved.getId())
                                    .title(item.getTitle())
                                    .price(item.getPrice())
                                    .count(item.getCount())
                                    .build())
                            .toList();
                    return orderItemRepository.saveAll(withOrderId)
                            .then()
                            .thenReturn(saved);
                })
                .as(transactionalOperator::transactional);
    }

    /** платёж, затем перевод заказа в финальный статус. */
    private Mono<Long> payAndFinalize(Orders order, BigDecimal total) {
        return purchaseService.pay(order.getUserId(), order.getId(), order.getIdempotencyKey(), total)
                .onErrorResume(e -> orderRepository.markPaymentFailed(order.getId())
                        .then(Mono.error(e)))
                .flatMap(response -> finalizePaid(order, response.getPaymentId()));
    }

    private Mono<Long> finalizePaid(Orders order, Long paymentId) {
        return orderRepository.markPaid(order.getId(), LocalDateTime.now(), paymentId)
                .then(cartItemRepository.deleteAllByUserId(order.getUserId()))
                .thenReturn(order.getId())
                .as(transactionalOperator::transactional);
    }
}