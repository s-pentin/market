package org.market.app.integration;

import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.payment.model.PaymentResponse;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.market.app.repositories.UserRepository;
import org.market.app.services.PurchaseService;
import org.market.app.usecases.CheckoutUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Состав заказа читается ДО сетевого вызова к payment-service.
 * Если пользователь успевает добавить в корзину новый товар, пока идёт оплата уже прочитанного
 * набора, финализация заказа не должна затронуть этот новый товар
 */
@SpringBootTest
@ImportTestcontainers(TestContainers.class)
class CheckoutCartSnapshotIntegrationTest {

    private static long counter = 0;

    @Autowired
    private CheckoutUseCase checkoutUseCase;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private PurchaseService purchaseService;

    @Test
    void execute_productAddedDuringPayment_isNotRemovedFromCart() {
        User user = userRepository.save(new User(null, "snapshotuser" + (counter++), "hash", Role.CUSTOMER, true)).block();
        Product productA = productRepository.save(new Product(null, "Product A", "d", null, BigDecimal.valueOf(100))).block();
        Product productB = productRepository.save(new Product(null, "Product B", "d", null, BigDecimal.valueOf(50))).block();

        cartItemRepository.save(new CartItem(null, user.getId(), productA.getId(), 1)).block();

        // purchaseService.pay имитирует товар Б, добавленный пользователем ПОСЛЕ снимка корзины,
        // но ДО того, как пришёл ответ от payment-service.
        when(purchaseService.pay(any(), any(), any(), any())).thenReturn(
                cartItemRepository.save(new CartItem(null, user.getId(), productB.getId(), 1))
                        .thenReturn(new PaymentResponse().success(true).paymentId(1L)));

        Long orderId = checkoutUseCase.execute(user.getId()).block();

        assertThat(orderId).isNotNull();
        List<CartItem> remaining = cartItemRepository.findAllByUserId(user.getId()).collectList().block();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getProductId()).isEqualTo(productB.getId());
    }
}
