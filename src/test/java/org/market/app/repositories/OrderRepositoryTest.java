package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestPostgresContainer;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
class OrderRepositoryTest extends TestPostgresContainer {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void save_withoutItems_persistsOrder() {
        Orders order = Orders.builder()
                .totalSum(BigDecimal.valueOf(100))
                .items(new ArrayList<>())
                .build();

        Orders saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void save_withItems_cascadeSavesOrderItems() {
        Orders order = Orders.builder()
                .totalSum(BigDecimal.valueOf(300))
                .items(new ArrayList<>())
                .build();

        OrderItems item = OrderItems.builder()
                .orders(order)
                .title("Ball")
                .price(BigDecimal.valueOf(100))
                .count(3)
                .build();
        order.getItems().add(item);

        Orders saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getTitle()).isEqualTo("Ball");
        assertThat(saved.getItems().getFirst().getId()).isNotNull();
    }

    @Test
    void findAll_returnsAllOrders() {
        Orders o1 = Orders.builder().totalSum(BigDecimal.valueOf(100)).items(new ArrayList<>()).build();
        Orders o2 = Orders.builder().totalSum(BigDecimal.valueOf(200)).items(new ArrayList<>()).build();

        orderRepository.save(o1);
        orderRepository.save(o2);

        assertThat(orderRepository.findAll()).hasSize(2);
    }
}
