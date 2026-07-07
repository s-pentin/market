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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final int ROW_SIZE = 3;
    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(2, 5, 10, 20, 50, 100);

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;

    public ProductService(ProductRepository productRepository, CartItemRepository cartItemRepository) {
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public ItemDto getProductById(Long id) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isEmpty()) {
            throw new ProductNotFoundException();
        }
        int count = cartItemRepository.findByProductId(product.get().getId())
                .map(CartItem::getCount)
                .orElse(0);
        return ItemDto.builder()
                .id(product.get().getId())
                .title(product.get().getTitle())
                .description(product.get().getDescription())
                .imgPath(product.get().getImgPath())
                .price(product.get().getPrice())
                .count(count)
                .build();
    }

    public ProductsPage getProducts(String search, SortType sort, Integer pageNumber, Integer pageSize) {
        if (pageNumber == null || pageNumber < 1){
            pageNumber = 1;
        }
        if (pageSize == null || !ALLOWED_PAGE_SIZES.contains(pageSize)) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize, toSort(sort));

        Page<Product> page;
        if (search != null && !search.isBlank()) {
            page = productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                    search, search, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }

        List<Long> productIds = page.getContent().stream()
                .map(Product::getId)
                .toList();

        Map<Long, Integer> cartCounts = cartItemRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ci -> ci.getProduct().getId(), CartItem::getCount));

        List<ItemDto> itemDtos = page.getContent().stream()
                .map(p -> toItemDto(p, cartCounts))
                .toList();

        return ProductsPage.builder()
                .items(splitIntoRows(itemDtos))
                .paging(buildPaging(page))
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

    private Paging buildPaging(Page<?> page) {
        Paging paging = new Paging();
        paging.setPageNumber(page.getNumber() + 1);
        paging.setPageSize(page.getSize());
        paging.setHasPrevious(!page.isFirst());
        paging.setHasNext(!page.isLast());
        return paging;
    }

    private ItemDto toItemDto(Product product, Map<Long, Integer> cartCounts) {
        int count = cartCounts.getOrDefault(product.getId(), 0);

        return ItemDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .imgPath(product.getImgPath())
                .price(product.getPrice())
                .count(count)
                .build();
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
