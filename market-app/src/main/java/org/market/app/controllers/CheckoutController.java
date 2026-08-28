package org.market.app.controllers;

import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.InvalidPaymentRequestException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.security.AppUserDetails;
import org.market.app.usecases.CheckoutUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

@Controller
public class CheckoutController {

    private final CheckoutUseCase checkoutUseCase;

    public CheckoutController(CheckoutUseCase checkoutUseCase) {
        this.checkoutUseCase = checkoutUseCase;
    }

    @PostMapping("/buy")
    public Mono<String> buy(@AuthenticationPrincipal AppUserDetails principal) {
        return checkoutUseCase.execute(principal.getId())
                .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true")
                .onErrorResume(InsufficientFundsException.class, e ->
                        Mono.just("redirect:/cart/items?error=insufficient_funds"))
                .onErrorResume(PaymentServiceUnavailableException.class, e ->
                        Mono.just("redirect:/cart/items?error=payment_unavailable"))
                .onErrorResume(InvalidPaymentRequestException.class, e ->
                        Mono.just("redirect:/cart/items?error=invalid_payment"));
    }
}