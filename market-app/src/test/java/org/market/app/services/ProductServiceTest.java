package org.market.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.ItemDto;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

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
    private ProductRepository productRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductCacheService productCacheService;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // По умолчанию — нет кеша и пустая корзина
        lenient().when(productCacheService.getProduct(anyLong())).thenReturn(Mono.empty());
        lenient().when(productCacheService.getProductList(any(), anyString(), anyInt(), anyInt())).thenReturn(Mono.empty());
        lenient().when(productCacheService.cacheProduct(any())).thenReturn(Mono.empty());
        lenient().when(productCacheService.cacheProductList(any(), anyString(), anyInt(), anyInt(), any())).thenReturn(Mono.empty());
        lenient().when(cartItemRepository.findAllByUserIdAndProductIdIn(anyLong(), anyList())).thenReturn(Flux.empty());
        lenient().when(cartItemRepository.findByUserIdAndProductId(anyLong(), anyLong())).thenReturn(Mono.empty());
    }

    @Test
    void getProductById_returnsItemDtoWithCartCount() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, USER_ID, 1L, 3);

        when(productRepository.findById(1L)).thenReturn(Mono.just(product));
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
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));

        when(productRepository.findById(1L)).thenReturn(Mono.just(product));

        StepVerifier.create(productService.getProductById(1L, USER_ID))
                .assertNext(result -> assertThat(result.getCount()).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void getProductById_anonymous_returnsZeroCount() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));

        when(productRepository.findById(1L)).thenReturn(Mono.just(product));

        StepVerifier.create(productService.getProductById(1L, null))
                .assertNext(result -> assertThat(result.getCount()).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void getProducts_noSearch_returnsAllProducts() {
        List<Product> products = List.of(
                new Product(1L, "Apple", null, null, BigDecimal.valueOf(50)),
                new Product(2L, "Banana", null, null, BigDecimal.valueOf(30))
        );
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.fromIterable(products));
        when(productRepository.count()).thenReturn(Mono.just(2L));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 5, USER_ID))
                .assertNext(result -> {
                    assertThat(result.getSearch()).isNull();
                    assertThat(result.getItems()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void getProducts_withSearch_filtersProducts() {
        List<Product> filtered = List.of(new Product(1L, "Ball", null, null, BigDecimal.valueOf(100)));
        when(productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"), any(Pageable.class))).thenReturn(Flux.fromIterable(filtered));
        when(productRepository.countByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"))).thenReturn(Mono.just(1L));

        StepVerifier.create(productService.getProducts("ball", SortType.NO, 1, 5, USER_ID))
                .assertNext(result -> assertThat(result.getSearch()).isEqualTo("ball"))
                .verifyComplete();

        verify(productRepository).findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"), any(Pageable.class));
    }

    @Test
    void getProducts_alphaSort_passesTitleSortToRepository() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productService.getProducts(null, SortType.ALPHA, 1, 5, USER_ID))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("title")).isNotNull();
    }

    @Test
    void getProducts_priceSort_passesPriceSortToRepository() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productService.getProducts(null, SortType.PRICE, 1, 5, USER_ID))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("price")).isNotNull();
    }

    @Test
    void getProducts_pagination_secondPage_passesCorrectPageable() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 2, 2, USER_ID))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void getProducts_paging_hasNextAndPrevious() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(
                Flux.just(new Product(1L, "X", null, null, BigDecimal.valueOf(10))));
        when(productRepository.count()).thenReturn(Mono.just(15L));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 2, 5, USER_ID))
                .assertNext(result -> {
                    assertThat(result.getPaging().getPageNumber()).isEqualTo(2);
                    assertThat(result.getPaging().isHasPrevious()).isTrue();
                    assertThat(result.getPaging().isHasNext()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void getProducts_splitIntoRows_padsLastRowWithStubs() {
        List<Product> products = List.of(
                new Product(1L, "AAA", null, null, BigDecimal.valueOf(10)),
                new Product(2L, "BBB", null, null, BigDecimal.valueOf(20))
        );
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.fromIterable(products));
        when(productRepository.count()).thenReturn(Mono.just(2L));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 5, USER_ID))
                .assertNext(result -> {
                    List<ItemDto> row = result.getItems().getFirst();
                    assertThat(row).hasSize(3);
                    assertThat(row.get(2).getId()).isEqualTo(-1L);
                })
                .verifyComplete();
    }

    @Test
    void getProducts_invalidPageSize_normalizedToDefault() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 7, USER_ID))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }
}