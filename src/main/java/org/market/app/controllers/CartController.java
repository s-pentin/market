package org.market.app.controllers;

import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.services.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // получение страницы со списком товаров в корзине
    @GetMapping("/items")
    public String getCart(Model model) {
        fillCart(model);
        return "cart";
    }

   // уменьшение/увеличение количества товара в корзине со страницы корзины
    @PostMapping("/items")
    public String updateCart(@RequestParam Long id, @RequestParam Action action) {
        cartService.changeCount(id, action);
        return "redirect:/cart/items";
    }

    private void fillCart(Model model) {
        ProductsInCart cart = cartService.getAllProductsInCart();
        model.addAttribute("items", cart.getItems());
        model.addAttribute("total", cart.getTotalCost());
    }
}
