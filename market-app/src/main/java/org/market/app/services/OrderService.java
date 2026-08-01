package org.market.app.services;

import org.market.app.dto.OrderDto;
import org.market.app.dto.OrderItemsDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.Orders;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public Flux<OrderDto> getAllOrders() {
        return orderRepository.findAll().flatMap(this::toOrderDto);
    }

    @Transactional(readOnly = true)
    public Mono<OrderDto> getOrderById(Long id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + id)))
                .flatMap(this::toOrderDto);
    }

    private Mono<OrderDto> toOrderDto(Orders orders) {
        return orderItemRepository.findAllByOrderId(orders.getId())
                .map(item -> OrderItemsDto.builder()
                        .id(item.getId())
                        .title(item.getTitle())
                        .count(item.getCount())
                        .price(item.getPrice())
                        .build())
                .collectList()
                .map(items -> OrderDto.builder()
                        .id(orders.getId())
                        .totalSum(orders.getTotalSum())
                        .items(items)
                        .build());
    }
}
