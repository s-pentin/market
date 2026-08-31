package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.security.SecurityConfig;
import org.market.app.services.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Optional;

import static org.mockito.Mockito.when;

@WebFluxTest(ImageController.class)
@Import(SecurityConfig.class)
class ImageControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ImageService imageService;

    @MockBean
    private ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void getImage_notFound_returns404() {
        when(imageService.findImage("nonexistent.jpg")).thenReturn(Optional.empty());

        webTestClient.get().uri("/images/nonexistent.jpg")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getImage_nullFilename_returns404() {
        when(imageService.findImage("null")).thenReturn(Optional.empty());

        webTestClient.get().uri("/images/null")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getImage_filenameWithAt_returns404() {
        when(imageService.findImage("file@name.png")).thenReturn(Optional.empty());

        webTestClient.get().uri("/images/file@name.png")
                .exchange()
                .expectStatus().isNotFound();
    }
}
