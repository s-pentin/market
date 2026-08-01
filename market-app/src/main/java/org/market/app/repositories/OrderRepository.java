package org.market.app.repositories;

import org.market.app.models.Orders;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Orders, Long> {
}
