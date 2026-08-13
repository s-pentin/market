package org.market.payment.controller;

import org.junit.jupiter.api.Test;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;

@WebFluxTest({PaymentController.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentService paymentService;

    @Test
    void getBalance_shouldReturn200WithBalance() {
        Balance balance = new Balance(1L, BigDecimal.valueOf(5000), "RUB");
        when(paymentService.getBalance()).thenReturn(Mono.just(balance));

        webTestClient.get().uri("/api/v1/balance")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(5000.0)
                .jsonPath("$.currency").isEqualTo("RUB");
    }

    @Test
    void processPayment_validRequest_shouldReturn200WithSuccess() {
        Balance updated = new Balance(1L, BigDecimal.valueOf(4000), "RUB");
        when(paymentService.processPayment(BigDecimal.valueOf(1000)))
                .thenReturn(Mono.just(updated));

        webTestClient.post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\": 1, \"amount\": 1000}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.newBalance").isEqualTo(4000.0)
                .jsonPath("$.message").isEqualTo("OK");
    }

    @Test
    void processPayment_insufficientFunds_shouldReturn402() {
        when(paymentService.processPayment(BigDecimal.valueOf(6000)))
                .thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));

        webTestClient.post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\": 1, \"amount\": 6000}")
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Недостаточно средств");
    }

    @Test
    void processPayment_invalidAmount_shouldReturn400() {
        when(paymentService.processPayment(BigDecimal.valueOf(-100)))
                .thenReturn(Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной")));

        webTestClient.post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\": 1, \"amount\": -100}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Сумма платежа должна быть положительной");
    }

    @Test
    void processPayment_zeroAmount_shouldReturn400() {
        when(paymentService.processPayment(BigDecimal.ZERO))
                .thenReturn(Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной")));

        webTestClient.post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\": 1, \"amount\": 0}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }
}