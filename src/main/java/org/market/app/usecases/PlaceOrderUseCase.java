package org.market.app.usecases;

import jakarta.transaction.Transactional;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.models.CartItem;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class PlaceOrderUseCase {

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;

    public PlaceOrderUseCase(CartItemRepository cartItemRepository, OrderRepository orderRepository) {
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Long execute() {
        List<CartItem> cartItems = cartItemRepository.findAll();

        if (cartItems.isEmpty()) {
            throw new EmptyCartException();
        }

        List<OrderItems> items = cartItems.stream()
                .map(cartItem -> OrderItems.builder()
                        .title(cartItem.getProduct().getTitle())
                        .price(cartItem.getProduct().getPrice())
                        .count(cartItem.getCount())
                        .build())
                .toList();

        BigDecimal totalSum = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Orders order = Orders.builder()
                .totalSum(totalSum)
                .items(new ArrayList<>())
                .build();

        items.forEach(order::addItem);

        Orders saved = orderRepository.save(order);
        cartItemRepository.deleteAll(cartItems);
        return saved.getId();
    }
}
