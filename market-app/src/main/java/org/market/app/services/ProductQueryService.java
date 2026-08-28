package org.market.app.services;

import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Чистый доступ к данным товаров: только БД, без кеша и без преобразования в DTO.
 */
@Service
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Mono<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Mono<ProductSearchResult> search(String search, SortType sort, int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize, toSort(sort));

        Flux<Product> productsFlux;
        Mono<Long> countMono;
        if (search != null && !search.isBlank()) {
            productsFlux = productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                    search, search, pageable);
            countMono = productRepository.countByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                    search, search);
        } else {
            productsFlux = productRepository.findAllBy(pageable);
            countMono = productRepository.count();
        }

        return productsFlux.collectList()
                .zipWith(countMono)
                .map(tuple -> new ProductSearchResult(tuple.getT1(), tuple.getT2()));
    }

    private Sort toSort(SortType sortType) {
        return switch (sortType) {
            case ALPHA -> Sort.by("title");
            case PRICE -> Sort.by("price");
            case NO -> Sort.unsorted();
        };
    }
}
