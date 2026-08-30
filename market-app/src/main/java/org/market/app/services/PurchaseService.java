package org.market.app.services;

import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.InvalidPaymentRequestException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.payment.api.BalanceApi;
import org.market.app.payment.api.PaymentApi;
import org.market.app.payment.model.BalanceResponse;
import org.market.app.payment.model.PaymentRequest;
import org.market.app.payment.model.PaymentResponse;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
                    if (e.getStatusCode().value() == 409) {
                        return new InvalidPaymentRequestException(
                                "Конфликт idempotency key: параметры платежа не совпадают с исходным запросом");
                    }
                    if (e.getStatusCode().is5xxServerError()) {
                        return new PaymentServiceUnavailableException("Сервис платежей недоступен", e);
                    }
                    return e;
                })
                .transform(PurchaseService::mapTransportErrors);
    }

    /**
     * Тройной исход опроса статуса платежа (см. {@link PaymentStatusResult}): 404 — платежа
     * действительно нет, успешный ответ — обрабатываем фактический статус, любая другая
     * ошибка (таймаут, 5xx, сбой OAuth2, сетевая ошибка) — Indeterminate, вызывающий код
     * (OrderReconciliationService) должен оставить заказ в PENDING_PAYMENT и повторить позже.
     */
    public Mono<PaymentStatusResult> checkPaymentStatus(UUID idempotencyKey) {
        return paymentApi.getPaymentByIdempotencyKey(idempotencyKey)
                .<PaymentStatusResult>map(PaymentStatusResult.Found::new)
                .onErrorResume(WebClientResponseException.NotFound.class,
                        e -> Mono.just(new PaymentStatusResult.NotFound()))
                .onErrorResume(WebClientResponseException.class,
                        e -> Mono.just(new PaymentStatusResult.Indeterminate(e)))
                .onErrorResume(WebClientRequestException.class,
                        e -> Mono.just(new PaymentStatusResult.Indeterminate(e)))
                .onErrorResume(OAuth2AuthorizationException.class,
                        e -> Mono.just(new PaymentStatusResult.Indeterminate(e)))
                .onErrorResume(TimeoutException.class,
                        e -> Mono.just(new PaymentStatusResult.Indeterminate(e)));
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