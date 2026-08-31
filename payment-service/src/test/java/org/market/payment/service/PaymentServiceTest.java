package org.market.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.payment.config.PaymentProperties;
import org.market.payment.exception.IdempotencyKeyConflictException;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.model.PaymentRecord;
import org.market.payment.model.PaymentRecordStatus;
import org.market.payment.repository.BalanceRepository;
import org.market.payment.repository.PaymentRecordRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long USER_ID = 1L;
    private static final long BALANCE_ID = 10L;
    private static final long ORDER_ID = 100L;
    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private PaymentRecordWriter paymentRecordWriter;

    private PaymentService paymentService;

    private PaymentService newPaymentService() {
        return new PaymentService(balanceRepository, paymentRecordRepository, paymentRecordWriter,
                new PaymentProperties(BigDecimal.valueOf(5000), "RUB"));
    }

    private void stubWriterEchoesInput() {
        when(paymentRecordWriter.saveIndependently(any(PaymentRecord.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private void stubRecordRepositorySaveEchoesInput() {
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void getBalance_forNewUser_returnsVirtualDefaultWithoutSaving() {
        paymentService = newPaymentService();
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getBalance(USER_ID))
                .expectNextMatches(b -> USER_ID == b.getUserId()
                        && b.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0
                        && "RUB".equals(b.getCurrency())
                        && b.getId() == null)
                .verifyComplete();

        verify(balanceRepository, never()).save(any());
    }

    @Test
    void getBalance_forExistingUser_returnsStoredBalance() {
        paymentService = newPaymentService();
        Balance balance = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(5000), "RUB");
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.just(balance));

        StepVerifier.create(paymentService.getBalance(USER_ID))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0
                        && "RUB".equals(b.getCurrency()))
                .verifyComplete();

        verify(balanceRepository, never()).save(any());
    }

    @Test
    void processPayment_decreasesBalanceForGivenUser() {
        paymentService = newPaymentService();
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(balanceRepository.ensureExists(eq(USER_ID), any(), any())).thenReturn(Mono.just(0));
        when(balanceRepository.debit(USER_ID, BigDecimal.valueOf(1000))).thenReturn(Mono.just(1));
        stubRecordRepositorySaveEchoesInput();
        when(balanceRepository.findByUserId(USER_ID))
                .thenReturn(Mono.just(new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(4000), "RUB")));

        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(1000)))
                .expectNextMatches(o -> o.balance().getAmount().compareTo(BigDecimal.valueOf(4000)) == 0)
                .verifyComplete();
    }

    @Test
    void processPayment_recordsSucceededPayment() {
        paymentService = newPaymentService();
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(balanceRepository.ensureExists(eq(USER_ID), any(), any())).thenReturn(Mono.just(0));
        when(balanceRepository.debit(USER_ID, BigDecimal.valueOf(1000))).thenReturn(Mono.just(1));
        stubRecordRepositorySaveEchoesInput();
        when(balanceRepository.findByUserId(USER_ID))
                .thenReturn(Mono.just(new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(4000), "RUB")));

        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(1000)))
                .expectNextCount(1)
                .verifyComplete();

        // SUCCEEDED сохраняется через paymentRecordRepository напрямую — в той же транзакции,
        // что и debit (см. javadoc chargeAndRecord), а не через PaymentRecordWriter (REQUIRES_NEW).
        verify(paymentRecordRepository).save(argThat(r ->
                r.getStatus() == PaymentRecordStatus.SUCCEEDED
                        && r.getIdempotencyKey().equals(IDEMPOTENCY_KEY)
                        && r.getOrderId().equals(ORDER_ID)));
        verify(paymentRecordWriter, never()).saveIndependently(any());
    }

    @Test
    void processPayment_nullAmount_throwsInvalidPaymentRequest() {
        paymentService = newPaymentService();
        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, null))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_zeroAmount_throwsInvalidPaymentRequest() {
        paymentService = newPaymentService();
        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.ZERO))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_negativeAmount_throwsInvalidPaymentRequest() {
        paymentService = newPaymentService();
        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(-100)))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_missingIdempotencyKey_throwsInvalidPaymentRequest() {
        paymentService = newPaymentService();
        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, null, BigDecimal.valueOf(100)))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_insufficientFunds_throwsInsufficientFundsAndRecordsFailure() {
        paymentService = newPaymentService();
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());
        when(balanceRepository.ensureExists(eq(USER_ID), any(), any())).thenReturn(Mono.just(0));
        when(balanceRepository.debit(USER_ID, BigDecimal.valueOf(1000))).thenReturn(Mono.just(0));
        stubWriterEchoesInput();

        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(1000)))
                .expectError(InsufficientFundsException.class)
                .verify();

        verify(paymentRecordWriter).saveIndependently(argThat(r -> r.getStatus() == PaymentRecordStatus.FAILED));
    }

    @Test
    void processPayment_sameIdempotencyKeyTwice_secondCallReplaysWithoutDebitingAgain() {
        paymentService = newPaymentService();
        PaymentRecord existing = new PaymentRecord(1L, ORDER_ID, USER_ID, IDEMPOTENCY_KEY,
                BigDecimal.valueOf(1000), PaymentRecordStatus.SUCCEEDED, LocalDateTime.now());
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.just(existing));
        when(balanceRepository.findByUserId(USER_ID))
                .thenReturn(Mono.just(new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(4000), "RUB")));

        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(1000)))
                .expectNextMatches(o -> o.balance().getAmount().compareTo(BigDecimal.valueOf(4000)) == 0)
                .verifyComplete();

        verify(balanceRepository, never()).debit(any(), any());
        verify(paymentRecordWriter, never()).saveIndependently(any());
        verify(paymentRecordRepository, never()).save(any());
    }

    @Test
    void processPayment_sameIdempotencyKeyAsPreviousFailure_replaysInsufficientFunds() {
        paymentService = newPaymentService();
        PaymentRecord existing = new PaymentRecord(1L, ORDER_ID, USER_ID, IDEMPOTENCY_KEY,
                BigDecimal.valueOf(1000), PaymentRecordStatus.FAILED, LocalDateTime.now());
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.just(existing));

        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(1000)))
                .expectError(InsufficientFundsException.class)
                .verify();

        verify(balanceRepository, never()).debit(any(), any());
    }

    @Test
    void processPayment_sameIdempotencyKeyDifferentAmount_returns409Conflict() {
        paymentService = newPaymentService();
        PaymentRecord existing = new PaymentRecord(1L, ORDER_ID, USER_ID, IDEMPOTENCY_KEY,
                BigDecimal.valueOf(1000), PaymentRecordStatus.SUCCEEDED, LocalDateTime.now());
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.just(existing));

        StepVerifier.create(paymentService.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(2000)))
                .expectError(IdempotencyKeyConflictException.class)
                .verify();

        verify(balanceRepository, never()).debit(any(), any());
    }

    @Test
    void processPayment_sameIdempotencyKeyDifferentUser_returns409Conflict() {
        paymentService = newPaymentService();
        PaymentRecord existing = new PaymentRecord(1L, ORDER_ID, USER_ID, IDEMPOTENCY_KEY,
                BigDecimal.valueOf(1000), PaymentRecordStatus.SUCCEEDED, LocalDateTime.now());
        when(paymentRecordRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.just(existing));

        StepVerifier.create(paymentService.processPayment(999L, ORDER_ID, IDEMPOTENCY_KEY, BigDecimal.valueOf(1000)))
                .expectError(IdempotencyKeyConflictException.class)
                .verify();

        verify(balanceRepository, never()).debit(any(), any());
    }
}
