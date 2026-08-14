package org.market.app.services;

import org.market.app.dto.ItemDto;
import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.market.app.exceptions.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Mono<ProductsInCart> getAllProductsInCart(Long userId) {
        return cartItemRepository.findAllByUserId(userId)
                .collectList()
                .flatMap(cartItems -> {
                    if (cartItems.isEmpty()) {
                        return Mono.just(ProductsInCart.builder()
                                .items(List.of())
                                .totalCost(BigDecimal.ZERO)
                                .build());
                    }
                    List<Long> productIds = cartItems.stream()
                            .map(CartItem::getProductId).toList();
                    return productRepository.findAllById(productIds)
                            .collectMap(Product::getId)
                            .map(productsMap -> {
                                BigDecimal total = BigDecimal.ZERO;
                                List<ItemDto> items = new ArrayList<>();
                                for (CartItem ci : cartItems) {
                                    Product product = productsMap.get(ci.getProductId());
                                    if (product != null) {
                                        ItemDto item = toItem(ci, product);
                                        items.add(item);
                                        total = total.add(product.getPrice().multiply(BigDecimal.valueOf(ci.getCount())));
                                    }
                                }
                                return ProductsInCart.builder()
                                        .items(items)
                                        .totalCost(total)
                                        .build();
                            });
                });
    }

    @Transactional
    public Mono<Void> changeCount(Long userId, Long productId, Action action) {
        return switch (action) {
            case PLUS -> plus(userId, productId);
            case MINUS -> minus(userId, productId);
            case DELETE -> delete(userId, productId);
        };
    }

    private Mono<Void> plus(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .flatMap(existingCartItem -> {
                    existingCartItem.setCount(existingCartItem.getCount() + 1);
                    return cartItemRepository.save(existingCartItem);
                })
                .switchIfEmpty(
                        productRepository.findById(productId)
                                .switchIfEmpty(Mono.error(new ProductNotFoundException("Product not found: " + productId)))
                                .flatMap(product -> cartItemRepository.save(new CartItem(null, userId, productId, 1)))
                )
                .then();
    }

    private Mono<Void> minus(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .flatMap(cartItem -> {
                    if (cartItem.getCount() > 1) {
                        cartItem.setCount(cartItem.getCount() - 1);
                        return cartItemRepository.save(cartItem);
                    } else {
                        return cartItemRepository.delete(cartItem);
                    }
                })
                .then();
    }

    private Mono<Void> delete(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .flatMap(cartItemRepository::delete);
    }

    private ItemDto toItem(CartItem cartItem, Product product) {
        return ItemDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .imgPath(product.getImgPath())
                .price(product.getPrice())
                .count(cartItem.getCount())
                .build();
    }
}
