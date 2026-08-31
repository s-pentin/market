package org.market.payment.repository;

import org.market.payment.model.PaymentRecord;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface PaymentRecordRepository extends ReactiveCrudRepository<PaymentRecord, Long> {
    Mono<PaymentRecord> findByIdempotencyKey(UUID idempotencyKey);
}