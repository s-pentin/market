package org.market.app.controllers;

import org.market.app.models.Action;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final PurchaseService purchaseService;

    public CartController(CartService cartService, PurchaseService purchaseService) {
        this.cartService = cartService;
        this.purchaseService = purchaseService;
    }

    @GetMapping("/items")
    public Mono<String> getCart(Model model) {
        return cartService.getAllProductsInCart()
                .flatMap(cart -> {
                    model.addAttribute("items", cart.getItems());
                    model.addAttribute("total", cart.getTotalCost());

                    if (cart.getItems().isEmpty()) {
                        model.addAttribute("canCheckout", false);
                        model.addAttribute("balance", BigDecimal.ZERO);
                        return Mono.just("cart");
                    }

                    return purchaseService.getBalance()
                            .map(balance -> {
                                model.addAttribute("balance", balance);
                                model.addAttribute("canCheckout",
                                        balance.compareTo(cart.getTotalCost()) >= 0);
                                return "cart";
                            })
                            .onErrorResume(e -> {
                                model.addAttribute("balance", BigDecimal.ZERO);
                                model.addAttribute("canCheckout", false);
                                model.addAttribute("paymentError", "Сервис платежей недоступен");
                                return Mono.just("cart");
                            });
                });
    }

    @PostMapping("/items")
    public Mono<String> updateCart(@RequestParam Long id, @RequestParam Action action) {
        return cartService.changeCount(id, action)
                .thenReturn("redirect:/cart/items");
    }
}