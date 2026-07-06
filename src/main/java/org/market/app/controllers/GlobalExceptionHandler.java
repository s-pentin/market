package org.market.app.controllers;

import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.exceptions.ProductNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmptyCartException.class)
    public String handleEmptyCart(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Empty cart");
        return "redirect:/cart";
    }

    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public String handleNotFound() {
        return "not_found";
    }
}