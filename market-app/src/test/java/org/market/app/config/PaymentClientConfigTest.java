package org.market.app.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.exceptions.PaymentServiceUnavailableException;
import org.market.app.infra.TestContainers;
import org.market.app.services.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поднимает реальный {@link PaymentClientConfig} (с OAuth2-фильтром и таймаутом),
 * а WireMock подменяет только token endpoint и payment-service. Так изменение
 * production-конфигурации реально ловится этим тестом.
 */
@SpringBootTest
@ImportTestcontainers(TestContainers.class)
class PaymentClientConfigTest {

    static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock.start();
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> "http://localhost:" + wireMock.port() + "/realms/market/protocol/openid-connect/token");
        registry.add("app.payment.service.url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    private PurchaseService purchaseService;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    @Test
    void getBalance_attachesBearerTokenFromTokenEndpoint() {
        wireMock.stubFor(post(urlEqualTo("/realms/market/protocol/openid-connect/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        wireMock.stubFor(get(urlEqualTo("/api/v1/balance/1"))
                .willReturn(okJson("{\"balance\":5000,\"currency\":\"RUB\"}")));

        StepVerifier.create(purchaseService.getBalance(1L))
                .assertNext(balance -> assertThat(balance).isEqualByComparingTo("5000"))
                .verifyComplete();

        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/balance/1"))
                .withHeader("Authorization", matching(".+")));
    }

    @Test
    void pay_paymentServiceReturns5xx_mapsToPaymentServiceUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/realms/market/protocol/openid-connect/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        wireMock.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(aResponse().withStatus(500).withBody("{}")));

        StepVerifier.create(purchaseService.pay(1L, 100L, java.util.UUID.randomUUID(), java.math.BigDecimal.valueOf(100)))
                .expectError(PaymentServiceUnavailableException.class)
                .verify();
    }

    @Test
    void pay_paymentServiceTimesOut_mapsToPaymentServiceUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/realms/market/protocol/openid-connect/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        wireMock.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(okJson("{}").withFixedDelay(5000)));

        StepVerifier.create(purchaseService.pay(1L, 100L, java.util.UUID.randomUUID(), java.math.BigDecimal.valueOf(100)))
                .expectError(PaymentServiceUnavailableException.class)
                .verify();
    }
}
