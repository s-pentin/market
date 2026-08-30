package org.market.app.repositories;

import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Orders, Long> {
    Flux<Orders> findAllByUserId(Long userId);
    Flux<Orders> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Mono<Orders> findByIdAndUserId(Long id, Long userId);
    Flux<Orders> findAllByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE orders SET status = 'PAID', paid_at = :paidAt, payment_id = :paymentId " +
            "WHERE id = :id AND status = 'PENDING_PAYMENT'")
    Mono<Integer> markPaid(Long id, LocalDateTime paidAt, Long paymentId);

    @Modifying
    @Query("UPDATE orders SET status = 'PAYMENT_FAILED' WHERE id = :id AND status = 'PENDING_PAYMENT'")
    Mono<Integer> markPaymentFailed(Long id);
}