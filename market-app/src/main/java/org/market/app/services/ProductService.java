package org.market.app.services;

import org.market.app.dto.ItemDto;
import org.market.app.dto.Paging;
import org.market.app.dto.ProductsPage;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.CartItemRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Оркестратор витрины: берёт сырые товары через кеширующий {@link ProductCacheService},
 * добавляет «живые» счётчики корзины текущего пользователя и собирает {@link ProductsPage}.
 */
@Service
public class ProductService {

    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(2, 5, 10, 20, 50, 100);

    private final ProductCacheService productCacheService;
    private final ProductMapper productMapper;
    private final CartItemRepository cartItemRepository;

    public ProductService(ProductCacheService productCacheService,
                          ProductMapper productMapper,
                          CartItemRepository cartItemRepository) {
        this.productCacheService = productCacheService;
        this.productMapper = productMapper;
        this.cartItemRepository = cartItemRepository;
    }

    public Mono<ItemDto> getProductById(Long id, Long userId) {
        return productCacheService.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException()))
                .flatMap(product -> cartCountFor(userId, id)
                        .map(count -> productMapper.toItemDto(product, count)));
    }

    public Mono<ProductsPage> getProducts(String search, SortType sort, Integer pageNumber, Integer pageSize, Long userId) {
        int finalPageNumber = normalizePageNumber(pageNumber);
        int finalPageSize = normalizePageSize(pageSize);

        return productCacheService.search(search, sort, finalPageNumber, finalPageSize)
                .flatMap(result -> {
                    List<Long> productIds = result.getProducts().stream()
                            .map(Product::getId)
                            .toList();
                    return cartCountsFor(userId, productIds)
                            .map(cartCounts -> {
                                List<ItemDto> items = result.getProducts().stream()
                                        .map(product -> productMapper.toItemDto(
                                                product, cartCounts.getOrDefault(product.getId(), 0)))
                                        .toList();
                                return ProductsPage.builder()
                                        .items(items)
                                        .paging(buildPaging(finalPageNumber, finalPageSize, result.getTotal()))
                                        .search(search)
                                        .sort(sort)
                                        .build();
                            });
                });
    }

    private int normalizePageNumber(Integer pageNumber) {
        return pageNumber == null || pageNumber < 1 ? 1 : pageNumber;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || !ALLOWED_PAGE_SIZES.contains(pageSize) ? DEFAULT_PAGE_SIZE : pageSize;
    }

    private Paging buildPaging(int pageNumber, int pageSize, long total) {
        Paging paging = new Paging();
        paging.setPageNumber(pageNumber);
        paging.setPageSize(pageSize);
        paging.setHasPrevious(pageNumber > 1);
        paging.setHasNext((long) pageNumber * pageSize < total);
        return paging;
    }

    private Mono<Map<Long, Integer>> cartCountsFor(Long userId, List<Long> productIds) {
        if (userId == null || productIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return cartItemRepository.findAllByUserIdAndProductIdIn(userId, productIds)
                .collectMap(CartItem::getProductId, CartItem::getCount);
    }

    private Mono<Integer> cartCountFor(Long userId, Long productId) {
        if (userId == null) {
            return Mono.just(0);
        }
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(CartItem::getCount)
                .defaultIfEmpty(0);
    }
}
