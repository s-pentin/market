package org.market.payment.repository;

import org.market.payment.model.Balance;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface BalanceRepository extends ReactiveCrudRepository<Balance, Long> {
    Mono<Balance> findByUserId(Long userId);
}