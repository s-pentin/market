package org.market.app.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.ItemDto;
import org.market.app.dto.ProductsInCart;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.models.OrderItems;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutUseCaseTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CartService cartService;

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

    private CheckoutUseCase checkoutUseCase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        checkoutUseCase = new CheckoutUseCase(cartService, orderRepository, orderItemRepository,
                cartItemRepository, purchaseService, transactionalOperator);
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ProductsInCart cartWithTotal(BigDecimal total) {
        return ProductsInCart.builder()
                .items(List.of(ItemDto.builder().id(1L).title("Ball").price(BigDecimal.valueOf(100)).count(2).build()))
                .totalCost(total)
                .build();
    }

    @Test
    void execute_createsPendingOrder_pays_marksPaid_clearsCart() {
        ProductsInCart cart = cartWithTotal(BigDecimal.valueOf(200));
        Orders saved = Orders.builder().id(42L).userId(USER_ID).totalSum(BigDecimal.valueOf(200))
                .status(OrderStatus.PENDING_PAYMENT).idempotencyKey(UUID.randomUUID()).build();

        when(cartService.getAllProductsInCart(USER_ID)).thenReturn(Mono.just(cart));
        when(orderRepository.save(any(Orders.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(any(Iterable.class))).thenReturn(Flux.empty());
        when(purchaseService.pay(eq(USER_ID), eq(42L), any(UUID.class), eq(BigDecimal.valueOf(200))))
                .thenReturn(Mono.just(new PaymentResponse().success(true).paymentId(7L)));
        when(orderRepository.markPaid(eq(42L), any(), eq(7L))).thenReturn(Mono.just(1));
        when(cartItemRepository.deleteAllByUserId(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(checkoutUseCase.execute(USER_ID))
                .expectNext(42L)
                .verifyComplete();

        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderCaptor.getValue().getIdempotencyKey()).isNotNull();
        verify(orderRepository).markPaid(eq(42L), any(LocalDateTime.class), eq(7L));
        verify(cartItemRepository).deleteAllByUserId(USER_ID);
    }

    @Test
    void execute_emptyCart_throwsEmptyCartException() {
        when(cartService.getAllProductsInCart(USER_ID))
                .thenReturn(Mono.just(ProductsInCart.builder().items(List.of()).totalCost(BigDecimal.ZERO).build()));

        StepVerifier.create(checkoutUseCase.execute(USER_ID))
                .expectError(EmptyCartException.class)
                .verify();

        verify(orderRepository, never()).save(any(Orders.class));
    }

    @Test
    void execute_paymentFailure_marksFailedAndKeepsCart() {
        ProductsInCart cart = cartWithTotal(BigDecimal.valueOf(200));
        Orders saved = Orders.builder().id(42L).userId(USER_ID).totalSum(BigDecimal.valueOf(200))
                .status(OrderStatus.PENDING_PAYMENT).idempotencyKey(UUID.randomUUID()).build();

        when(cartService.getAllProductsInCart(USER_ID)).thenReturn(Mono.just(cart));
        when(orderRepository.save(any(Orders.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(any(Iterable.class))).thenReturn(Flux.empty());
        when(purchaseService.pay(eq(USER_ID), eq(42L), any(UUID.class), eq(BigDecimal.valueOf(200))))
                .thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));
        when(orderRepository.markPaymentFailed(42L)).thenReturn(Mono.just(1));

        StepVerifier.create(checkoutUseCase.execute(USER_ID))
                .expectError(InsufficientFundsException.class)
                .verify();

        verify(orderRepository).markPaymentFailed(42L);
        verify(cartItemRepository, never()).deleteAllByUserId(anyLong());
    }

    @Test
    void execute_paymentSucceededButFinalSaveFails_leavesOrderPendingForReconciliation() {
        ProductsInCart cart = cartWithTotal(BigDecimal.valueOf(200));
        Orders saved = Orders.builder().id(42L).userId(USER_ID).totalSum(BigDecimal.valueOf(200))
                .status(OrderStatus.PENDING_PAYMENT).idempotencyKey(UUID.randomUUID()).build();

        when(cartService.getAllProductsInCart(USER_ID)).thenReturn(Mono.just(cart));
        when(orderRepository.save(any(Orders.class))).thenReturn(Mono.just(saved));
        when(orderItemRepository.saveAll(any(Iterable.class))).thenReturn(Flux.empty());
        when(purchaseService.pay(eq(USER_ID), eq(42L), any(UUID.class), eq(BigDecimal.valueOf(200))))
                .thenReturn(Mono.just(new PaymentResponse().success(true).paymentId(7L)));
        when(orderRepository.markPaid(eq(42L), any(), eq(7L)))
                .thenReturn(Mono.error(new RuntimeException("DB failure")));

        StepVerifier.create(checkoutUseCase.execute(USER_ID))
                .expectError(RuntimeException.class)
                .verify();

        // Заказ остался PENDING_PAYMENT — его подхватит сверка.
        verify(orderRepository, never()).markPaymentFailed(42L);
    }
}
