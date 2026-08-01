package org.market.app.integration;

import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class FullUserFlowIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

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

        webTestClient.post().uri("/items?id=" + productId + "&action=PLUS")
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