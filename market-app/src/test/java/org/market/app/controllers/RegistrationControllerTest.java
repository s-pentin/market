package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.RegistrationRequest;
import org.market.app.exceptions.PasswordMismatchException;
import org.market.app.security.SecurityConfig;
import org.market.app.services.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Проверяет, что валидация регистрации работает на сервере, а не только в HTML
 * (обход HTML5-валидации прямым HTTP-запросом не должен создавать пользователя).
 */
@WebFluxTest({RegistrationController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
class RegistrationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void register_blankUsername_rejectedServerSide() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("username=&password=password123&confirmPassword=password123")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/register"));
    }

    @Test
    void register_mismatchedPasswords_rejected() {
        when(registrationService.register(any(RegistrationRequest.class)))
                .thenReturn(Mono.error(new PasswordMismatchException("Пароли не совпадают")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("username=customer99&password=password123&confirmPassword=different")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/register"));
    }
}
