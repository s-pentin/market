package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.ProductsInCart;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.services.CartService;
import org.market.app.services.PurchaseService;
import org.market.app.usecases.PlaceOrderUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest({CheckoutController.class, GlobalExceptionHandler.class})
class CheckoutControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockBean
    private CartService cartService;

    @MockBean
    private PurchaseService purchaseService;

    private ProductsInCart cartWithTotal(BigDecimal total) {
        return ProductsInCart.builder()
                .items(java.util.List.of())
                .totalCost(total)
                .build();
    }

    @Test
    void buy_redirectsToOrderPageWithNewOrderTrue() {
        when(cartService.getAllProductsInCart()).thenReturn(Mono.just(cartWithTotal(BigDecimal.valueOf(100))));
        when(purchaseService.getBalance()).thenReturn(Mono.just(BigDecimal.valueOf(500)));
        when(purchaseService.pay(eq(null), any(BigDecimal.class)))
                .thenReturn(Mono.just(new PaymentResponse().success(true)));
        when(placeOrderUseCase.execute()).thenReturn(Mono.just(42L));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/orders/42?newOrder=true"));
    }

    @Test
    void buy_emptyCart_redirectsToCart() {
        when(cartService.getAllProductsInCart()).thenReturn(Mono.just(cartWithTotal(BigDecimal.ZERO)));
        when(purchaseService.getBalance()).thenReturn(Mono.just(BigDecimal.valueOf(500)));
        when(purchaseService.pay(eq(null), any(BigDecimal.class)))
                .thenReturn(Mono.just(new PaymentResponse().success(true)));
        when(placeOrderUseCase.execute()).thenReturn(Mono.error(new EmptyCartException()));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));
    }

    @Test
    void buy_insufficientFunds_redirectsToCartWithError() {
        when(cartService.getAllProductsInCart()).thenReturn(Mono.just(cartWithTotal(BigDecimal.valueOf(100))));
        when(purchaseService.getBalance()).thenReturn(Mono.just(BigDecimal.valueOf(50)));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items?error=insufficient_funds"));
    }

    @Test
    void buy_paymentServiceUnavailable_redirectsToCartWithError() {
        when(cartService.getAllProductsInCart()).thenReturn(Mono.just(cartWithTotal(BigDecimal.valueOf(100))));
        when(purchaseService.getBalance())
                .thenReturn(Mono.error(new PaymentServiceUnavailableException("Сервис платежей недоступен")));

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items?error=payment_unavailable"));
    }
}