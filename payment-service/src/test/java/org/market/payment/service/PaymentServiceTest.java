package org.market.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.payment.exception.BalanceNotFoundException;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.repository.BalanceRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static final long BALANCE_ID = 1L;

    @Test
    void getBalance_shouldReturnBalance() {
        Balance balance = new Balance(BALANCE_ID, BigDecimal.valueOf(5000), "RUB");
        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.just(balance));

        StepVerifier.create(paymentService.getBalance())
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0
                        && "RUB".equals(b.getCurrency()))
                .verifyComplete();
    }

    @Test
    void processPayment_shouldDecreaseBalance() {
        Balance initial = new Balance(BALANCE_ID, BigDecimal.valueOf(5000), "RUB");
        Balance expected = new Balance(BALANCE_ID, BigDecimal.valueOf(4000), "RUB");
        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.just(initial));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.just(expected));

        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(1000)))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(4000)) == 0)
                .verifyComplete();
    }

    @Test
    void processPayment_nullAmount_shouldThrowInvalidPaymentRequestException() {
        StepVerifier.create(paymentService.processPayment(null))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_zeroAmount_shouldThrowInvalidPaymentRequestException() {
        StepVerifier.create(paymentService.processPayment(BigDecimal.ZERO))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_negativeAmount_shouldThrowInvalidPaymentRequestException() {
        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(-100)))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_insufficientFunds_shouldThrowException() {
        Balance balance = new Balance(BALANCE_ID, BigDecimal.valueOf(500), "RUB");
        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.just(balance));

        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(1000)))
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    void processPayment_balanceNotFound_shouldThrowException() {
        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(1000)))
                .expectError(BalanceNotFoundException.class)
                .verify();
    }

    @Test
    void processPayment_exactBalance_shouldDeductToZero() {
        Balance initial = new Balance(BALANCE_ID, BigDecimal.valueOf(1000), "RUB");
        Balance expected = new Balance(BALANCE_ID, BigDecimal.ZERO, "RUB");
        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.just(initial));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.just(expected));

        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(1000)))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.ZERO) == 0)
                .verifyComplete();
    }

    @Test
    void processPayment_sequentialPayments_shouldAccumulate() {
        Balance first = new Balance(BALANCE_ID, BigDecimal.valueOf(5000), "RUB");
        Balance afterFirst = new Balance(BALANCE_ID, BigDecimal.valueOf(4000), "RUB");
        Balance afterSecond = new Balance(BALANCE_ID, BigDecimal.valueOf(2500), "RUB");

        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.just(first));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.just(afterFirst));

        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(1000)))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(4000)) == 0)
                .verifyComplete();

        when(balanceRepository.findById(BALANCE_ID)).thenReturn(Mono.just(afterFirst));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.just(afterSecond));

        StepVerifier.create(paymentService.processPayment(BigDecimal.valueOf(1500)))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(2500)) == 0)
                .verifyComplete();
    }
}