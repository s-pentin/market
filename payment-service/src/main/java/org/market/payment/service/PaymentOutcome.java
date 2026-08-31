package org.market.payment.service;

import org.market.payment.model.Balance;
import org.market.payment.model.PaymentRecord;

public record PaymentOutcome(PaymentRecord paymentRecord, Balance balance) {
}