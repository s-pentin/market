package org.market.app.services;

import org.market.app.dto.OrderDto;
import org.market.app.dto.OrderItemsDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public Flux<OrderDto> getAllOrders(Long userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .collectList()
                .flatMapMany(orders -> {
                    if (orders.isEmpty()) {
                        return Flux.empty();
                    }
                    List<Long> orderIds = orders.stream().map(Orders::getId).toList();
                    // Один запрос на все позиции всех заказов — без N+1.
                    return orderItemRepository.findAllByOrderIdIn(orderIds)
                            .collectList()
                            .flatMapMany(items -> {
                                Map<Long, List<OrderItems>> byOrderId = items.stream()
                                        .collect(Collectors.groupingBy(OrderItems::getOrderId));
                                return Flux.fromIterable(orders.stream()
                                        .map(order -> toOrderDto(order, byOrderId.getOrDefault(order.getId(), List.of())))
                                        .toList());
                            });
                });
    }

    @Transactional(readOnly = true)
    public Mono<OrderDto> getOrderById(Long id, Long userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + id)))
                .flatMap(order -> orderItemRepository.findAllByOrderId(order.getId())
                        .collectList()
                        .map(items -> toOrderDto(order, items)));
    }

    private OrderDto toOrderDto(Orders order, List<OrderItems> items) {
        List<OrderItemsDto> itemDtos = items.stream()
                .map(item -> OrderItemsDto.builder()
                        .id(item.getId())
                        .title(item.getTitle())
                        .count(item.getCount())
                        .price(item.getPrice())
                        .build())
                .toList();
        return OrderDto.builder()
                .id(order.getId())
                .totalSum(order.getTotalSum())
                .items(itemDtos)
                .build();
    }
}
