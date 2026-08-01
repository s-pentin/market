package org.market.app.controllers;

import org.market.app.models.Action;
import org.market.app.services.CartService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // получение страницы со списком товаров в корзине
    @GetMapping("/items")
    public Mono<String> getCart(Model model) {
        return cartService.getAllProductsInCart()
                .doOnNext(cart -> {
                    model.addAttribute("items", cart.getItems());
                    model.addAttribute("total", cart.getTotalCost());
                })
                .thenReturn("cart");
    }

   // уменьшение/увеличение количества товара в корзине со страницы корзины
    @PostMapping("/items")
    public Mono<String> updateCart(@RequestParam Long id, @RequestParam Action action) {
        return cartService.changeCount(id, action)
                .thenReturn("redirect:/cart/items");
    }
}
