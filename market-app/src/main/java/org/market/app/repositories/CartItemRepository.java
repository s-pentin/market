package org.market.app.repositories;

import org.market.app.models.CartItem;
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
}