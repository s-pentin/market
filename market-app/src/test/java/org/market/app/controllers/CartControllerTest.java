package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.ItemDto;
import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.security.AppUserDetails;
import org.market.app.security.SecurityConfig;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CartService cartService;

    @MockBean
    private PurchaseService purchaseService;

    @MockBean
    private ReactiveUserDetailsService reactiveUserDetailsService;

    private UsernamePasswordAuthenticationToken auth() {
        AppUserDetails principal = new AppUserDetails(new User(1L, "customer1", "hash", Role.CUSTOMER, true));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private Mono<ProductsInCart> emptyCart() {
        return Mono.just(ProductsInCart.builder()
                .items(List.of())
                .totalCost(BigDecimal.ZERO)
                .build());
    }

    @Test
    void getCart_returns200() {
        when(cartService.getAllProductsInCart(1L)).thenReturn(emptyCart());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .get().uri("/cart/items")
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
        when(cartService.getAllProductsInCart(1L)).thenReturn(Mono.just(cart));
        when(purchaseService.getBalance(1L)).thenReturn(Mono.just(BigDecimal.valueOf(1000)));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("500"));
    }

    @Test
    void updateCart_plus_redirectsToCart() {
        when(cartService.changeCount(1L, 1L, Action.PLUS)).thenReturn(Mono.empty());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "1");
        form.add("action", Action.PLUS.name());

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/cart/items")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));

        verify(cartService).changeCount(eq(1L), eq(1L), eq(Action.PLUS));
    }

    @Test
    void updateCart_delete_redirectsToCart() {
        when(cartService.changeCount(1L, 2L, Action.DELETE)).thenReturn(Mono.empty());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "2");
        form.add("action", Action.DELETE.name());

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/cart/items")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));

        verify(cartService).changeCount(eq(1L), eq(2L), eq(Action.DELETE));
    }
}