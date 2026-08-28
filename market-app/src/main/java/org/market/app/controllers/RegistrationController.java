package org.market.app.controllers;

import org.market.app.dto.RegistrationRequest;
import org.market.app.services.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Controller
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public Mono<String> register(@ModelAttribute @Valid RegistrationRequest registrationRequest) {
        return registrationService.register(registrationRequest)
                .thenReturn("redirect:/login?registered");
    }
}