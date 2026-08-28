package org.market.payment.service;

import org.market.payment.config.PaymentProperties;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.model.PaymentRecord;
import org.market.payment.model.PaymentRecordStatus;
import org.market.payment.repository.BalanceRepository;
import org.market.payment.repository.PaymentRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final BalanceRepository balanceRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentProperties paymentProperties;

    public PaymentService(BalanceRepository balanceRepository,
                           PaymentRecordRepository paymentRecordRepository,
                           PaymentProperties paymentProperties) {
        this.balanceRepository = balanceRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentProperties = paymentProperties;
    }

    public Mono<Balance> getBalance(Long userId) {
        return balanceRepository.findByUserId(userId)
                .switchIfEmpty(Mono.fromSupplier(() -> new Balance(
                        null, userId, paymentProperties.initialBalance(), paymentProperties.defaultCurrency())));
    }

    @Transactional
    public Mono<PaymentOutcome> processPayment(Long userId, Long orderId, UUID idempotencyKey, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной"));
        }
        if (idempotencyKey == null) {
            return Mono.error(new InvalidPaymentRequestException("idempotencyKey обязателен"));
        }

        return paymentRecordRepository.findByIdempotencyKey(idempotencyKey)
                .flatMap(this::replay)
                .switchIfEmpty(Mono.defer(() -> chargeAndRecord(userId, orderId, idempotencyKey, amount)));
    }

    private Mono<PaymentOutcome> replay(PaymentRecord existing) {
        if (existing.getStatus() == PaymentRecordStatus.FAILED) {
            return Mono.error(new InsufficientFundsException(
                    "Платёж с этим idempotency key уже был отклонён ранее"));
        }
        return balanceRepository.findByUserId(existing.getUserId())
                .map(balance -> new PaymentOutcome(existing, balance));
    }

    private Mono<PaymentOutcome> chargeAndRecord(Long userId, Long orderId, UUID idempotencyKey, BigDecimal amount) {
        return balanceRepository.ensureExists(userId, paymentProperties.initialBalance(), paymentProperties.defaultCurrency())
                .then(balanceRepository.debit(userId, amount))
                .flatMap(rowsUpdated -> {
                    PaymentRecordStatus status = rowsUpdated > 0 ? PaymentRecordStatus.SUCCEEDED : PaymentRecordStatus.FAILED;
                    PaymentRecord record = new PaymentRecord(
                            null, orderId, userId, idempotencyKey, amount, status, LocalDateTime.now());

                    return paymentRecordRepository.save(record)
                            .onErrorResume(DataIntegrityViolationException.class, e ->
                                    paymentRecordRepository.findByIdempotencyKey(idempotencyKey))
                            .flatMap(saved -> saved.getStatus() == PaymentRecordStatus.SUCCEEDED
                                    ? balanceRepository.findByUserId(userId).map(balance -> new PaymentOutcome(saved, balance))
                                    : Mono.error(new InsufficientFundsException(
                                            "Недостаточно средств для списания " + amount)));
                });
    }

    public Mono<PaymentRecord> findByIdempotencyKey(UUID idempotencyKey) {
        return paymentRecordRepository.findByIdempotencyKey(idempotencyKey);
    }
}