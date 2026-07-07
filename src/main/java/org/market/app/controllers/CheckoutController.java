package org.market.app.controllers;

import org.market.app.usecases.PlaceOrderUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CheckoutController {

    private final PlaceOrderUseCase placeOrderUseCase;

    public CheckoutController(PlaceOrderUseCase placeOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
    }

    @PostMapping("/buy")
    public String buy(RedirectAttributes redirectAttributes) {
        Long orderId = placeOrderUseCase.execute();
        redirectAttributes.addAttribute("id", orderId);
        redirectAttributes.addAttribute("newOrder", true);
        return "redirect:/orders/{id}";
    }
}
