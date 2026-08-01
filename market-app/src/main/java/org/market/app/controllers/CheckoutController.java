package org.market.app.controllers;

import org.market.app.usecases.PlaceOrderUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

@Controller
public class CheckoutController {

    private final PlaceOrderUseCase placeOrderUseCase;

    public CheckoutController(PlaceOrderUseCase placeOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
    }

    @PostMapping("/buy")
    public Mono<String> buy() {
        return placeOrderUseCase.execute()
                .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true");
    }
}
