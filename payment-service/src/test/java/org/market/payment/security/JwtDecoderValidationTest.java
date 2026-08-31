package org.market.payment.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.market.payment.controller.PaymentController;
import org.market.payment.model.Balance;
import org.market.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PaymentController.class)
@Import(SecurityConfig.class)
class JwtDecoderValidationTest {

    private static final String ISSUER_PREFIX = "http://localhost:";
    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final RSAKey rsaKey = generateKey();

    private static String issuer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock.start();
        issuer = ISSUER_PREFIX + wireMock.port() + "/realms/market";
        String jwksUri = issuer + "/protocol/openid-connect/certs";

        wireMock.stubFor(get(urlEqualTo("/realms/market/.well-known/openid-configuration"))
                .willReturn(okJson("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + jwksUri + "\"}")));
        wireMock.stubFor(get(urlEqualTo("/realms/market/protocol/openid-connect/certs"))
                .willReturn(okJson(new JWKSet(rsaKey.toPublicJWK()).toString())));

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentService paymentService;

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("test-kid")
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String token(String jwtIssuer, String aud, String scope, Instant exp) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(jwtIssuer)
                    .subject("market-app-client")
                    .audience(aud)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(exp));
            if (scope != null) {
                claims.claim("scope", List.of(scope));
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
                    claims.build());
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void validToken_returns200() {
        when(paymentService.getBalance(1L)).thenReturn(Mono.just(new Balance(1L, 1L, BigDecimal.valueOf(5000), "RUB")));

        webTestClient.get().uri("/api/v1/balance/1")
                .header("Authorization", "Bearer " + token(issuer, "payment-service", "payment-service-audience",
                        Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void wrongAudience_returns401() {
        webTestClient.get().uri("/api/v1/balance/1")
                .header("Authorization", "Bearer " + token(issuer, "something-else", "payment-service-audience",
                        Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void missingScope_returns401() {
        webTestClient.get().uri("/api/v1/balance/1")
                .header("Authorization", "Bearer " + token(issuer, "payment-service", null,
                        Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredToken_returns401() {
        webTestClient.get().uri("/api/v1/balance/1")
                .header("Authorization", "Bearer " + token(issuer, "payment-service", "payment-service-audience",
                        Instant.now().minusSeconds(60)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongIssuer_returns401() {
        webTestClient.get().uri("/api/v1/balance/1")
                .header("Authorization", "Bearer " + token("http://wrong-issuer", "payment-service", "payment-service-audience",
                        Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
