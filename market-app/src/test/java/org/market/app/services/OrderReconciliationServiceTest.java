package org.market.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.models.OrderItems;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.payment.model.PaymentRecordResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReconciliationServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private PurchaseService purchaseService;

    @Mock
    private TransactionalOperator transactionalOperator;

    private OrderReconciliationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new OrderReconciliationService(orderRepository, orderItemRepository, cartItemRepository, purchaseService,
                transactionalOperator, Duration.ofSeconds(60), Duration.ofMinutes(10));
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Orders pendingOrder(LocalDateTime createdAt) {
        return Orders.builder().id(1L).userId(USER_ID).status(OrderStatus.PENDING_PAYMENT)
                .createdAt(createdAt).idempotencyKey(UUID.randomUUID()).build();
    }

    private PaymentRecordResponse record(PaymentRecordResponse.StatusEnum status, Long paymentId) {
        return new PaymentRecordResponse().status(status).paymentId(paymentId);
    }

    @Test
    void foundSucceeded_marksPaidWithPaymentIdFromRecordAndClearsOnlyOrderedItems() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(new PaymentStatusResult.Found(record(PaymentRecordResponse.StatusEnum.SUCCEEDED, 77L))));
        when(orderRepository.markPaid(eq(1L), any(), eq(77L))).thenReturn(Mono.just(1));
        when(orderItemRepository.findAllByOrderId(1L)).thenReturn(Flux.just(
                OrderItems.builder().id(10L).orderId(1L).productId(5L).title("Ball").count(2).build()));
        when(cartItemRepository.deleteAllByUserIdAndProductIdIn(USER_ID, List.of(5L))).thenReturn(Mono.empty());

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository).markPaid(eq(1L), any(LocalDateTime.class), eq(77L));
        verify(cartItemRepository).deleteAllByUserIdAndProductIdIn(USER_ID, List.of(5L));
    }

    @Test
    void foundSucceeded_markPaidZeroRowsUpdated_doesNotClearCart() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(new PaymentStatusResult.Found(record(PaymentRecordResponse.StatusEnum.SUCCEEDED, 77L))));
        when(orderRepository.markPaid(eq(1L), any(), eq(77L))).thenReturn(Mono.just(0));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(cartItemRepository, never()).deleteAllByUserIdAndProductIdIn(any(), any());
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    @Test
    void foundFailed_marksPaymentFailed() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(new PaymentStatusResult.Found(record(PaymentRecordResponse.StatusEnum.FAILED, null))));
        when(orderRepository.markPaymentFailed(1L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository).markPaymentFailed(1L);
        verify(orderRepository, never()).markPaid(any(), any(), any());
    }

    @Test
    void notFound_pastHardTimeout_marksPaymentFailed() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(15));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(new PaymentStatusResult.NotFound()));
        when(orderRepository.markPaymentFailed(1L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository).markPaymentFailed(1L);
    }

    @Test
    void notFound_withinGrace_leavesOrderAlone() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(new PaymentStatusResult.NotFound()));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository, never()).markPaymentFailed(1L);
        verify(orderRepository, never()).markPaid(any(), any(), any());
    }

    @Test
    void indeterminate_leavesOrderPending() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(15));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(new PaymentStatusResult.Indeterminate(new RuntimeException("payment-service недоступен"))));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository, never()).markPaymentFailed(any());
        verify(orderRepository, never()).markPaid(any(), any(), any());
    }
}
