package org.market.app.services;

import org.market.app.dto.ProductsPage;
import org.market.app.models.Product;
import org.market.app.properties.CacheProperties;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class ProductCacheService {

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String LIST_KEY_PREFIX = "products:list:";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Duration productTtl;
    private final Duration productListTtl;

    public ProductCacheService(ReactiveRedisTemplate<String, Object> redisTemplate, CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.productTtl = cacheProperties.productTtl();
        this.productListTtl = cacheProperties.productListTtl();
    }

    public Mono<Void> cacheProduct(Product product) {
        String key = PRODUCT_KEY_PREFIX + product.getId();
        return redisTemplate.opsForValue()
                .set(key, product, productTtl)
                .then();
    }

    public Mono<Product> getProduct(long id) {
        return redisTemplate.opsForValue()
                .get(PRODUCT_KEY_PREFIX + id)
                .cast(Product.class);
    }

    public Mono<Void> cacheProductList(String search, String sort, int pageNumber, int pageSize, ProductsPage page) {
        String key = buildListKey(search, sort, pageNumber, pageSize);
        return redisTemplate.opsForValue()
                .set(key, page, productListTtl)
                .then();
    }

    public Mono<ProductsPage> getProductList(String search, String sort, int pageNumber, int pageSize) {
        String key = buildListKey(search, sort, pageNumber, pageSize);
        return redisTemplate.opsForValue()
                .get(key)
                .cast(ProductsPage.class);
    }

    public Mono<Void> evictAll() {
        return redisTemplate.keys(PRODUCT_KEY_PREFIX + "*")
                .concatWith(redisTemplate.keys(LIST_KEY_PREFIX + "*"))
                .flatMap(redisTemplate::delete)
                .then();
    }

    private String buildListKey(String search, String sort, int pageNumber, int pageSize) {
        return LIST_KEY_PREFIX + search + ":" + sort + ":" + pageNumber + ":" + pageSize;
    }
}
