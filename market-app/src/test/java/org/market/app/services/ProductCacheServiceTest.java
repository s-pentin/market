package org.market.app.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.market.app.dto.ItemDto;
import org.market.app.dto.Paging;
import org.market.app.dto.ProductsPage;
import org.market.app.infra.TestContainers;
import org.market.app.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheServiceTest extends TestContainers {

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.keys("*").flatMap(redisTemplate::delete).blockLast();
    }

    @Test
    void cacheProduct_and_getProduct_shouldReturnSavedProduct() {
        Product product = new Product(1L, "Мяч", "Описание", "/img.jpg", BigDecimal.valueOf(100));

        StepVerifier.create(
                productCacheService.cacheProduct(product)
                        .then(productCacheService.getProduct(1L))
        ).assertNext(result -> {
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("Мяч");
            assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
        }).verifyComplete();
    }

    @Test
    void getProduct_notCached_shouldReturnEmpty() {
        StepVerifier.create(productCacheService.getProduct(999L))
                .verifyComplete(); // пустой Mono — никакого значения
    }

    @Test
    void cacheProductList_and_getProductList_shouldReturnSavedPage() {
        ProductsPage page = ProductsPage.builder()
                .items(List.of(
                        List.of(ItemDto.builder().id(1L).title("A").price(BigDecimal.TEN).count(0).build())
                ))
                .search("test")
                .sort(org.market.app.models.SortType.NO)
                .paging(new Paging())
                .build();

        StepVerifier.create(
                productCacheService.cacheProductList("test", "NO", 1, 5, page)
                        .then(productCacheService.getProductList("test", "NO", 1, 5))
        ).assertNext(result -> {
            assertThat(result.getSearch()).isEqualTo("test");
            assertThat(result.getItems()).hasSize(1);
        }).verifyComplete();
    }

    @Test
    void getProductList_notCached_shouldReturnEmpty() {
        StepVerifier.create(productCacheService.getProductList("nothing", "NO", 1, 5))
                .verifyComplete();
    }

    @Test
    void evictAll_shouldRemoveAllKeys() {
        Product product = new Product(1L, "Мяч", null, null, BigDecimal.ONE);
        ProductsPage page = ProductsPage.builder()
                .items(List.of())
                .search("x")
                .sort(org.market.app.models.SortType.NO)
                .build();

        StepVerifier.create(
                productCacheService.cacheProduct(product)
                        .then(productCacheService.cacheProductList("x", "NO", 1, 5, page))
                        .then(productCacheService.evictAll())
                        .then(productCacheService.getProduct(1L))
        ).verifyComplete(); // после evictAll — пусто
    }

    @Test
    void cacheProduct_shouldExpireAfterTtl() throws Exception {
        Product product = new Product(1L, "Мяч", null, null, BigDecimal.ONE);

        StepVerifier.create(
                productCacheService.cacheProduct(product)
                        .then(productCacheService.getProduct(1L))
        ).assertNext(result -> assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();

        // Ждём истечения TTL (500ms + запас)
        Thread.sleep(800);

        StepVerifier.create(productCacheService.getProduct(1L))
                .verifyComplete(); // ключ истёк
    }
}