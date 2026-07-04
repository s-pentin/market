package org.market.app.services;

import org.market.app.dto.ItemDto;
import org.market.app.dto.Paging;
import org.market.app.dto.ProductsPage;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductService {

    private static final int ROW_SIZE = 3;

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;

    public ProductService(ProductRepository productRepository, CartItemRepository cartItemRepository) {
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public ItemDto getProductById(Long id) {
        Product product = productRepository.getProductById(id);
        int count = cartItemRepository.findByProductId(product.getId())
                .map(CartItem::getCount)
                .orElse(0);
        return ItemDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .imgPath(product.getImgPath())
                .price(product.getPrice())
                .count(count)
                .build();
    }

    public ProductsPage getProducts(String search, SortType sort, Integer pageNumber, Integer pageSize) {
        List<Product> products;
        if (search != null && !search.isBlank()) {
            products = productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(search, search);
        } else {
            products = productRepository.findAll();
        }

        if (!sort.equals(SortType.NO)) {
            sortProductBy(sort, products);
        }

        int total = products.size();
        products = getPagingProducts(products, pageNumber, pageSize);
        List<ItemDto> itemDtos = products.stream()
                .map(this::toItemDto)
                .toList();

        return ProductsPage.builder()
                .items(splitIntoRows(itemDtos))
                .paging(buildPaging(total, pageNumber, pageSize))
                .search(search)
                .sort(sort)
                .build();
    }

    private void sortProductBy(SortType sort, List<Product> products) {
        if (sort.equals(SortType.ALPHA)) {
            products.sort(Comparator.comparing(Product::getTitle));
        } else if (sort.equals(SortType.PRICE)) {
            products.sort(Comparator.comparing(Product::getPrice));
        }
    }

    private Paging buildPaging(int total, int pageNumber, int pageSize) {
        Paging paging = new Paging();
        paging.setPageNumber(pageNumber);
        paging.setPageSize(pageSize);
        paging.setHasPrevious(pageNumber > 1);
        paging.setHasNext((long) pageNumber * pageSize < total);

        return paging;
    }

    private List<Product> getPagingProducts(List<Product> products, Integer pageNumber, Integer pageSize) {
        int from = (pageNumber - 1) * pageSize;
        int to = Math.min(from + pageSize, products.size());
        if (from >= products.size()) {
            return List.of();
        }
        return products.subList(from, to);
    }

    private ItemDto toItemDto(Product product) {
        int count = cartItemRepository.findByProductId(product.getId())
                .map(CartItem::getCount)
                .orElse(0);

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
