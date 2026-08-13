package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ImportTestcontainers(TestContainers.class)
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll().block();
        productRepository.deleteAll().block();
    }

    @Test
    void findByProductId_found_returnsCartItem() {
        Product product = productRepository.save(
                new Product(null, "Ball", null, null, BigDecimal.valueOf(100))).block();
        cartItemRepository.save(new CartItem(null, product.getId(), 2)).block();

        StepVerifier.create(cartItemRepository.findByProductId(product.getId()))
                .assertNext(ci -> {
                    assertThat(ci.getCount()).isEqualTo(2);
                    assertThat(ci.getProductId()).isEqualTo(product.getId());
                })
                .verifyComplete();
    }

    @Test
    void findByProductId_notFound_returnsEmpty() {
        StepVerifier.create(cartItemRepository.findByProductId(999L))
                .verifyComplete();
    }

    @Test
    void save_persistsCartItem() {
        Product product = productRepository.save(
                new Product(null, "Book", null, null, BigDecimal.valueOf(50))).block();

        StepVerifier.create(cartItemRepository.save(new CartItem(null, product.getId(), 1)))
                .assertNext(saved -> assertThat(saved.getId()).isNotNull())
                .verifyComplete();

        StepVerifier.create(cartItemRepository.findAll())
                .expectNextCount(1)
                .verifyComplete();
    }
}