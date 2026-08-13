package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void getAllOrders_returnsOrderDtoList() {
        Orders order = new Orders(1L, BigDecimal.valueOf(500));
        OrderItems item = OrderItems.builder()
                .id(1L).orderId(1L).title("Ball").price(BigDecimal.valueOf(100)).count(5).build();

        when(orderRepository.findAll()).thenReturn(Flux.just(order));
        when(orderItemRepository.findAllByOrderId(1L)).thenReturn(Flux.just(item));

        StepVerifier.create(orderService.getAllOrders())
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(500));
                    assertThat(dto.getItems()).hasSize(1);
                    assertThat(dto.getItems().getFirst().getTitle()).isEqualTo("Ball");
                })
                .verifyComplete();
    }

    @Test
    void getOrderById_returnsOrderDto() {
        Orders order = new Orders(1L, BigDecimal.valueOf(200));
        OrderItems item = OrderItems.builder()
                .id(1L).orderId(1L).title("Book").price(BigDecimal.valueOf(100)).count(2).build();

        when(orderRepository.findById(1L)).thenReturn(Mono.just(order));
        when(orderItemRepository.findAllByOrderId(1L)).thenReturn(Flux.just(item));

        StepVerifier.create(orderService.getOrderById(1L))
                .assertNext(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(200));
                    assertThat(dto.getItems()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void getOrderById_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(orderService.getOrderById(99L))
                .expectErrorMatches(e -> e instanceof OrderNotFoundException
                        && e.getMessage().contains("99"))
                .verify();
    }
}
