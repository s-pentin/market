package org.market.app.services;

import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.InvalidPaymentRequestException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.payment.api.BalanceApi;
import org.market.app.payment.api.PaymentApi;
import org.market.app.payment.model.BalanceResponse;
import org.market.app.payment.model.PaymentRecordResponse;
import org.market.app.payment.model.PaymentRequest;
import org.market.app.payment.model.PaymentResponse;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

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
                .transform(PurchaseService::mapTransportErrors);
    }

    public Mono<PaymentResponse> pay(Long userId, Long orderId, UUID idempotencyKey, BigDecimal amount) {
        PaymentRequest request = new PaymentRequest()
                .userId(userId)
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .amount(amount);

        return paymentApi.processPayment(request)
                .onErrorMap(WebClientResponseException.class, e -> {
                    if (e.getStatusCode().value() == 402) {
                        return new InsufficientFundsException("Недостаточно средств");
                    }
                    if (e.getStatusCode().value() == 400) {
                        return new InvalidPaymentRequestException("Некорректная сумма платежа");
                    }
                    if (e.getStatusCode().is5xxServerError()) {
                        return new PaymentServiceUnavailableException("Сервис платежей недоступен", e);
                    }
                    return e;
                })
                .transform(PurchaseService::mapTransportErrors);
    }

    public Mono<Optional<PaymentRecordResponse>> checkPaymentStatus(UUID idempotencyKey) {
        return paymentApi.getPaymentByIdempotencyKey(idempotencyKey)
                .map(Optional::of)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(Optional.empty()))
                .transform(PurchaseService::mapTransportErrors);
    }

    private static <T> Mono<T> mapTransportErrors(Mono<T> mono) {
        return mono
                .onErrorMap(WebClientRequestException.class,
                        e -> new PaymentServiceUnavailableException("Сервис платежей недоступен", e))
                .onErrorMap(OAuth2AuthorizationException.class,
                        e -> new PaymentServiceUnavailableException("Не удалось получить токен авторизации для сервиса платежей", e))
                .onErrorMap(TimeoutException.class,
                        e -> new PaymentServiceUnavailableException("Сервис платежей не ответил вовремя", e));
    }
}