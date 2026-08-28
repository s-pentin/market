package org.market.app.repositories;

import org.market.app.models.CartItem;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Repository
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {
    Flux<CartItem> findAllByUserId(Long userId);
    Mono<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    Flux<CartItem> findAllByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);
    Mono<Void> deleteAllByUserId(Long userId);

    @Modifying
    @Query("INSERT INTO cart_item (user_id, product_id, count) VALUES (:userId, :productId, 1) " +
            "ON CONFLICT (user_id, product_id) DO UPDATE SET count = cart_item.count + 1")
    Mono<Integer> incrementOrInsert(Long userId, Long productId);

    @Modifying
    @Query("UPDATE cart_item SET count = count - 1 WHERE user_id = :userId AND product_id = :productId AND count > 1")
    Mono<Integer> decrementIfAboveOne(Long userId, Long productId);

    Mono<Void> deleteByUserIdAndProductId(Long userId, Long productId);
}
