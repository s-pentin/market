package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.services.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import org.market.app.dto.ItemDto;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(CartController.class)
class CartControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CartService cartService;

    private Mono<ProductsInCart> emptyCart() {
        return Mono.just(ProductsInCart.builder()
                .items(List.of())
                .totalCost(BigDecimal.ZERO)
                .build());
    }

    @Test
    void getCart_returns200() {
        when(cartService.getAllProductsInCart()).thenReturn(emptyCart());

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getCart_displaysTotalCost() {
        ItemDto item = ItemDto.builder().id(1L).title("Ball").price(BigDecimal.valueOf(500)).count(1).build();
        ProductsInCart cart = ProductsInCart.builder()
                .items(List.of(item))
                .totalCost(BigDecimal.valueOf(500))
                .build();
        when(cartService.getAllProductsInCart()).thenReturn(Mono.just(cart));

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("500"));
    }

    @Test
    void updateCart_plus_redirectsToCart() {
        when(cartService.changeCount(1L, Action.PLUS)).thenReturn(Mono.empty());

        webTestClient.post().uri("/cart/items?id=1&action=PLUS")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));

        verify(cartService).changeCount(1L, Action.PLUS);
    }

    @Test
    void updateCart_delete_redirectsToCart() {
        when(cartService.changeCount(2L, Action.DELETE)).thenReturn(Mono.empty());

        webTestClient.post().uri("/cart/items?id=2&action=DELETE")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));

        verify(cartService).changeCount(2L, Action.DELETE);
    }
}