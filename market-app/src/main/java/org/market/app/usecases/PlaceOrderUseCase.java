package org.market.app.usecases;

import org.market.app.exceptions.EmptyCartException;
import org.market.app.models.CartItem;
import org.market.app.models.OrderItems;
import org.market.app.models.Orders;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderItemRepository;
import org.market.app.repositories.OrderRepository;
import org.market.app.repositories.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PlaceOrderUseCase {

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public PlaceOrderUseCase(CartItemRepository cartItemRepository,
                             OrderRepository orderRepository,
                             ProductRepository productRepository,
                             OrderItemRepository orderItemRepository) {
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public Mono<Long> execute(Long userId) {
        return cartItemRepository.findAllByUserId(userId)
                .collectList()
                .flatMap(cartItems -> {
                    if (cartItems.isEmpty()) {
                        return Mono.error(new EmptyCartException());
                    }
                    List<Long> productIds = cartItems.stream()
                            .map(CartItem::getProductId).toList();

                    return productRepository.findAllById(productIds)
                            .collectMap(Product::getId)
                            .flatMap(productsMap -> {
                                List<OrderItems> orderItemsList = cartItems.stream()
                                        .map(cartItem -> {
                                            Product p = productsMap.get(cartItem.getProductId());
                                            return OrderItems.builder()
                                                    .title(p.getTitle())
                                                    .price(p.getPrice())
                                                    .count(cartItem.getCount())
                                                    .build();
                                        })
                                        .toList();

                                BigDecimal totalSum = orderItemsList.stream()
                                        .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getCount())))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                Orders order = Orders.builder()
                                        .userId(userId)
                                        .totalSum(totalSum)
                                        .build();

                                return orderRepository.save(order)
                                        .flatMap(savedOrder -> {
                                            List<OrderItems> withOrderId = orderItemsList.stream()
                                                    .map(i -> OrderItems.builder()
                                                            .orderId(savedOrder.getId())
                                                            .title(i.getTitle())
                                                            .price(i.getPrice())
                                                            .count(i.getCount())
                                                            .build())
                                                    .toList();

                                            return orderItemRepository.saveAll(withOrderId)
                                                    .then(cartItemRepository.deleteAll(cartItems))
                                                    .thenReturn(savedOrder.getId());
                                        });
                            });
                });
    }
}
