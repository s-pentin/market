package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.OrderDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.security.AppUserDetails;
import org.market.app.security.SecurityConfig;
import org.market.app.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest({OrderController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderService orderService;

    @MockBean
    private ReactiveUserDetailsService reactiveUserDetailsService;

    private UsernamePasswordAuthenticationToken auth() {
        AppUserDetails principal = new AppUserDetails(new User(1L, "customer1", "hash", Role.CUSTOMER, true));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void getOrders_returns200() {
        when(orderService.getAllOrders(1L)).thenReturn(Flux.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .get().uri("/orders")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrderById_returns200() {
        OrderDto order = OrderDto.builder().id(1L).totalSum(BigDecimal.valueOf(100)).items(List.of()).build();
        when(orderService.getOrderById(eq(1L), any())).thenReturn(Mono.just(order));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .get().uri("/orders/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrderById_notOwner_returns404() {
        when(orderService.getOrderById(eq(99L), any()))
                .thenReturn(Mono.error(new OrderNotFoundException("Order not found: 99")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .get().uri("/orders/99")
                .exchange()
                .expectStatus().isNotFound();
    }
}