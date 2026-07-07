package org.market.app.usecases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.models.CartItem;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PlaceOrderUseCase placeOrderUseCase;

    @Test
    void execute_createsFromCart_clearsCart_returnsId() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, product, 3);
        Orders savedOrder = new Orders(42L, BigDecimal.valueOf(300), List.of());

        when(cartItemRepository.findAll()).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Orders.class))).thenReturn(savedOrder);

        Long result = placeOrderUseCase.execute();

        assertThat(result).isEqualTo(42L);
        verify(orderRepository).save(any(Orders.class));
        verify(cartItemRepository).deleteAll(List.of(cartItem));
    }

    @Test
    void execute_calculatesTotalSum() {
        Product p1 = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        Product p2 = new Product(2L, "Book", null, null, BigDecimal.valueOf(50));
        CartItem c1 = new CartItem(1L, p1, 2); // 200
        CartItem c2 = new CartItem(2L, p2, 3); // 150
        Orders savedOrder = new Orders(1L, BigDecimal.valueOf(350), List.of());

        when(cartItemRepository.findAll()).thenReturn(List.of(c1, c2));
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders arg = inv.getArgument(0);
            assertThat(arg.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(350));
            return savedOrder;
        });

        placeOrderUseCase.execute();
    }

    @Test
    void execute_emptyCart_throwsEmptyCartException() {
        when(cartItemRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> placeOrderUseCase.execute()).isInstanceOf(EmptyCartException.class);
    }

    @Test
    void execute_snapshotsProductTitlePriceCount() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, product, 3);
        Orders savedOrder = new Orders(1L, BigDecimal.valueOf(300), List.of());

        when(cartItemRepository.findAll()).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders arg = inv.getArgument(0);
            OrderItems item = arg.getItems().getFirst();
            assertThat(item.getTitle()).isEqualTo("Ball");
            assertThat(item.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
            assertThat(item.getCount()).isEqualTo(3);
            return savedOrder;
        });

        placeOrderUseCase.execute();
    }
}
