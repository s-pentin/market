package org.market.app.services;

import org.market.app.dto.ItemDto;
import org.market.app.dto.Paging;
import org.market.app.dto.ProductsPage;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private static final int ROW_SIZE = 3;
    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(2, 5, 10, 20, 50, 100);

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductCacheService productCacheService;

    public ProductService(ProductRepository productRepository, CartItemRepository cartItemRepository, ProductCacheService productCacheService) {
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.productCacheService = productCacheService;
    }

    public Mono<ItemDto> getProductById(Long id, Long userId) {
        return productCacheService.getProduct(id)
                .flatMap(product -> buildItemDto(product, userId))
                .switchIfEmpty(
                        productRepository.findById(id)
                                .switchIfEmpty(Mono.error(new ProductNotFoundException()))
                                .flatMap(product -> productCacheService.cacheProduct(product)
                                        .then(buildItemDto(product, userId)))
                );
    }

    public Mono<ProductsPage> getProducts(String search, SortType sort, Integer pageNumber, Integer pageSize, Long userId) {
        int finalPageNumber = normalizePageNumber(pageNumber);
        int finalPageSize = normalizePageSize(pageSize);
        String sortStr = sort.name();

        return productCacheService.getProductList(search, sortStr, finalPageNumber, finalPageSize)
                .switchIfEmpty(
                        loadProductsFromDB(search, sort, finalPageNumber, finalPageSize)
                                .flatMap(page -> productCacheService
                                        .cacheProductList(search, sortStr, finalPageNumber, finalPageSize, page)
                                        .thenReturn(page))
                )
                .flatMap(page -> withFreshCartCounts(page, userId));
    }

    /**
     * Кеш хранит только данные товара, а количество в корзине всегда пересчитывается
     * под конкретного пользователя (или 0 для анонима), чтобы не показывать чужие значения.
     */
    private Mono<ProductsPage> withFreshCartCounts(ProductsPage page, Long userId) {
        List<ItemDto> items = page.getItems().stream().flatMap(List::stream).toList();
        List<Long> productIds = items.stream()
                .map(ItemDto::getId)
                .filter(id -> id != -1L)
                .toList();

        return cartCountsFor(userId, productIds)
                .map(cartCounts -> {
                    items.forEach(item -> item.setCount(cartCounts.getOrDefault(item.getId(), 0)));
                    return page;
                });
    }

    private Mono<ProductsPage> loadProductsFromDB(String search, SortType sort, int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize, toSort(sort));

        Flux<Product> productsFlux;
        Mono<Long> countMono;
        if (search != null && !search.isBlank()) {
            productsFlux = productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(search, search, pageable);
            countMono = productRepository.countByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(search, search);
        } else {
            productsFlux = productRepository.findAllBy(pageable);
            countMono = productRepository.count();
        }

        return productsFlux.collectList()
                .zipWith(countMono)
                .map(tuple -> buildProductsPage(tuple.getT1(), tuple.getT2(), search, sort, pageNumber, pageSize));
    }

    private Mono<ItemDto> buildItemDto(Product product, Long userId) {
        return cartCountFor(userId, product.getId())
                .map(count -> ItemDto.builder()
                        .id(product.getId())
                        .title(product.getTitle())
                        .description(product.getDescription())
                        .imgPath(product.getImgPath())
                        .price(product.getPrice())
                        .count(count)
                        .build());
    }

    private int normalizePageNumber(Integer pageNumber) {
        return pageNumber == null || pageNumber < 1 ? 1 : pageNumber;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || !ALLOWED_PAGE_SIZES.contains(pageSize) ? DEFAULT_PAGE_SIZE : pageSize;
    }

    private ProductsPage buildProductsPage(List<Product> products,
                                           Long total,
                                           String search,
                                           SortType sort,
                                           int pageNumber,
                                           int pageSize) {
        List<ItemDto> itemDtos = products.stream()
                .map(this::toItemDto)
                .toList();

        return ProductsPage.builder()
                .items(splitIntoRows(itemDtos))
                .paging(buildPaging(pageNumber, pageSize, total))
                .search(search)
                .sort(sort)
                .build();
    }

    private Sort toSort(SortType sortType) {
        return switch (sortType) {
            case ALPHA -> Sort.by("title");
            case PRICE -> Sort.by("price");
            case NO -> Sort.unsorted();
        };
    }

    private Paging buildPaging(int pageNumber, int pageSize, long total) {
        Paging paging = new Paging();
        paging.setPageNumber(pageNumber);
        paging.setPageSize(pageSize);
        paging.setHasPrevious(pageNumber > 1);
        paging.setHasNext((long) pageNumber * pageSize < total);
        return paging;
    }

    private ItemDto toItemDto(Product product) {
        return ItemDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .imgPath(product.getImgPath())
                .price(product.getPrice())
                .count(0)
                .build();
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

    private List<List<ItemDto>> splitIntoRows(List<ItemDto> items) {
        List<List<ItemDto>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i += ROW_SIZE) {
            List<ItemDto> row = new ArrayList<>(items.subList(i, Math.min(i + ROW_SIZE, items.size())));
            while (row.size() < ROW_SIZE) {
                ItemDto stub = ItemDto.builder().id(-1L).build();
                row.add(stub);
            }
            rows.add(row);
        }
        return rows;
    }
}