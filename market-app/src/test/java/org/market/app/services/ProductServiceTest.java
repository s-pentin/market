package org.market.app.services;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void getProductById_returnsItemDtoWithCartCount() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));
        CartItem cartItem = new CartItem(1L, 1L, 3);

        when(productRepository.findById(1L)).thenReturn(Mono.just(product));
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.just(cartItem));

        StepVerifier.create(productService.getProductById(1L))
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
        when(cartItemRepository.findByProductId(1L)).thenReturn(Mono.empty());

        StepVerifier.create(productService.getProductById(1L))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 5))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts("ball", SortType.NO, 1, 5))
                .assertNext(result -> assertThat(result.getSearch()).isEqualTo("ball"))
                .verifyComplete();

        verify(productRepository).findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"), any(Pageable.class));
    }

    @Test
    void getProducts_alphaSort_passesTitleSortToRepository() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.ALPHA, 1, 5))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.PRICE, 1, 5))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.NO, 2, 2))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.NO, 2, 5))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 5))
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
        when(cartItemRepository.findAllByProductIdIn(any())).thenReturn(Flux.empty());

        StepVerifier.create(productService.getProducts(null, SortType.NO, 1, 7))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }
}
