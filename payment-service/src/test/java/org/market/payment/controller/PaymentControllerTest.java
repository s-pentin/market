package org.market.payment.controller;

import org.junit.jupiter.api.Test;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.security.SecurityConfig;
import org.market.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;

@WebFluxTest({PaymentController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/market")
class PaymentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient withValidToken() {
        return webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt()
                .jwt(jwt -> jwt.claim("aud", List.of("payment-service"))));
    }

    @Test
    void getBalance_withoutToken_returns401() {
        webTestClient.get().uri("/api/v1/balance/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getBalance_withValidJwt_returns200() {
        Balance balance = new Balance(1L, 1L, BigDecimal.valueOf(5000), "RUB");
        when(paymentService.getBalance(1L)).thenReturn(Mono.just(balance));

        withValidToken().get().uri("/api/v1/balance/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(5000)
                .jsonPath("$.currency").isEqualTo("RUB");
    }

    @Test
    void processPayment_validRequest_returns200() {
        Balance updated = new Balance(1L, 1L, BigDecimal.valueOf(4000), "RUB");
        when(paymentService.processPayment(1L, BigDecimal.valueOf(1000)))
                .thenReturn(Mono.just(updated));

        withValidToken().post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":1,\"amount\":1000}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.newBalance").isEqualTo(4000)
                .jsonPath("$.message").isEqualTo("OK");
    }

    @Test
    void processPayment_insufficientFunds_returns402() {
        when(paymentService.processPayment(1L, BigDecimal.valueOf(6000)))
                .thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));

        withValidToken().post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":1,\"amount\":6000}")
                .exchange()
                .expectStatus().isEqualTo(402);
    }

    @Test
    void processPayment_invalidAmount_returns400() {
        when(paymentService.processPayment(1L, BigDecimal.valueOf(-100)))
                .thenReturn(Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной")));

        withValidToken().post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":1,\"amount\":-100}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}