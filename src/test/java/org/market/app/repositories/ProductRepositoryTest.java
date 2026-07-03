package org.market.app.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.market.app.models.Product;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryTest extends TestPostgresContainer {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void findAll_returnsAllSavedProducts() {
        productRepository.save(new Product(null, "Ball", null, null, 100L));
        productRepository.save(new Product(null, "Book", null, null, 50L));

        assertThat(productRepository.findAll()).hasSize(2);
    }

    @Test
    void findByTitle_returnsMatchingByTitle() {
        productRepository.save(new Product(null, "Basketball", null, null, 100L));
        productRepository.save(new Product(null, "Football", null, null, 80L));
        productRepository.save(new Product(null, "Cup", null, null, 20L));

        List<Product> result = productRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("ball", "ball");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getTitle).containsExactlyInAnyOrder("Basketball", "Football");
    }

    @Test
    void findByDescription_returnsMatchingByDescription() {
        productRepository.save(new Product(null, "Cup", "Kitchen ball toy", null, 20L));
        productRepository.save(new Product(null, "Pen", "Writing tool", null, 10L));

        List<Product> result = productRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("ball", "ball");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("Cup");
    }

    @Test
    void findByTitle_caseInsensitive_returnsMatch() {
        productRepository.save(new Product(null, "BASKETBALL", null, null, 100L));

        List<Product> result = productRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("basketball", "basketball");

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductById_returnsCorrectProduct() {
        Product saved = productRepository.save(new Product(null, "Ball", null, null, 50L));

        Product result = productRepository.getProductById(saved.getId());

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Ball");
        assertThat(result.getId()).isEqualTo(saved.getId());
    }
}
