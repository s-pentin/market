package org.market.app.controllers;

import org.market.app.models.Action;
import org.market.app.security.AppUserDetails;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Objects;

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
    public Mono<String> getCart(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        return cartService.getAllProductsInCart(principal.getId())
                .flatMap(cart -> {
                    model.addAttribute("items", cart.getItems());
                    model.addAttribute("total", cart.getTotalCost());

                    if (cart.getItems().isEmpty()) {
                        model.addAttribute("canCheckout", false);
                        model.addAttribute("balance", BigDecimal.ZERO);
                        return Mono.just("cart");
                    }

                    return purchaseService.getBalance(principal.getId())
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
    public Mono<String> updateCart(@AuthenticationPrincipal AppUserDetails principal, ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(form -> {
                    Long id = Long.valueOf(Objects.requireNonNull(form.getFirst("id")));
                    Action action = Action.valueOf(form.getFirst("action"));
                    return cartService.changeCount(principal.getId(), id, action)
                            .thenReturn("redirect:/cart/items");
                });
    }
}