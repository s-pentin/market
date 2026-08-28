package org.market.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(BigDecimal initialBalance, String defaultCurrency) {

    public PaymentProperties {
        if (initialBalance == null) {
            initialBalance = BigDecimal.valueOf(5000);
        }
        if (defaultCurrency == null) {
            defaultCurrency = "RUB";
        }
    }
}