package org.market.payment.controller;

import org.junit.jupiter.api.Test;
import org.market.payment.exception.InsufficientFundsException;
import org.market.payment.exception.InvalidPaymentRequestException;
import org.market.payment.model.Balance;
import org.market.payment.model.PaymentRecord;
import org.market.payment.model.PaymentRecordStatus;
import org.market.payment.security.SecurityConfig;
import org.market.payment.service.PaymentOutcome;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest({PaymentController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/market")
class PaymentControllerTest {

    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();

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
        PaymentRecord record = new PaymentRecord(7L, 5L, 1L, IDEMPOTENCY_KEY,
                BigDecimal.valueOf(1000), PaymentRecordStatus.SUCCEEDED, LocalDateTime.now());
        when(paymentService.processPayment(eq(1L), any(), any(), eq(BigDecimal.valueOf(1000))))
                .thenReturn(Mono.just(new PaymentOutcome(record, updated)));

        withValidToken().post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":1,\"amount\":1000,\"idempotencyKey\":\"" + IDEMPOTENCY_KEY + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.newBalance").isEqualTo(4000)
                .jsonPath("$.paymentId").isEqualTo(7)
                .jsonPath("$.message").isEqualTo("OK");
    }

    @Test
    void processPayment_insufficientFunds_returns402() {
        when(paymentService.processPayment(eq(1L), any(), any(), eq(BigDecimal.valueOf(6000))))
                .thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));

        withValidToken().post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":1,\"amount\":6000,\"idempotencyKey\":\"" + IDEMPOTENCY_KEY + "\"}")
                .exchange()
                .expectStatus().isEqualTo(402);
    }

    @Test
    void processPayment_invalidAmount_returns400() {
        when(paymentService.processPayment(eq(1L), any(), any(), eq(BigDecimal.valueOf(-100))))
                .thenReturn(Mono.error(new InvalidPaymentRequestException("Сумма платежа должна быть положительной")));

        withValidToken().post().uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":1,\"amount\":-100,\"idempotencyKey\":\"" + IDEMPOTENCY_KEY + "\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getPaymentByIdempotencyKey_found_returns200() {
        PaymentRecord record = new PaymentRecord(1L, 5L, 1L, IDEMPOTENCY_KEY,
                BigDecimal.valueOf(1000), PaymentRecordStatus.SUCCEEDED, LocalDateTime.now());
        when(paymentService.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.just(record));
        when(paymentService.getBalance(1L)).thenReturn(Mono.just(new Balance(1L, 1L, BigDecimal.valueOf(4000), "RUB")));

        withValidToken().get().uri("/api/v1/payment/by-idempotency-key/" + IDEMPOTENCY_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCEEDED")
                .jsonPath("$.newBalance").isEqualTo(4000);
    }

    @Test
    void getPaymentByIdempotencyKey_notFound_returns404() {
        when(paymentService.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Mono.empty());

        withValidToken().get().uri("/api/v1/payment/by-idempotency-key/" + IDEMPOTENCY_KEY)
                .exchange()
                .expectStatus().isNotFound();
    }
}