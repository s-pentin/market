package org.market.app.controllers;

import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.exceptions.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmptyCartException.class)
    public String handleEmptyCart(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error",
                "Корзина пуста. Добавьте хотя бы один товар перед оформлением заказа.");
        return "redirect:/cart/items";
    }

    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public ModelAndView handleNotFound() {
        return new ModelAndView("not_found", HttpStatus.NOT_FOUND);
    }
}