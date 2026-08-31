package org.market.payment.service;

import org.market.payment.model.PaymentRecord;
import org.market.payment.repository.PaymentRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Сохраняет запись о FAILED-платеже в собственной, независимой транзакции (REQUIRES_NEW).
 * Это гарантирует, что запись коммитится, даже если внешний {@link PaymentService#processPayment}
 * метод после сохранения вернёт ошибку и его транзакция откатится — иначе ledger теряет исход
 * отклонённого платежа.
 * <p>
 * Используется ТОЛЬКО для FAILED. Для SUCCEEDED запись сохраняется в той же транзакции, что
 * и списание баланса ({@link PaymentService#chargeAndRecord}) — REQUIRES_NEW для успеха был бы
 * ошибкой: запись могла бы закоммититься, а последующий откат внешней транзакции отменил бы
 * списание, оставив ledger с "SUCCEEDED" при фактически неизменённом балансе.
 */
@Component
public class PaymentRecordWriter {

    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentRecordWriter(PaymentRecordRepository paymentRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mono<PaymentRecord> saveIndependently(PaymentRecord record) {
        return paymentRecordRepository.save(record)
                .onErrorResume(DataIntegrityViolationException.class,
                        e -> paymentRecordRepository.findByIdempotencyKey(record.getIdempotencyKey()));
    }
}
