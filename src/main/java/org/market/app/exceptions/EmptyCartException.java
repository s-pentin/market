package org.market.app.exceptions;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("You can't place an order: the shopping cart is empty");
    }

    public EmptyCartException(String message) {
        super(message);
    }

    public EmptyCartException(String message, Throwable cause) {
        super(message, cause);
    }
}
