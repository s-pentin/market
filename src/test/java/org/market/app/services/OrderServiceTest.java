package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.OrderDto;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.OrderNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void getAllOrders_returnsOrderDtoList() {
        Orders order = new Orders(1L, BigDecimal.valueOf(500), List.of(
                new OrderItems(1L, null, "Ball", BigDecimal.valueOf(100), 5)
        ));
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderDto> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1L);
        assertThat(result.getFirst().getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.getFirst().getItems()).hasSize(1);
        assertThat(result.getFirst().getItems().getFirst().getTitle()).isEqualTo("Ball");
    }

    @Test
    void getOrderById_returnsOrderDto() {
        Orders order = new Orders(1L, BigDecimal.valueOf(200), List.of(
                new OrderItems(1L, null, "Book", BigDecimal.valueOf(100), 2)
        ));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderDto result = orderService.getOrderById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void getOrderById_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createOrder_createsFromCart_clearsCart_returnsId() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, product, 3);
        Orders savedOrder = new Orders(42L, BigDecimal.valueOf(300), List.of());

        when(cartItemRepository.findAll()).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Orders.class))).thenReturn(savedOrder);

        Long result = orderService.createOrder();

        assertThat(result).isEqualTo(42L);
        verify(orderRepository).save(any(Orders.class));
        verify(cartItemRepository).deleteAll(List.of(cartItem));
    }

    @Test
    void createOrder_calculatesTotalSum() {
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

        orderService.createOrder();
    }

    @Test
    void createOrder_emptyCart_throwsEmptyCartException() {
        when(cartItemRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder()).isInstanceOf(EmptyCartException.class);
    }

    @Test
    void createOrder_snapshotsProductTitlePriceCount() {
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

        orderService.createOrder();
    }
}
