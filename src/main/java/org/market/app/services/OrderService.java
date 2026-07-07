package org.market.app.services;

import org.market.app.dto.OrderDto;
import org.market.app.dto.OrderItemsDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.Orders;
import org.market.app.repositories.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<OrderDto> getAllOrders() {
        return orderRepository.findAll().stream().map(this::toOrderDto).toList();
    }

    public OrderDto getOrderById(Long id) {
        Orders order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
        return toOrderDto(order);
    }

    private OrderDto toOrderDto(Orders orders) {
        List<OrderItemsDto> oids = orders.getItems().stream()
                .map(item -> OrderItemsDto.builder()
                        .id(item.getId())
                        .title(item.getTitle())
                        .count(item.getCount())
                        .price(item.getPrice())
                        .build())
                .toList();
        return OrderDto.builder()
                .id(orders.getId())
                .totalSum(orders.getTotalSum())
                .items(oids)
                .build();
    }
}
