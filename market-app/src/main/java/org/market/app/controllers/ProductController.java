package org.market.app.controllers;

import org.market.app.dto.ProductsPage;
import org.market.app.models.Action;
import org.market.app.models.SortType;
import org.market.app.services.CartService;
import org.market.app.services.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Controller
public class ProductController {

    private final ProductService productService;
    private final CartService cartService;

    public ProductController(ProductService productService, CartService cartService) {
        this.productService = productService;
        this.cartService = cartService;
    }

    @GetMapping({"/", "/items"})
    public Mono<String> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "NO") SortType sort,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "5") Integer pageSize,
            Model model) {

        return productService.getProducts(search, sort, pageNumber, pageSize)
                .doOnNext(page -> {
                    model.addAttribute("items", page.getItems());
                    model.addAttribute("search", page.getSearch());
                    model.addAttribute("sort", page.getSort());
                    model.addAttribute("paging", page.getPaging());
                })
                .thenReturn("items");
    }

    @GetMapping("/items/{id}")
    public Mono<String> getProductById(@PathVariable Long id, Model model) {
        return productService.getProductById(id)
                .doOnNext(item -> model.addAttribute("item", item))
                .thenReturn("item");
    }

    @PostMapping("/items")
    public Mono<String> updateProductCountFromItems(
            @RequestParam Long id,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "NO") SortType sort,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "5") Integer pageSize,
            @RequestParam Action action) {

        return cartService.changeCount(id, action)
                .thenReturn(buildRedirectUrl(search, sort, pageNumber, pageSize));
    }

    @PostMapping("/items/{id}")
    public Mono<String> updateProductCountFromItem(@PathVariable Long id, @RequestParam Action action) {
        return cartService.changeCount(id, action)
                .thenReturn("redirect:/items/" + id);
    }

    private String buildRedirectUrl(String search, SortType sort, int page, int size) {
        StringBuilder url = new StringBuilder("redirect:/items?sort=").append(sort)
                .append("&pageNumber=").append(page)
                .append("&pageSize=").append(size);
        if (search != null && !search.isBlank()) {
            url.append("&search=").append(search);
        }
        return url.toString();
    }
}
