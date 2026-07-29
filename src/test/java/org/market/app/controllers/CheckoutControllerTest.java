package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.usecases.PlaceOrderUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@WebFluxTest({CheckoutController.class, GlobalExceptionHandler.class})
class CheckoutControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PlaceOrderUseCase placeOrderUseCase;

    @Test
    void buy_redirectsToOrderPageWithNewOrderTrue() {
        when(placeOrderUseCase.execute()).thenReturn(Mono.just(42L));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/orders/42?newOrder=true"));
    }

    @Test
    void buy_emptyCart_redirectsToCart() {
        when(placeOrderUseCase.execute()).thenReturn(Mono.error(new EmptyCartException()));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));
    }
}