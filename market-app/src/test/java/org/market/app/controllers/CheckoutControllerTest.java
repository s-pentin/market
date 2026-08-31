package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.exceptions.InsufficientFundsException;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.security.AppUserDetails;
import org.market.app.security.SecurityConfig;
import org.market.app.usecases.CheckoutUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@WebFluxTest({CheckoutController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
class CheckoutControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CheckoutUseCase checkoutUseCase;

    @MockBean
    private ReactiveUserDetailsService reactiveUserDetailsService;

    private UsernamePasswordAuthenticationToken auth() {
        AppUserDetails principal = new AppUserDetails(new User(1L, "customer1", "hash", Role.CUSTOMER, true));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void buy_redirectsToOrderPageWithNewOrderTrue() {
        when(checkoutUseCase.execute(1L)).thenReturn(Mono.just(42L));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/orders/42?newOrder=true"));
    }

    @Test
    void buy_emptyCart_redirectsToCart() {
        when(checkoutUseCase.execute(1L)).thenReturn(Mono.error(new EmptyCartException()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items"));
    }

    @Test
    void buy_insufficientFunds_redirectsToCartWithError() {
        when(checkoutUseCase.execute(1L)).thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items?error=insufficient_funds"));
    }

    @Test
    void buy_paymentServiceUnavailable_redirectsToCartWithError() {
        when(checkoutUseCase.execute(1L)).thenReturn(Mono.error(new PaymentServiceUnavailableException("Сервис платежей недоступен")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/cart/items?error=payment_unavailable"));
    }
}
