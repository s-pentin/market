package org.market.app.controllers;

import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.exceptions.ProductNotFoundException;
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
}