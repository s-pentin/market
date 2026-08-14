package org.market.app.controllers;

import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.InvalidPaymentRequestException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.security.AppUserDetails;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.market.app.usecases.PlaceOrderUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

@Controller
public class CheckoutController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CartService cartService;
    private final PurchaseService purchaseService;

    public CheckoutController(PlaceOrderUseCase placeOrderUseCase,
                               CartService cartService,
                               PurchaseService purchaseService) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.cartService = cartService;
        this.purchaseService = purchaseService;
    }

    @PostMapping("/buy")
    public Mono<String> buy(@AuthenticationPrincipal AppUserDetails principal) {
        return cartService.getAllProductsInCart(principal.getId())
                .flatMap(cart -> purchaseService.getBalance(principal.getId())
                        .flatMap(balance -> {
                            if (balance.compareTo(cart.getTotalCost()) < 0) {
                                return Mono.<String>error(new InsufficientFundsException(
                                        "Недостаточно средств. Баланс: " + balance + ", требуется: " + cart.getTotalCost()));
                            }
                            return purchaseService.pay(principal.getId(), null, cart.getTotalCost())
                                    .then(placeOrderUseCase.execute(principal.getId()));
                        })
                )
                .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true")
                .onErrorResume(InsufficientFundsException.class, e ->
                        Mono.just("redirect:/cart/items?error=insufficient_funds"))
                .onErrorResume(PaymentServiceUnavailableException.class, e ->
                        Mono.just("redirect:/cart/items?error=payment_unavailable"))
                .onErrorResume(InvalidPaymentRequestException.class, e ->
                        Mono.just("redirect:/cart/items?error=invalid_payment"));
    }
}