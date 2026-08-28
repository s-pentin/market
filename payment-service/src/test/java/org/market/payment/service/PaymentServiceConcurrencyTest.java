package org.market.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.payment.infra.TestContainers;
import org.market.payment.model.Balance;
import org.market.payment.repository.BalanceRepository;
import org.market.payment.repository.PaymentRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет атомарность списания баланса и идемпотентность платежа на реальном PostgreSQL.
 */
@SpringBootTest
@ImportTestcontainers(TestContainers.class)
class PaymentServiceConcurrencyTest {

    private static final long USER_ID = 1L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private PaymentRecordRepository paymentRecordRepository;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        paymentRecordRepository.deleteAll().block();
        balanceRepository.deleteAll().block();
    }

    @Test
    void concurrentPayments_againstSameBalance_onlyOneSucceeds() {
        balanceRepository.save(new Balance(null, USER_ID, BigDecimal.valueOf(5000), "RUB")).block();

        AtomicBoolean p1Success = new AtomicBoolean();
        AtomicBoolean p2Success = new AtomicBoolean();

        Mono<PaymentOutcome> p1 = paymentService.processPayment(USER_ID, 100L, UUID.randomUUID(), BigDecimal.valueOf(4000))
                .doOnSuccess(o -> p1Success.set(true))
                .onErrorResume(e -> {
                    p1Success.set(false);
                    return Mono.empty();
                });
        Mono<PaymentOutcome> p2 = paymentService.processPayment(USER_ID, 200L, UUID.randomUUID(), BigDecimal.valueOf(4000))
                .doOnSuccess(o -> p2Success.set(true))
                .onErrorResume(e -> {
                    p2Success.set(false);
                    return Mono.empty();
                });

        Mono.when(p1, p2).block();

        assertThat(p1Success.get() ^ p2Success.get()).as("ровно один платёж должен пройти").isTrue();
        Balance balance = balanceRepository.findByUserId(USER_ID).block();
        assertThat(balance.getAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void sameIdempotencyKey_submittedTwice_debitsOnce() {
        balanceRepository.save(new Balance(null, USER_ID, BigDecimal.valueOf(5000), "RUB")).block();
        UUID key = UUID.randomUUID();

        PaymentOutcome first = paymentService.processPayment(USER_ID, 100L, key, BigDecimal.valueOf(1000)).block();
        PaymentOutcome second = paymentService.processPayment(USER_ID, 100L, key, BigDecimal.valueOf(1000)).block();

        Balance balance = balanceRepository.findByUserId(USER_ID).block();
        assertThat(balance.getAmount()).isEqualByComparingTo("4000");
        assertThat(second.paymentRecord().getId()).isEqualTo(first.paymentRecord().getId());
        assertThat(paymentRecordRepository.count().block()).isEqualTo(1L);
    }
}
