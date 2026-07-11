package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestPostgresContainer;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ImportTestcontainers(TestPostgresContainer.class)
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void findByProductId_found_returnsCartItem() {
        Product product = productRepository.save(new Product(null, "Ball", null, null, BigDecimal.valueOf(100)));
        cartItemRepository.save(new CartItem(null, product, 2));

        Optional<CartItem> result = cartItemRepository.findByProductId(product.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getCount()).isEqualTo(2);
        assertThat(result.get().getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    void findByProductId_notFound_returnsEmpty() {
        Optional<CartItem> result = cartItemRepository.findByProductId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void save_persistsCartItem() {
        Product product = productRepository.save(new Product(null, "Book", null, null, BigDecimal.valueOf(50)));
        CartItem saved = cartItemRepository.save(new CartItem(null, product, 1));

        assertThat(saved.getId()).isNotNull();
        assertThat(cartItemRepository.findAll()).hasSize(1);
    }
}
