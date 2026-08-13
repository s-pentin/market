package org.market.app.repositories;

import org.market.app.models.OrderItems;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;


@Repository
public interface OrderItemRepository extends ReactiveCrudRepository<OrderItems, Long> {
    Flux<OrderItems> findAllByOrderId(Long orderId);
}