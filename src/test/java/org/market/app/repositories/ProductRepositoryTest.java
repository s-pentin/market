package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestPostgresContainer;
import org.market.app.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductRepositoryTest extends TestPostgresContainer {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void findAll_returnsAllSavedProducts() {
        productRepository.save(new Product(null, "Ball", null, null, BigDecimal.valueOf(100)));
        productRepository.save(new Product(null, "Book", null, null, BigDecimal.valueOf(50)));

        assertThat(productRepository.findAll()).hasSize(2);
    }

    @Test
    void findByTitle_returnsMatchingByTitle() {
        productRepository.save(new Product(null, "Basketball", null, null, BigDecimal.valueOf(100)));
        productRepository.save(new Product(null, "Football", null, null, BigDecimal.valueOf(80)));
        productRepository.save(new Product(null, "Cup", null, null, BigDecimal.valueOf(20)));

        Page<Product> result = productRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("ball", "ball", Pageable.unpaged());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Product::getTitle)
                .containsExactlyInAnyOrder("Basketball", "Football");
    }

    @Test
    void findByDescription_returnsMatchingByDescription() {
        productRepository.save(new Product(null, "Cup", "Kitchen ball toy", null, BigDecimal.valueOf(20)));
        productRepository.save(new Product(null, "Pen", "Writing tool", null, BigDecimal.valueOf(10)));

        Page<Product> result = productRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("ball", "ball", Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getTitle()).isEqualTo("Cup");
    }

    @Test
    void findByTitle_caseInsensitive_returnsMatch() {
        productRepository.save(new Product(null, "BASKETBALL", null, null, BigDecimal.valueOf(100)));

        Page<Product> result = productRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("basketball", "basketball", Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findById_returnsCorrectProduct() {
        Product saved = productRepository.save(new Product(null, "Ball", null, null, BigDecimal.valueOf(50)));

        Optional<Product> result = productRepository.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Ball");
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findAll_withPageable_returnsPaginatedResult() {
        for (int i = 1; i <= 5; i++) {
            productRepository.save(new Product(null, "Product " + i, null, null, BigDecimal.valueOf(i * 10)));
        }

        Page<Product> page = productRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }
}
