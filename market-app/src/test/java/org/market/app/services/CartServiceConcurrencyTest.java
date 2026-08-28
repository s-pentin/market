package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Action;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.Role;
import org.market.app.models.User;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.market.app.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет атомарность инкремента количества товара в корзине при параллельных
 * запросах на реальном PostgreSQL (upsert с ON CONFLICT DO UPDATE).
 */
@SpringBootTest
@ImportTestcontainers(TestContainers.class)
class CartServiceConcurrencyTest {

    private static long counter = 0;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void parallelPlus_sameProduct_finalCountIsTwo() {
        User user = userRepository.save(new User(null, "cartuser" + (counter++), "hash", Role.CUSTOMER, true)).block();
        Product product = productRepository.save(new Product(null, "Test Product", "d", null, BigDecimal.TEN)).block();

        Mono.when(
                cartService.changeCount(user.getId(), product.getId(), Action.PLUS),
                cartService.changeCount(user.getId(), product.getId(), Action.PLUS)
        ).block();

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId()).block();
        assertThat(item.getCount()).isEqualTo(2);
    }
}
