package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryTest extends TestPostgresContainer {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void save_withoutItems_persistsOrder() {
        Orders order = new Orders();
        order.setTotalSum(100L);
        order.setItems(new ArrayList<>());

        Orders saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTotalSum()).isEqualTo(100L);
    }

    @Test
    void save_withItems_cascadeSavesOrderItems() {
        Orders order = new Orders();
        order.setTotalSum(300L);

        OrderItems item = OrderItems.builder()
                .orders(order)
                .title("Ball")
                .price(100L)
                .count(3)
                .build();
        order.setItems(new ArrayList<>(List.of(item)));

        Orders saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getTitle()).isEqualTo("Ball");
        assertThat(saved.getItems().get(0).getId()).isNotNull();
    }

    @Test
    void findAll_returnsAllOrders() {
        Orders o1 = new Orders();
        o1.setTotalSum(100L);
        o1.setItems(new ArrayList<>());

        Orders o2 = new Orders();
        o2.setTotalSum(200L);
        o2.setItems(new ArrayList<>());

        orderRepository.save(o1);
        orderRepository.save(o2);

        assertThat(orderRepository.findAll()).hasSize(2);
    }
}
