package org.market.app.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Action;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.repositories.ProductRepository;
import org.market.app.services.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class FullUserFlowIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

    @MockBean
    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        when(purchaseService.getBalance()).thenReturn(Mono.just(BigDecimal.valueOf(50000)));
        when(purchaseService.pay(eq(null), any(BigDecimal.class)))
                .thenReturn(Mono.just(new PaymentResponse().success(true)));
        when(purchaseService.canCheckout(any(BigDecimal.class))).thenReturn(Mono.just(true));
    }

    /**
     * 1. Открываем каталог
     * 2. Добавляем товар в корзину
     * 3. Открываем корзину
     * 4. Оформляем заказ
     * 5. Проверяем страницу заказа
     * 6. Проверяем, что корзина пуста
     */
    @Test
    void fullUserFlow_buyProduct_shouldCreateOrderAndClearCart() {
        Long productId = productRepository.findAll().blockFirst().getId();

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(productId));
        form.add("action", Action.PLUS.name());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Итого:"));

        AtomicReference<String> orderPath = new AtomicReference<>();
        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> {
                    assertThat(loc).contains("/orders/");
                    orderPath.set(loc);
                });

        webTestClient.get().uri(orderPath.get())
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Корзина пуста"));
    }
}