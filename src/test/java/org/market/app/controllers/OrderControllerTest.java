package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.OrderDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;

@WebFluxTest({OrderController.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderService orderService;

    @Test
    void getOrders_returns200() {
        when(orderService.getAllOrders()).thenReturn(Flux.empty());

        webTestClient.get().uri("/orders")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrderById_returns200() {
        OrderDto order = OrderDto.builder().id(1L).totalSum(BigDecimal.valueOf(100)).items(List.of()).build();
        when(orderService.getOrderById(1L)).thenReturn(Mono.just(order));

        webTestClient.get().uri("/orders/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrderById_withNewOrderParam_returns200() {
        OrderDto order = OrderDto.builder().id(1L).totalSum(BigDecimal.valueOf(100)).items(List.of()).build();
        when(orderService.getOrderById(1L)).thenReturn(Mono.just(order));

        webTestClient.get().uri("/orders/1?newOrder=true")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrderById_notFound_returns404() {
        when(orderService.getOrderById(99L)).thenReturn(Mono.error(new OrderNotFoundException()));

        webTestClient.get().uri("/orders/99")
                .exchange()
                .expectStatus().isNotFound();
    }
}