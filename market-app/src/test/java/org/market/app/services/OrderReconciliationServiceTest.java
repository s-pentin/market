package org.market.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.payment.model.PaymentRecordResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReconciliationServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private PurchaseService purchaseService;

    private OrderReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new OrderReconciliationService(orderRepository, cartItemRepository, purchaseService,
                Duration.ofSeconds(60), Duration.ofMinutes(10));
    }

    private Orders pendingOrder(LocalDateTime createdAt) {
        return Orders.builder().id(1L).userId(USER_ID).status(OrderStatus.PENDING_PAYMENT)
                .createdAt(createdAt).idempotencyKey(UUID.randomUUID()).build();
    }

    private PaymentRecordResponse record(PaymentRecordResponse.StatusEnum status) {
        return new PaymentRecordResponse().status(status);
    }

    @Test
    void foundSucceeded_marksPaidAndClearsCart() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(Optional.of(record(PaymentRecordResponse.StatusEnum.SUCCEEDED))));
        when(orderRepository.markPaid(eq(1L), any(), isNull())).thenReturn(Mono.just(1));
        when(cartItemRepository.deleteAllByUserId(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository).markPaid(eq(1L), any(LocalDateTime.class), isNull());
        verify(cartItemRepository).deleteAllByUserId(USER_ID);
    }

    @Test
    void foundFailed_marksPaymentFailed() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey()))
                .thenReturn(Mono.just(Optional.of(record(PaymentRecordResponse.StatusEnum.FAILED))));
        when(orderRepository.markPaymentFailed(1L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository).markPaymentFailed(1L);
        verify(orderRepository, never()).markPaid(any(), any(), any());
    }

    @Test
    void notFound_pastHardTimeout_marksPaymentFailed() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(15));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey())).thenReturn(Mono.just(Optional.empty()));
        when(orderRepository.markPaymentFailed(1L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository).markPaymentFailed(1L);
    }

    @Test
    void notFound_withinGrace_leavesOrderAlone() {
        Orders order = pendingOrder(LocalDateTime.now().minusMinutes(5));
        when(purchaseService.checkPaymentStatus(order.getIdempotencyKey())).thenReturn(Mono.just(Optional.empty()));

        StepVerifier.create(service.reconcileOne(order)).verifyComplete();

        verify(orderRepository, never()).markPaymentFailed(1L);
        verify(orderRepository, never()).markPaid(any(), any(), any());
    }
}
