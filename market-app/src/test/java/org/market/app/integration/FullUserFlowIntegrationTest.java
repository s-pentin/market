package org.market.app.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Action;
import org.market.app.models.Product;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.ProductRepository;
import org.market.app.repositories.UserRepository;
import org.market.app.security.AppUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class FullUserFlowIntegrationTest {

    private static long counter = 0;

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        wireMock.start();
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> "http://localhost:" + wireMock.port() + "/realms/market/protocol/openid-connect/token");
        registry.add("app.payment.service.url", () -> "http://localhost:" + wireMock.port());
    }

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private UsernamePasswordAuthenticationToken auth;

    private Long productId;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/realms/market/protocol/openid-connect/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));

        User user = userRepository.save(new User(null, "ituser" + (counter++), "hash", Role.CUSTOMER, true)).block();
        AppUserDetails principal = new AppUserDetails(user);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        productId = productRepository.save(new Product(null, "Test Product", "desc", null, BigDecimal.valueOf(100)))
                .block().getId();

        wireMock.stubFor(get(urlEqualTo("/api/v1/balance/" + user.getId()))
                .willReturn(okJson("{\"balance\":50000,\"currency\":\"RUB\"}")));
        wireMock.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(okJson("{\"success\":true,\"newBalance\":49800,\"paymentId\":1,\"message\":\"OK\"}")));
    }

    @Test
    void fullUserFlow_buyProduct_shouldCreateOrderAndClearCart() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(productId));
        form.add("action", Action.PLUS.name());

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post().uri("/items")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Итого:"));

        AtomicReference<String> orderPath = new AtomicReference<>();
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> {
                    assertThat(loc).contains("/orders/");
                    orderPath.set(loc);
                });

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get().uri(orderPath.get())
                .exchange()
                .expectStatus().isOk();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Корзина пуста"));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/payment"))
                .withHeader("Authorization", matching(".+")));
    }
}
