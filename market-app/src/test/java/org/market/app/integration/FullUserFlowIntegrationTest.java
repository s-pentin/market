package org.market.app.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Action;
import org.market.app.models.Product;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.repositories.ProductRepository;
import org.market.app.repositories.UserRepository;
import org.market.app.security.AppUserDetails;
import org.market.app.services.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class FullUserFlowIntegrationTest {

    private static long counter = 0;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private PurchaseService purchaseService;

    private UsernamePasswordAuthenticationToken auth;

    private Long productId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User(null, "ituser" + (counter++), "hash", Role.CUSTOMER, true)).block();
        AppUserDetails principal = new AppUserDetails(user);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        productId = productRepository.save(new Product(null, "Test Product", "desc", null, BigDecimal.valueOf(100)))
                .block().getId();

        when(purchaseService.getBalance(any())).thenReturn(Mono.just(BigDecimal.valueOf(50000)));
        when(purchaseService.pay(any(), any(), any(), any()))
                .thenReturn(Mono.just(new PaymentResponse().success(true)));
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
    }
}