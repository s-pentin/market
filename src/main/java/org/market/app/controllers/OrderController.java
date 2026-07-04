package org.market.app.controllers;

import org.market.app.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    //Эндпоинт получения страницы со списком заказов
    @GetMapping("/orders")
    public String getOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        return "orders";
    }

    //Эндпоинт получения страницы заказа
    @GetMapping("/orders/{id}")
    public String getOrderById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") Boolean newOrder,
            Model model) {

        model.addAttribute("order", orderService.getOrderById(id));
        model.addAttribute("newOrder", newOrder);

        return "order";
    }

    @PostMapping("/buy")
    public String buy(RedirectAttributes redirectAttributes) {

        Long orderId = orderService.createOrder();
        redirectAttributes.addAttribute("id", orderId);
        redirectAttributes.addAttribute("newOrder", true);

        return "redirect:/orders/{id}";
    }

}
