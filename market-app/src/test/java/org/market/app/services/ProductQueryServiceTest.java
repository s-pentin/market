package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.models.Product;
import org.market.app.models.SortType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductQueryService productQueryService;

    @Test
    void findById_delegatesToRepository() {
        Product product = new Product(1L, "Ball", null, null, BigDecimal.valueOf(100));
        when(productRepository.findById(1L)).thenReturn(Mono.just(product));

        StepVerifier.create(productQueryService.findById(1L))
                .assertNext(result -> assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void search_noSearch_usesFindAllAndCount() {
        List<Product> products = List.of(new Product(1L, "A", null, null, BigDecimal.ONE));
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.fromIterable(products));
        when(productRepository.count()).thenReturn(Mono.just(1L));

        StepVerifier.create(productQueryService.search(null, SortType.NO, 1, 5))
                .assertNext(result -> {
                    assertThat(result.getProducts()).hasSize(1);
                    assertThat(result.getTotal()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    void search_withSearch_filtersByTitleAndDescription() {
        when(productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"), any(Pageable.class)))
                .thenReturn(Flux.fromIterable(List.of(new Product(1L, "Ball", null, null, BigDecimal.ONE))));
        when(productRepository.countByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"))).thenReturn(Mono.just(1L));

        StepVerifier.create(productQueryService.search("ball", SortType.NO, 1, 5))
                .assertNext(result -> assertThat(result.getProducts()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void search_alphaSort_passesTitleSort() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productQueryService.search(null, SortType.ALPHA, 1, 5))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("title")).isNotNull();
    }

    @Test
    void search_priceSort_passesPriceSort() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productQueryService.search(null, SortType.PRICE, 1, 5))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("price")).isNotNull();
    }

    @Test
    void search_pagination_passesCorrectPageable() {
        when(productRepository.findAllBy(any(Pageable.class))).thenReturn(Flux.empty());
        when(productRepository.count()).thenReturn(Mono.just(0L));

        StepVerifier.create(productQueryService.search(null, SortType.NO, 2, 2))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(productRepository).findAllBy(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(2);
    }
}
