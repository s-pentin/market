package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.OrderDto;
import org.market.app.models.CartItem;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        Orders order = new Orders(1L, 500L, List.of(
                new OrderItems(1L, null, "Ball", 100L, 5)
        ));
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderDto> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalSum()).isEqualTo(500L);
        assertThat(result.get(0).getItems()).hasSize(1);
        assertThat(result.get(0).getItems().get(0).getTitle()).isEqualTo("Ball");
    }

    @Test
    void getOrderById_returnsOrderDto() {
        Orders order = new Orders(1L, 200L, List.of(
                new OrderItems(1L, null, "Book", 100L, 2)
        ));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderDto result = orderService.getOrderById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTotalSum()).isEqualTo(200L);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void getOrderById_notFound_throwsRuntimeException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createOrder_createsFromCart_clearsCart_returnsId() {
        Product product = new Product(1L, "Ball", null, null, 100L);
        CartItem cartItem = new CartItem(1L, product, 3);
        Orders savedOrder = new Orders(42L, 300L, List.of());

        when(cartItemRepository.findAll()).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Orders.class))).thenReturn(savedOrder);

        Long result = orderService.createOrder();

        assertThat(result).isEqualTo(42L);
        verify(orderRepository).save(any(Orders.class));
        verify(cartItemRepository).deleteAll(List.of(cartItem));
    }

    @Test
    void createOrder_calculatesTotalSum() {
        Product p1 = new Product(1L, "Ball", null, null, 100L);
        Product p2 = new Product(2L, "Book", null, null, 50L);
        CartItem c1 = new CartItem(1L, p1, 2); // 200
        CartItem c2 = new CartItem(2L, p2, 3); // 150
        Orders savedOrder = new Orders(1L, 350L, List.of());

        when(cartItemRepository.findAll()).thenReturn(List.of(c1, c2));
        when(orderRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders arg = inv.getArgument(0);
            assertThat(arg.getTotalSum()).isEqualTo(350L);
            return savedOrder;
        });

        orderService.createOrder();
    }
}
