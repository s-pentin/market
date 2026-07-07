package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.OrderDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.repositories.OrderRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

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
}
