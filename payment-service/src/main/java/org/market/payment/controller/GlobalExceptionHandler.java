package org.market.payment.controller;

import org.market.payment.exception.IdempotencyKeyConflictException;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public Mono<ResponseEntity<PaymentResponse>> handleInsufficientFunds(InsufficientFundsException e) {
        PaymentResponse response = new PaymentResponse()
                .success(false)
                .message(e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response));
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public Mono<ResponseEntity<PaymentResponse>> handleInvalidRequest(InvalidPaymentRequestException e) {
        PaymentResponse response = new PaymentResponse()
                .success(false)
                .message(e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public Mono<ResponseEntity<PaymentResponse>> handleIdempotencyConflict(IdempotencyKeyConflictException e) {
        PaymentResponse response = new PaymentResponse()
                .success(false)
                .message(e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(response));
    }
}