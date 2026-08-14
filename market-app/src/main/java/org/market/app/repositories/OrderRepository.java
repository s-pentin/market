package org.market.app.repositories;

import org.market.app.models.Orders;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Orders, Long> {
    Flux<Orders> findAllByUserId(Long userId);
    Mono<Orders> findByIdAndUserId(Long id, Long userId);
}