package org.market.app.controllers;

import org.market.app.services.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public String getOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String getOrderById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") Boolean newOrder,
            Model model) {
        model.addAttribute("order", orderService.getOrderById(id));
        model.addAttribute("newOrder", newOrder);
        return "order";
    }
}
