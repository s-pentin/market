package org.market.app.integration;

import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.CartItem;
import org.market.app.models.OrderStatus;
import org.market.app.models.Orders;
import org.market.app.models.Product;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.OrderRepository;
import org.market.app.repositories.ProductRepository;
import org.market.app.repositories.UserRepository;
import org.market.app.services.PurchaseService;
import org.market.app.usecases.CheckoutUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Два параллельных checkout не должны мешать друг другу: у каждого пользователя
 * создаётся свой заказ и очищается только своя корзина.
 */
@SpringBootTest
@ImportTestcontainers(TestContainers.class)
class CheckoutConcurrencyTest {

    private static long counter = 0;

    @Autowired
    private CheckoutUseCase checkoutUseCase;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private PurchaseService purchaseService;

    @Test
    void twoConcurrentCheckouts_eachCreatesOrderAndClearsOwnCart() {
        User u1 = userRepository.save(new User(null, "cu1_" + (counter++), "hash", Role.CUSTOMER, true)).block();
        User u2 = userRepository.save(new User(null, "cu2_" + (counter++), "hash", Role.CUSTOMER, true)).block();
        Product product = productRepository.save(new Product(null, "Concurrent Product", "d", null, BigDecimal.valueOf(100))).block();

        cartItemRepository.save(new CartItem(null, u1.getId(), product.getId(), 2)).block();
        cartItemRepository.save(new CartItem(null, u2.getId(), product.getId(), 3)).block();

        when(purchaseService.pay(any(), any(), any(), any()))
                .thenReturn(Mono.just(new PaymentResponse().success(true).paymentId(1L)));

        Mono.when(
                checkoutUseCase.execute(u1.getId()),
                checkoutUseCase.execute(u2.getId())
        ).block();

        List<Orders> ordersU1 = orderRepository.findAllByUserId(u1.getId()).collectList().block();
        List<Orders> ordersU2 = orderRepository.findAllByUserId(u2.getId()).collectList().block();

        assertThat(ordersU1).hasSize(1);
        assertThat(ordersU2).hasSize(1);
        assertThat(ordersU1.getFirst().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(ordersU2.getFirst().getStatus()).isEqualTo(OrderStatus.PAID);

        assertThat(cartItemRepository.findAllByUserId(u1.getId()).collectList().block()).isEmpty();
        assertThat(cartItemRepository.findAllByUserId(u2.getId()).collectList().block()).isEmpty();
    }
}
