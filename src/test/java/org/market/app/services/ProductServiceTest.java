package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.market.app.dto.ItemDto;
import org.market.app.dto.ProductsPage;
import org.market.app.models.CartItem;
import org.market.app.models.Product;
import org.market.app.models.SortType;
import org.market.app.repositories.CartItemRepository;
import org.market.app.repositories.ProductRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
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
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", 100L);
        CartItem cartItem = new CartItem(1L, product, 3);

        when(productRepository.getProductById(1L)).thenReturn(product);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.of(cartItem));

        ItemDto result = productService.getProductById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Ball");
        assertThat(result.getCount()).isEqualTo(3);
    }

    @Test
    void getProductById_notInCart_returnsZeroCount() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", 100L);

        when(productRepository.getProductById(1L)).thenReturn(product);
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.empty());

        ItemDto result = productService.getProductById(1L);

        assertThat(result.getCount()).isEqualTo(0);
    }

    @Test
    void getProducts_noSearch_returnsAllProducts() {
        List<Product> products = new ArrayList<>(List.of(
                new Product(1L, "Apple", null, null, 50L),
                new Product(2L, "Banana", null, null, 30L)
        ));
        when(productRepository.findAll()).thenReturn(products);
        when(cartItemRepository.findByProductId(anyLong())).thenReturn(Optional.empty());

        ProductsPage result = productService.getProducts(null, SortType.NO, 1, 5);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst()).hasSize(3); // 2 products + 1 stub
        assertThat(result.getSearch()).isNull();
    }

    @Test
    void getProducts_withSearch_filtersProducts() {
        List<Product> filtered = List.of(new Product(1L, "Ball", null, null, 100L));
        when(productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase("ball", "ball"))
                .thenReturn(filtered);
        when(cartItemRepository.findByProductId(anyLong())).thenReturn(Optional.empty());

        ProductsPage result = productService.getProducts("ball", SortType.NO, 1, 5);

        assertThat(result.getSearch()).isEqualTo("ball");
        verify(productRepository).findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(eq("ball"), eq("ball"));
    }

    @Test
    void getProducts_alphaSort_sortsByTitle() {
        List<Product> products = new ArrayList<>(List.of(
                new Product(1L, "Carrot", null, null, 20L),
                new Product(2L, "Apple", null, null, 30L),
                new Product(3L, "Banana", null, null, 25L)
        ));
        when(productRepository.findAll()).thenReturn(products);
        when(cartItemRepository.findByProductId(anyLong())).thenReturn(Optional.empty());

        ProductsPage result = productService.getProducts(null, SortType.ALPHA, 1, 5);

        List<ItemDto> row = result.getItems().getFirst();
        assertThat(row.get(0).getTitle()).isEqualTo("Apple");
        assertThat(row.get(1).getTitle()).isEqualTo("Banana");
        assertThat(row.get(2).getTitle()).isEqualTo("Carrot");
    }

    @Test
    void getProducts_priceSort_sortsByPrice() {
        List<Product> products = new ArrayList<>(List.of(
                new Product(1L, "Carrot", null, null, 20L),
                new Product(2L, "Apple", null, null, 50L),
                new Product(3L, "Banana", null, null, 30L)
        ));
        when(productRepository.findAll()).thenReturn(products);
        when(cartItemRepository.findByProductId(anyLong())).thenReturn(Optional.empty());

        ProductsPage result = productService.getProducts(null, SortType.PRICE, 1, 5);

        List<ItemDto> row = result.getItems().getFirst();
        assertThat(row.get(0).getPrice()).isEqualTo(20L);
        assertThat(row.get(1).getPrice()).isEqualTo(30L);
        assertThat(row.get(2).getPrice()).isEqualTo(50L);
    }

    @Test
    void getProducts_pagination_secondPage_returnsCorrectItems() {
        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            products.add(new Product((long) i, "Product " + i, null, null, (long) i * 10));
        }
        when(productRepository.findAll()).thenReturn(products);
        when(cartItemRepository.findByProductId(anyLong())).thenReturn(Optional.empty());

        ProductsPage result = productService.getProducts(null, SortType.NO, 2, 3);

        assertThat(result.getPaging().getPageNumber()).isEqualTo(2);
        assertThat(result.getPaging().isHasPrevious()).isTrue();
        assertThat(result.getPaging().isHasNext()).isTrue();
        // страница 2, размер 3 → товары 4, 5, 6 → одна строка из 3
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getFirst().getTitle()).isEqualTo("Product 4");
    }

    @Test
    void getProducts_splitIntoRows_padsLastRowWithStubs() {
        List<Product> products = new ArrayList<>(List.of(
                new Product(1L, "AAA", null, null, 10L),
                new Product(2L, "BBB", null, null, 20L)
        ));
        when(productRepository.findAll()).thenReturn(products);
        when(cartItemRepository.findByProductId(anyLong())).thenReturn(Optional.empty());

        ProductsPage result = productService.getProducts(null, SortType.NO, 1, 5);

        List<ItemDto> row = result.getItems().getFirst();
        assertThat(row).hasSize(3);
        assertThat(row.get(2).getId()).isEqualTo(-1L);
    }
}
