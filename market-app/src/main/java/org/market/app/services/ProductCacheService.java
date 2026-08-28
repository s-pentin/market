package org.market.app.services;

import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.properties.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cache-aside декоратор над {@link ProductQueryService}: сначала Redis, затем— БД. Запись в кеш не прерывает вызов
 */
@Service
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);
    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String LIST_KEY_PREFIX = "products:list:";

    private final ProductQueryService productQueryService;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Duration productTtl;
    private final Duration productListTtl;

    public ProductCacheService(ProductQueryService productQueryService,
                               ReactiveRedisTemplate<String, Object> redisTemplate,
                               CacheProperties cacheProperties) {
        this.productQueryService = productQueryService;
        this.redisTemplate = redisTemplate;
        this.productTtl = cacheProperties.productTtl();
        this.productListTtl = cacheProperties.productListTtl();
    }

    public Mono<Product> findById(Long id) {
        return read(PRODUCT_KEY_PREFIX + id, Product.class)
                .switchIfEmpty(Mono.defer(() -> productQueryService.findById(id)
                        .flatMap(product -> write(PRODUCT_KEY_PREFIX + id, product, productTtl)
                                .thenReturn(product))));
    }

    public Mono<ProductSearchResult> search(String search, SortType sort, int pageNumber, int pageSize) {
        String key = LIST_KEY_PREFIX + search + ":" + sort.name() + ":" + pageNumber + ":" + pageSize;
        return read(key, ProductSearchResult.class)
                .switchIfEmpty(Mono.defer(() -> productQueryService.search(search, sort, pageNumber, pageSize)
                        .flatMap(result -> write(key, result, productListTtl).thenReturn(result))));
    }

    public Mono<Void> evictAll() {
        return redisTemplate.scan(ScanOptions.scanOptions().match(PRODUCT_KEY_PREFIX + "*").count(500).build())
                .concatWith(redisTemplate.scan(ScanOptions.scanOptions().match(LIST_KEY_PREFIX + "*").count(500).build()))
                .flatMap(key -> redisTemplate.delete(key))
                .onErrorResume(e -> {
                    log.warn("Failed to evict product cache: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private <T> Mono<T> read(String key, Class<T> type) {
        return redisTemplate.opsForValue()
                .get(key)
                .cast(type)
                .onErrorResume(e -> {
                    log.warn("Redis read failed for {}: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> write(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(key, value, ttl)
                .onErrorResume(e -> {
                    log.warn("Redis write failed for {}: {}", key, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
