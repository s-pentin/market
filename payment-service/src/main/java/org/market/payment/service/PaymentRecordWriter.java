package org.market.payment.service;

import org.market.payment.model.PaymentRecord;
import org.market.payment.repository.PaymentRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Сохраняет запись о платеже в собственной, независимой транзакции (REQUIRES_NEW).
 * Запись о FAILED-платеже коммитится, даже если внешний
 * {@link PaymentService#processPayment} метод после сохранения вернёт ошибку и
 * его транзакция откатится — иначе ledger теряет исход отклонённого платежа.
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
