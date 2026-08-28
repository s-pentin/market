package org.market.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.ItemDto;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.CartItemRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private CartItemRepository cartItemRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productCacheService, new ProductMapper(), cartItemRepository);
        lenient().when(cartItemRepository.findAllByUserIdAndProductIdIn(anyLong(), anyList())).thenReturn(Flux.empty());
        lenient().when(cartItemRepository.findByUserIdAndProductId(anyLong(), anyLong())).thenReturn(Mono.empty());
    }

    private Product product(long id, String title) {
        return new Product(id, title, "desc", "/img.jpg", BigDecimal.valueOf(100));
    }

    @Test
    void getProductById_returnsItemDtoWithCartCount() {
        Product product = product(1L, "Ball");
        CartItem cartItem = new CartItem(1L, USER_ID, 1L, 3);

        when(productCacheService.findById(1L)).thenReturn(Mono.just(product));
        when(cartItemRepository.findByUserIdAndProductId(USER_ID, 1L)).thenReturn(Mono.just(cartItem));

        StepVerifier.create(productService.getProductById(1L, USER_ID))
                .assertNext(result -> {
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getTitle()).isEqualTo("Ball");
                    assertThat(result.getCount()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void getProductById_notInCart_returnsZeroCount() {
        when(productCacheService.findById(1L)).thenReturn(Mono.just(product(1L, "Ball")));

        StepVerifier.create(productService.getProductById(1L, USER_ID))
                .assertNext(result -> assertThat(result.getCount()).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void getProductById_anonymous_returnsZeroCount() {
        when(productCacheService.findById(1L)).thenReturn(Mono.just(product(1L, "Ball")));

        StepVerifier.create(productService.getProductById(1L, null))
                .assertNext(result -> assertThat(result.getCount()).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void getProductById_notFound_throwsProductNotFoundException() {
        when(productCacheService.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(productService.getProductById(1L, USER_ID))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void getProducts_noSearch_returnsFlatItemList() {
        List<Product> products = List.of(product(1L, "Apple"), product(2L, "Banana"));
        when(productCacheService.search(eq(null), any(SortType.class), anyInt(), anyInt()))
                .thenReturn(Mono.just(new ProductSearchResult(products, 2L)));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 5, USER_ID))
                .assertNext(result -> {
                    assertThat(result.getSearch()).isNull();
                    assertThat(result.getItems()).hasSize(2);
                    assertThat(result.getItems()).allMatch(item -> item instanceof ItemDto);
                })
                .verifyComplete();
    }

    @Test
    void getProducts_addsLiveCartCounts() {
        List<Product> products = List.of(product(1L, "Apple"), product(2L, "Banana"));
        when(productCacheService.search(eq(null), any(SortType.class), anyInt(), anyInt()))
                .thenReturn(Mono.just(new ProductSearchResult(products, 2L)));
        when(cartItemRepository.findAllByUserIdAndProductIdIn(eq(USER_ID), anyList()))
                .thenReturn(Flux.just(new CartItem(1L, USER_ID, 1L, 4)));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 5, USER_ID))
                .assertNext(result -> {
                    Map<Long, Integer> counts = result.getItems().stream()
                            .collect(java.util.stream.Collectors.toMap(ItemDto::getId, ItemDto::getCount));
                    assertThat(counts.get(1L)).isEqualTo(4);
                    assertThat(counts.get(2L)).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void getProducts_withSearch_passesSearchToCache() {
        when(productCacheService.search(eq("ball"), any(SortType.class), anyInt(), anyInt()))
                .thenReturn(Mono.just(new ProductSearchResult(List.of(), 0L)));

        StepVerifier.create(productService.getProducts("ball", SortType.NO, 1, 5, USER_ID))
                .expectNextCount(1)
                .verifyComplete();

        verify(productCacheService).search(eq("ball"), eq(SortType.NO), anyInt(), anyInt());
    }

    @Test
    void getProducts_invalidPageSize_normalizedToDefault() {
        when(productCacheService.search(any(), any(SortType.class), anyInt(), anyInt()))
                .thenReturn(Mono.just(new ProductSearchResult(List.of(), 0L)));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 7, USER_ID))
                .expectNextCount(1)
                .verifyComplete();

        verify(productCacheService).search(any(), any(SortType.class), eq(1), eq(5));
    }
}
