package org.market.payment.repository;

import org.market.payment.model.Balance;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public interface BalanceRepository extends ReactiveCrudRepository<Balance, Long> {
    Mono<Balance> findByUserId(Long userId);

    @Modifying
    @Query("INSERT INTO balance (user_id, amount, currency) VALUES (:userId, :initialAmount, :currency) " +
            "ON CONFLICT (user_id) DO NOTHING")
    Mono<Integer> ensureExists(Long userId, BigDecimal initialAmount, String currency);

    @Modifying
    @Query("UPDATE balance SET amount = amount - :amount WHERE user_id = :userId AND amount >= :amount")
    Mono<Integer> debit(Long userId, BigDecimal amount);
}