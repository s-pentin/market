package org.market.app.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.InvalidPaymentRequestException;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.exceptions.PasswordMismatchException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.exceptions.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmptyCartException.class)
    public Mono<String> handleEmptyCart() {
        return Mono.just("redirect:/cart/items?error=empty");
    }

    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public Mono<Rendering> handleNotFound() {
        return Mono.just(Rendering.view("not_found")
                .status(HttpStatus.NOT_FOUND)
                .build());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public Mono<String> handleInsufficientFunds() {
        return Mono.just("redirect:/cart/items?error=insufficient_funds");
    }

    @ExceptionHandler(PaymentServiceUnavailableException.class)
    public Mono<String> handlePaymentUnavailable() {
        return Mono.just("redirect:/cart/items?error=payment_unavailable");
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public Mono<String> handleInvalidPayment() {
        return Mono.just("redirect:/cart/items?error=invalid_payment");
    }

    @ExceptionHandler({UsernameAlreadyExistsException.class, PasswordMismatchException.class})
    public Mono<String> handleRegistrationError(RuntimeException e) {
        return Mono.just("redirect:/register?error="
                + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
    }
}