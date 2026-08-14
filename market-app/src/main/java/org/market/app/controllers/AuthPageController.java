package org.market.app.controllers;

import jakarta.annotation.Nonnull;
import org.market.app.dto.RegistrationRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Controller
public class AuthPageController {

    @GetMapping("/login")
    public Mono<String> loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @Nonnull Model model) {
        model.addAttribute("error", error != null);
        model.addAttribute("logout", logout != null);
        return Mono.just("login");
    }

    @GetMapping("/register")
    public Mono<String> registerPage(@Nonnull Model model) {
        model.addAttribute("registrationRequest", new RegistrationRequest());
        return Mono.just("register");
    }
}