package org.market.app.controllers;

import org.market.app.security.AppUserDetails;
import org.market.app.services.OrderService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public Mono<String> getOrders(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        return orderService.getAllOrders(principal.getId())
                .collectList()
                .doOnNext(orders -> model.addAttribute("orders", orders))
                .thenReturn("orders");
    }

    @GetMapping("/orders/{id}")
    public Mono<String> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "false") Boolean newOrder,
            Model model) {
        return orderService.getOrderById(id, principal.getId())
                .doOnNext(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("newOrder", newOrder);
                })
                .thenReturn("order");
    }
}