package org.market.app.services;

import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.InvalidPaymentRequestException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.payment.api.BalanceApi;
import org.market.app.payment.api.PaymentApi;
import org.market.app.payment.model.BalanceResponse;
import org.market.app.payment.model.PaymentRequest;
import org.market.app.payment.model.PaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class PurchaseService {

    private final BalanceApi balanceApi;
    private final PaymentApi paymentApi;

    public PurchaseService(BalanceApi balanceApi, PaymentApi paymentApi) {
        this.balanceApi = balanceApi;
        this.paymentApi = paymentApi;
    }

    public Mono<BigDecimal> getBalance(Long userId) {
        return balanceApi.getBalance(userId)
                .map(BalanceResponse::getBalance)
                .onErrorMap(WebClientRequestException.class,
                        e -> new PaymentServiceUnavailableException("Сервис платежей недоступен", e));
    }

    public Mono<PaymentResponse> pay(Long userId, Long orderId, BigDecimal amount) {
        PaymentRequest request = new PaymentRequest()
                .userId(userId)
                .orderId(orderId)
                .amount(amount);

        return paymentApi.processPayment(request)
                .onErrorMap(WebClientResponseException.class, e -> {
                    if (e.getStatusCode().value() == 402) {
                        return new InsufficientFundsException("Недостаточно средств");
                    }
                    if (e.getStatusCode().value() == 400) {
                        return new InvalidPaymentRequestException("Некорректная сумма платежа");
                    }
                    return e;
                })
                .onErrorMap(WebClientRequestException.class,
                        e -> new PaymentServiceUnavailableException("Сервис платежей недоступен", e));
    }

    public Mono<Boolean> canCheckout(Long userId, BigDecimal cartTotal) {
        return getBalance(userId)
                .map(balance -> balance.compareTo(cartTotal) >= 0)
                .onErrorReturn(false);
    }
}