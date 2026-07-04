package org.market.app.services;

import jakarta.transaction.Transactional;
import org.market.app.dto.OrderDto;
import org.market.app.dto.OrderItemsDto;
import org.market.app.models.CartItem;
import org.market.app.models.Orders;
import org.market.app.models.OrderItems;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;

    public OrderService(OrderRepository orderRepository, CartItemRepository cartItemRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public List<OrderDto> getAllOrders() {
        List<Orders> orders = orderRepository.findAll();
        return orders.stream().map(this::toOrderDto).toList();
    }

    public OrderDto getOrderById(Long id) {
        Orders order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found: " + id));
        return toOrderDto(order);
    }

    @Transactional
    public Long createOrder() {
        List<CartItem> cartItems = cartItemRepository.findAll();

        Orders orders = new Orders();
        List<OrderItems> items = cartItems.stream()
                .map(cartItem ->
                        OrderItems.builder()
                                .orders(orders)
                                .title(cartItem.getProduct().getTitle())
                                .price(cartItem.getProduct().getPrice())
                                .count(cartItem.getCount())
                                .build()
                ).toList();

        long totalSum = items.stream().mapToLong(i -> i.getPrice() * i.getCount()).sum();
        orders.setTotalSum(totalSum);
        orders.setItems(items);

        Orders saved = orderRepository.save(orders);
        cartItemRepository.deleteAll(cartItems);
        return saved.getId();
    }

    private OrderDto toOrderDto(Orders orders) {
        List<OrderItemsDto> oids = orders.getItems().stream()
                .map(item ->
                        OrderItemsDto.builder()
                                .id(item.getId())
                                .title(item.getTitle())
                                .count(item.getCount())
                                .price(item.getPrice())
                                .build()
                ).toList();
        return OrderDto.builder()
                .id(orders.getId())
                .totalSum(orders.getTotalSum())
                .items(oids)
                .build();
    }
}
