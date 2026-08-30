package org.market.app.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.CartItem;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.models.Product;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.market.app.repositories.ProductRepository;
import org.market.app.repositories.UserRepository;
import org.market.app.security.AppUserDetails;
import org.market.app.services.OrderReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainers.class)
class CheckoutPaymentFailureIntegrationTest {

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

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReconciliationService orderReconciliationService;

    private UsernamePasswordAuthenticationToken auth;
    private User user;
    private Long productId;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/realms/market/protocol/openid-connect/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));

        user = userRepository.save(new User(null, "checkoutfail" + (counter++), "hash", Role.CUSTOMER, true)).block();
        AppUserDetails principal = new AppUserDetails(user);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        productId = productRepository.save(new Product(null, "Failure Product", "desc", null, BigDecimal.valueOf(100)))
                .block().getId();
        cartItemRepository.save(new CartItem(null, user.getId(), productId, 1)).block();
    }

    private void buy() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post().uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    private Orders theOnlyOrder() {
        List<Orders> orders = orderRepository.findAllByUserId(user.getId()).collectList().block();
        assertThat(orders).hasSize(1);
        return orders.getFirst();
    }

    @Test
    void insufficientFunds_marksOrderPaymentFailedAndKeepsCart() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(aResponse().withStatus(402).withBody("{\"success\":false,\"message\":\"insufficient\"}")));

        buy();

        assertThat(theOnlyOrder().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(cartItemRepository.findAllByUserId(user.getId()).collectList().block()).hasSize(1);
    }

    @Test
    void lostResponseAfterPayment_leavesOrderPendingPayment_notFailed() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        buy();

        assertThat(theOnlyOrder().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(cartItemRepository.findAllByUserId(user.getId()).collectList().block()).hasSize(1);
    }

    @Test
    void paymentServiceTemporarilyUnavailable_leavesOrderPendingPayment() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(okJson("{}").withFixedDelay(5000)));

        buy();

        assertThat(theOnlyOrder().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void reconciliation_findsSucceededPaymentAfterLostResponse_marksPaidAndClearsOrderedItems() {
        Orders stuck = Orders.builder()
                .userId(user.getId())
                .totalSum(BigDecimal.valueOf(100))
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .idempotencyKey(UUID.randomUUID())
                .build();
        Orders saved = orderRepository.save(stuck).block();

        wireMock.stubFor(get(urlEqualTo("/api/v1/payment/by-idempotency-key/" + saved.getIdempotencyKey()))
                .willReturn(okJson("{\"status\":\"SUCCEEDED\",\"newBalance\":4900,\"paymentId\":42}")));

        orderReconciliationService.reconcile();

        Orders reconciled = awaitOrderStatus(saved.getId(), OrderStatus.PAID);
        assertThat(reconciled.getPaymentId()).isEqualTo(42L);
        // order_items для этого заказа не создавались в тесте — корзину трогать нечего,
        assertThat(cartItemRepository.findAllByUserId(user.getId()).collectList().block()).hasSize(1);
    }

    private Orders awaitOrderStatus(Long orderId, OrderStatus expected) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Orders current = orderRepository.findById(orderId).block();
            if (current.getStatus() == expected) {
                return current;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("Order " + orderId + " did not reach status " + expected + " within timeout");
    }
}
