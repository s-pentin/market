package org.market.app.usecases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.models.CartItem;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.market.app.repositories.ProductRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private PlaceOrderUseCase placeOrderUseCase;

    @Test
    void execute_createsFromCart_clearsCart_returnsId() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, 1L, 3);
        Orders savedOrder = new Orders(42L, BigDecimal.valueOf(300));
        OrderItems savedItem = OrderItems.builder().id(1L).orderId(42L).title("Ball").price(BigDecimal.valueOf(100)).count(3).build();

        when(cartItemRepository.findAll()).thenReturn(Flux.just(cartItem));
        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(product));
        when(orderRepository.save(any(Orders.class))).thenReturn(Mono.just(savedOrder));
        when(orderItemRepository.saveAll(any(Iterable.class))).thenReturn(Flux.just(savedItem));
        when(cartItemRepository.deleteAll(any(Iterable.class))).thenReturn(Mono.empty());

        StepVerifier.create(placeOrderUseCase.execute())
                .assertNext(id -> assertThat(id).isEqualTo(42L))
                .verifyComplete();

        verify(orderRepository).save(any(Orders.class));
        verify(cartItemRepository).deleteAll(any(Iterable.class));
    }

    @Test
    void execute_calculatesTotalSum() {
        Product p1 = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        Product p2 = new Product(2L, "Book", null, null, BigDecimal.valueOf(50));
        CartItem c1 = new CartItem(1L, 1L, 2);
        CartItem c2 = new CartItem(2L, 2L, 3);
        Orders savedOrder = new Orders(1L, BigDecimal.valueOf(350));
        OrderItems item = OrderItems.builder().id(1L).orderId(1L).title("Ball").price(BigDecimal.valueOf(100)).count(2).build();

        when(cartItemRepository.findAll()).thenReturn(Flux.just(c1, c2));
        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(p1, p2));
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders arg = inv.getArgument(0);
            assertThat(arg.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(350));
            return Mono.just(savedOrder);
        });
        when(orderItemRepository.saveAll(any(Iterable.class))).thenReturn(Flux.just(item));
        when(cartItemRepository.deleteAll(any(Iterable.class))).thenReturn(Mono.empty());

        StepVerifier.create(placeOrderUseCase.execute())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void execute_emptyCart_throwsEmptyCartException() {
        when(cartItemRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(placeOrderUseCase.execute())
                .expectError(EmptyCartException.class)
                .verify();
    }

    @Test
    void execute_snapshotsProductTitlePriceCount() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, 1L, 3);
        Orders savedOrder = new Orders(1L, BigDecimal.valueOf(300));
        OrderItems savedItem = OrderItems.builder().id(1L).orderId(1L).title("Ball").price(BigDecimal.valueOf(100)).count(3).build();

        when(cartItemRepository.findAll()).thenReturn(Flux.just(cartItem));
        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(product));
        when(orderRepository.save(any(Orders.class))).thenReturn(Mono.just(savedOrder));
        when(orderItemRepository.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            Iterable<OrderItems> items = inv.getArgument(0);
            OrderItems item = items.iterator().next();
            assertThat(item.getTitle()).isEqualTo("Ball");
            assertThat(item.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
            assertThat(item.getCount()).isEqualTo(3);
            return Flux.just(savedItem);
        });
        when(cartItemRepository.deleteAll(any(Iterable.class))).thenReturn(Mono.empty());

        StepVerifier.create(placeOrderUseCase.execute())
                .expectNextCount(1)
                .verifyComplete();
    }
}
