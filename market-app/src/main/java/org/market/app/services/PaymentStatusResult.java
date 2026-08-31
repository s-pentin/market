package org.market.app.services;

import org.market.app.payment.model.PaymentRecordResponse;

/**
 * Тройной опрос статуса платежа: платёж-сервис ответил NotFound,
 * ответил успешно Found, или мы не смогли понять исход (Indeterminate — таймаут, 5xx,
 * ошибка получения OAuth2-токена, сетевой сбой)
 */
public sealed interface PaymentStatusResult {

    record Found(PaymentRecordResponse record) implements PaymentStatusResult {
    }

    record NotFound() implements PaymentStatusResult {
    }

    record Indeterminate(Throwable cause) implements PaymentStatusResult {
    }
}
