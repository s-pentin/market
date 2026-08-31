package org.market.app.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.market.app.infra.TestContainers;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ProductCacheServiceTest extends TestContainers {

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ProductQueryService productQueryService;

    @AfterEach
    void tearDown() {
        redisTemplate.keys("*").flatMap(redisTemplate::delete).blockLast();
    }

    private Product product(long id) {
        return new Product(id, "Мяч", "Описание", "/img.jpg", BigDecimal.valueOf(100));
    }

    @Test
    void findById_cacheMiss_queriesDbThenCaches() {
        when(productQueryService.findById(1L)).thenReturn(Mono.just(product(1L)));

        StepVerifier.create(productCacheService.findById(1L))
                .assertNext(result -> assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();

        // Второй вызов — из кеша, без повторного обращения в БД.
        StepVerifier.create(productCacheService.findById(1L))
                .assertNext(result -> assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();

        verify(productQueryService, times(1)).findById(1L);
    }

    @Test
    void search_cacheMiss_queriesDbThenCaches() {
        ProductSearchResult result = new ProductSearchResult(List.of(product(1L)), 1L);
        when(productQueryService.search(null, SortType.NO, 1, 5)).thenReturn(Mono.just(result));

        StepVerifier.create(productCacheService.search(null, SortType.NO, 1, 5))
                .assertNext(r -> assertThat(r.getTotal()).isEqualTo(1L))
                .verifyComplete();

        StepVerifier.create(productCacheService.search(null, SortType.NO, 1, 5))
                .assertNext(r -> assertThat(r.getTotal()).isEqualTo(1L))
                .verifyComplete();

        verify(productQueryService, times(1)).search(null, SortType.NO, 1, 5);
    }

    @Test
    void evictAll_removesCachedProducts() {
        when(productQueryService.findById(1L)).thenReturn(Mono.just(product(1L)));
        productCacheService.findById(1L).block();

        StepVerifier.create(productCacheService.evictAll().then(productCacheService.findById(1L)))
                .assertNext(result -> assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();

        // После evictAll кеш пуст — БД запрошена повторно.
        verify(productQueryService, times(2)).findById(1L);
    }

    @Test
    void findById_expiresAfterTtl() {
        when(productQueryService.findById(1L)).thenReturn(Mono.just(product(1L)));

        productCacheService.findById(1L).block();
        verify(productQueryService, times(1)).findById(1L);

        awaitKeyExpiry("product:1");

        productCacheService.findById(1L).block();
        verify(productQueryService, times(2)).findById(1L);
    }

    private void awaitKeyExpiry(String key) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            Boolean exists = redisTemplate.hasKey(key).block();
            if (exists != null && !exists) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Redis key " + key + " did not expire within timeout");
    }
}
