package org.market.payment.repository;

import org.market.payment.model.Balance;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceRepository extends ReactiveCrudRepository<Balance, Long> {
}
