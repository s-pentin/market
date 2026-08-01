package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ImportTestcontainers(TestContainers.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll().block();
        orderRepository.deleteAll().block();
    }

    @Test
    void save_persistsOrder() {
        Orders order = Orders.builder().totalSum(BigDecimal.valueOf(100)).build();

        StepVerifier.create(orderRepository.save(order))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(100));
                })
                .verifyComplete();
    }

    @Test
    void save_andSaveItems_persists() {
        StepVerifier.create(
                orderRepository.save(Orders.builder().totalSum(BigDecimal.valueOf(300)).build())
                        .flatMap(savedOrder -> {
                            OrderItems item = OrderItems.builder()
                                    .orderId(savedOrder.getId())
                                    .title("Ball")
                                    .price(BigDecimal.valueOf(100))
                                    .count(3)
                                    .build();
                            return orderItemRepository.save(item).thenReturn(savedOrder);
                        })
        ).assertNext(savedOrder -> assertThat(savedOrder.getId()).isNotNull())
         .verifyComplete();

        StepVerifier.create(orderItemRepository.findAll())
                .assertNext(item -> {
                    assertThat(item.getTitle()).isEqualTo("Ball");
                    assertThat(item.getCount()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void findAll_returnsAllOrders() {
        orderRepository.save(Orders.builder().totalSum(BigDecimal.valueOf(100)).build()).block();
        orderRepository.save(Orders.builder().totalSum(BigDecimal.valueOf(200)).build()).block();

        StepVerifier.create(orderRepository.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }
}