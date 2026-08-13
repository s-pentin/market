package org.market.payment.service;

import org.market.payment.exception.BalanceNotFoundException;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.repository.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private static final long BALANCE_ID = 1L;

    private final BalanceRepository balanceRepository;

    public PaymentService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public Mono<Balance> getBalance() {
        return balanceRepository.findById(BALANCE_ID);
    }

    @Transactional
    public Mono<Balance> processPayment(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной"));
        }

        return balanceRepository.findById(BALANCE_ID)
                .switchIfEmpty(Mono.error(new BalanceNotFoundException("Баланс не найден")))
                .flatMap(balance -> {
                    if (balance.getAmount().compareTo(amount) < 0) {
                        return Mono.error(new InsufficientFundsException(
                                "Недостаточно средств. Баланс: " + balance.getAmount() + ", требуется: " + amount));
                    }

                    BigDecimal newAmount = balance.getAmount().subtract(amount);
                    Balance updated = new Balance(balance.getId(), newAmount, balance.getCurrency());
                    return balanceRepository.save(updated);
                });
    }
}