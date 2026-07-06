package org.market.app.services;

import jakarta.transaction.Transactional;
import org.market.app.dto.OrderDto;
import org.market.app.dto.OrderItemsDto;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.CartItem;
import org.market.app.models.Orders;
import org.market.app.models.OrderItems;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        Orders order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
        return toOrderDto(order);
    }

    @Transactional
    public Long createOrder() {
        List<CartItem> cartItems = cartItemRepository.findAll();

        if (cartItems.isEmpty()) {
            throw new EmptyCartException();
        }

        List<OrderItems> items = cartItems.stream()
                .map(cartItem ->
                        OrderItems.builder()
                                .title(cartItem.getProduct().getTitle())
                                .price(cartItem.getProduct().getPrice())
                                .count(cartItem.getCount())
                                .build()
                ).toList();

        BigDecimal totalSum = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Orders orders = Orders.builder()
                .totalSum(totalSum)
                .items(new ArrayList<>())
                .build();

        items.forEach(orders::addItem);

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
