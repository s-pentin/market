package org.market.payment.service;

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

    private static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(5000);
    private static final String DEFAULT_CURRENCY = "RUB";

    private final BalanceRepository balanceRepository;

    public PaymentService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public Mono<Balance> getBalance(Long userId) {
        return balanceRepository.findByUserId(userId)
                .switchIfEmpty(balanceRepository.save(
                        new Balance(null, userId, INITIAL_BALANCE, DEFAULT_CURRENCY)));
    }

    @Transactional
    public Mono<Balance> processPayment(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной"));
        }

        return getBalance(userId)
                .flatMap(balance -> {
                    if (balance.getAmount().compareTo(amount) < 0) {
                        return Mono.error(new InsufficientFundsException(
                                "Недостаточно средств. Баланс: " + balance.getAmount() + ", требуется: " + amount));
                    }

                    Balance updated = new Balance(balance.getId(), userId,
                            balance.getAmount().subtract(amount), balance.getCurrency());
                    return balanceRepository.save(updated);
                });
    }
}
