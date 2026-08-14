package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ImportTestcontainers(TestContainers.class)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @BeforeEach
    void setUp() {
        // cart_item.product_id ссылается на product — этот класс делит один Testcontainers-контейнер с CartItemRepositoryTest
        cartItemRepository.deleteAll().block();
        productRepository.deleteAll().block();
    }

    @Test
    void findAll_returnsAllSavedProducts() {
        productRepository.saveAll(List.of(
                new Product(null, "Ball", null, null, BigDecimal.valueOf(100)),
                new Product(null, "Book", null, null, BigDecimal.valueOf(50))
        )).blockLast();

        StepVerifier.create(productRepository.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findByTitle_returnsMatchingByTitle() {
        productRepository.saveAll(List.of(
                new Product(null, "Basketball", null, null, BigDecimal.valueOf(100)),
                new Product(null, "Football", null, null, BigDecimal.valueOf(80)),
                new Product(null, "Cup", null, null, BigDecimal.valueOf(20))
        )).blockLast();

        StepVerifier.create(
                productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                        "ball", "ball", Pageable.unpaged()))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findByDescription_returnsMatchingByDescription() {
        productRepository.saveAll(List.of(
                new Product(null, "Cup", "Kitchen ball toy", null, BigDecimal.valueOf(20)),
                new Product(null, "Pen", "Writing tool", null, BigDecimal.valueOf(10))
        )).blockLast();

        StepVerifier.create(
                productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                        "ball", "ball", Pageable.unpaged()))
                .assertNext(p -> assertThat(p.getTitle()).isEqualTo("Cup"))
                .verifyComplete();
    }

    @Test
    void findByTitle_caseInsensitive_returnsMatch() {
        productRepository.save(new Product(null, "BASKETBALL", null, null, BigDecimal.valueOf(100))).block();

        StepVerifier.create(
                productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                        "basketball", "basketball", Pageable.unpaged()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void findById_returnsCorrectProduct() {
        Product saved = productRepository.save(
                new Product(null, "Ball", null, null, BigDecimal.valueOf(50))).block();

        StepVerifier.create(productRepository.findById(saved.getId()))
                .assertNext(p -> {
                    assertThat(p.getTitle()).isEqualTo("Ball");
                    assertThat(p.getId()).isEqualTo(saved.getId());
                })
                .verifyComplete();
    }

    @Test
    void findAllBy_withPageable_returnsPaginatedResult() {
        for (int i = 1; i <= 5; i++) {
            productRepository.save(new Product(null, "Product " + i, null, null,
                    BigDecimal.valueOf(i * 10))).block();
        }

        StepVerifier.create(productRepository.findAllBy(PageRequest.of(0, 3)))
                .expectNextCount(3)
                .verifyComplete();

        StepVerifier.create(productRepository.count())
                .assertNext(count -> assertThat(count).isEqualTo(5))
                .verifyComplete();
    }
}