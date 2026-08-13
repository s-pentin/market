package org.market.payment.exception;

public class BalanceNotFoundException  extends RuntimeException {
    public BalanceNotFoundException() {
    }

    public BalanceNotFoundException(String message) {
        super(message);
    }

    public BalanceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
