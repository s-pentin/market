package org.market.app.repositories;

import org.market.app.models.CartItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;


@Repository
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {
    Mono<CartItem> findByProductId(Long productId);
    Flux<CartItem> findAllByProductIdIn(Collection<Long> productIds);
}
