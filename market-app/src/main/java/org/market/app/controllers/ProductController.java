package org.market.app.controllers;

import org.market.app.models.Action;
import org.market.app.models.SortType;
import org.market.app.security.AppUserDetails;
import org.market.app.services.CartService;
import org.market.app.services.ProductService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

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
            @AuthenticationPrincipal(errorOnInvalidType = false) AppUserDetails principal,
            Model model) {

        return productService.getProducts(search, sort, pageNumber, pageSize, currentUserId(principal))
                .doOnNext(page -> {
                    model.addAttribute("items", page.getItems());
                    model.addAttribute("search", page.getSearch());
                    model.addAttribute("sort", page.getSort());
                    model.addAttribute("paging", page.getPaging());
                })
                .thenReturn("items");
    }

    @GetMapping("/items/{id}")
    public Mono<String> getProductById(@PathVariable Long id,
                                       @AuthenticationPrincipal(errorOnInvalidType = false) AppUserDetails principal,
                                       Model model) {
        return productService.getProductById(id, currentUserId(principal))
                .doOnNext(item -> model.addAttribute("item", item))
                .thenReturn("item");
    }

    @PostMapping("/items")
    public Mono<String> updateProductCountFromItems(
            @AuthenticationPrincipal AppUserDetails principal,
            ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(form -> {
                    Long id = Long.valueOf(Objects.requireNonNull(form.getFirst("id")));
                    Action action = Action.valueOf(form.getFirst("action"));
                    String search = form.getFirst("search");
                    SortType sort = SortType.valueOf(form.getOrDefault("sort", List.of(SortType.NO.toString())).getFirst());
                    int pageNumber = Integer.parseInt(form.getOrDefault("pageNumber", List.of("1")).getFirst());
                    int pageSize = Integer.parseInt(form.getOrDefault("pageSize", List.of("5")).getFirst());

                    return cartService.changeCount(principal.getId(), id, action)
                            .thenReturn(buildRedirectUrl(search, sort, pageNumber, pageSize));
                });
    }

    @PostMapping("/items/{id}")
    public Mono<String> updateProductCountFromItem(@PathVariable Long id,
                                                   @AuthenticationPrincipal AppUserDetails principal,
                                                   ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(form -> {
                    Action action = Action.valueOf(form.getFirst("action"));
                    return cartService.changeCount(principal.getId(), id, action)
                            .thenReturn("redirect:/items/" + id);
                });
    }

    private Long currentUserId(AppUserDetails principal) {
        return principal != null ? principal.getId() : null;
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