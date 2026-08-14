package org.market.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static final long USER_ID = 1L;
    private static final long BALANCE_ID = 10L;

    @Test
    void getBalance_forNewUser_createsInitialBalance() {
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.empty());
        when(balanceRepository.save(any(Balance.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(paymentService.getBalance(USER_ID))
                .expectNextMatches(b -> USER_ID == b.getUserId()
                        && b.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0
                        && "RUB".equals(b.getCurrency()))
                .verifyComplete();
    }

    @Test
    void getBalance_forExistingUser_returnsBalanceWithoutCreating() {
        Balance balance = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(5000), "RUB");
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.just(balance));
        // switchIfEmpty оценивает fallback-аргумент лениво на этапе сборки цепочки,
        // поэтому save(...) обязан вернуть ненулевой Mono, даже если не будет подписан.
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getBalance(USER_ID))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0
                        && "RUB".equals(b.getCurrency()))
                .verifyComplete();
    }

    @Test
    void processPayment_decreasesBalanceForGivenUser() {
        Balance initial = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(5000), "RUB");
        Balance expected = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(4000), "RUB");
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.just(initial));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.just(expected));

        StepVerifier.create(paymentService.processPayment(USER_ID, BigDecimal.valueOf(1000)))
                .expectNextMatches(b -> b.getAmount().compareTo(BigDecimal.valueOf(4000)) == 0)
                .verifyComplete();
    }

    @Test
    void processPayment_onlyTouchesBalanceOfGivenUser() {
        Balance initial = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(5000), "RUB");
        Balance expected = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(4000), "RUB");
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.just(initial));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.just(expected));

        StepVerifier.create(paymentService.processPayment(USER_ID, BigDecimal.valueOf(1000)))
                .expectNextCount(1)
                .verifyComplete();

        verify(balanceRepository).findByUserId(USER_ID);
        verify(balanceRepository, never()).findByUserId(2L);
    }

    @Test
    void processPayment_nullAmount_throwsInvalidPaymentRequest() {
        StepVerifier.create(paymentService.processPayment(USER_ID, null))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_zeroAmount_throwsInvalidPaymentRequest() {
        StepVerifier.create(paymentService.processPayment(USER_ID, BigDecimal.ZERO))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_negativeAmount_throwsInvalidPaymentRequest() {
        StepVerifier.create(paymentService.processPayment(USER_ID, BigDecimal.valueOf(-100)))
                .expectError(InvalidPaymentRequestException.class)
                .verify();
    }

    @Test
    void processPayment_insufficientFunds_throwsInsufficientFunds() {
        Balance balance = new Balance(BALANCE_ID, USER_ID, BigDecimal.valueOf(500), "RUB");
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(Mono.just(balance));
        when(balanceRepository.save(any(Balance.class))).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.processPayment(USER_ID, BigDecimal.valueOf(1000)))
                .expectError(InsufficientFundsException.class)
                .verify();
    }
}