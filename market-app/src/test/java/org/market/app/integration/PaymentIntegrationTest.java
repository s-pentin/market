package org.market.app.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Action;
import org.market.app.payment.api.BalanceApi;
import org.market.app.payment.api.PaymentApi;
import org.market.app.payment.model.BalanceResponse;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Интеграционный тест полного пользовательского сценария с реальным PurchaseService.
 * Мокаются только сгенерированные HTTP-клиенты (BalanceApi/PaymentApi) —
 * вся остальная цепочка (контроллеры → сервисы → репозитории → БД) работает по-настоящему.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class PaymentIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

    @MockBean
    private BalanceApi balanceApi;

    @MockBean
    private PaymentApi paymentApi;

    @BeforeEach
    void setUp() {
        when(balanceApi.getBalance())
                .thenReturn(Mono.just(new BalanceResponse().balance(BigDecimal.valueOf(5000)).currency("RUB")));
        when(paymentApi.processPayment(any()))
                .thenReturn(Mono.just(new PaymentResponse().success(true).newBalance(BigDecimal.valueOf(4000)).message("OK")));
    }

    /**
     * Полный happy-path:
     * 1. Открываем каталог
     * 2. Добавляем товар в корзину
     * 3. Открываем корзину — видим товар и баланс
     * 4. Оформляем заказ (платёж проходит)
     * 5. Редирект на страницу заказа
     * 6. Корзина пуста
     */
    @Test
    void fullFlow_withPayment_shouldCreateOrderAndClearCart() {
        Long productId = productRepository.findAll().blockFirst().getId();

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("id", String.valueOf(productId));
        addForm.add("action", Action.PLUS.name());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData(addForm))
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Итого:");
                    assertThat(body).contains("Баланс:");
                    assertThat(body).contains("5000");
                });

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

    /**
     * Недостаточно средств:
     * 1. Кладём товар в корзину
     * 2. Мокаем маленький баланс
     * 3. Пытаемся оформить заказ → редирект на корзину с ошибкой
     * 4. Заказ НЕ создаётся
     */
    @Test
    void checkout_insufficientFunds_shouldRedirectToCartWithError() {
        when(balanceApi.getBalance())
                .thenReturn(Mono.just(new BalanceResponse().balance(BigDecimal.valueOf(10)).currency("RUB")));

        Long productId = productRepository.findAll().blockFirst().getId();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("id", String.valueOf(productId));
        addForm.add("action", Action.PLUS.name());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData(addForm))
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("disabled");
                    assertThat(body).contains("10"); // баланс
                });

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc ->
                        assertThat(loc).contains("/cart/items?error=insufficient_funds"));
    }

    /**
     * Сервис платежей недоступен:
     * 1. Кладём товар в корзину
     * 2. Мокаем ошибку соединения
     * 3. Кнопка оформления недоступна
     * 4. Попытка оформления → редирект с payment_unavailable
     */
    @Test
    void checkout_paymentServiceUnavailable_shouldShowError() {
        when(balanceApi.getBalance())
                .thenReturn(Mono.error(mock(WebClientRequestException.class)));

        Long productId = productRepository.findAll().blockFirst().getId();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("id", String.valueOf(productId));
        addForm.add("action", Action.PLUS.name());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData(addForm))
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Сервис платежей недоступен");
                    assertThat(body).contains("disabled");
                });

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc ->
                        assertThat(loc).contains("/cart/items?error=payment_unavailable"));
    }

    /**
     * Платёж проходит успешно при достаточном балансе.
     */
    @Test
    void checkout_sufficientFunds_shouldCompleteOrder() {
        when(balanceApi.getBalance())
                .thenReturn(Mono.just(new BalanceResponse().balance(BigDecimal.valueOf(99999)).currency("RUB")));

        Long productId = productRepository.findAll().blockFirst().getId();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("id", String.valueOf(productId));
        addForm.add("action", Action.PLUS.name());

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData(addForm))
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/cart/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("99999");
                    assertThat(body).doesNotContain("disabled");
                });

        webTestClient.post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc ->
                        assertThat(loc).contains("/orders/"));
    }
}