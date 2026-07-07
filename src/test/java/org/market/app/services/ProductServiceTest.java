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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
        CartItem cartItem = new CartItem(1L, product, 3);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.of(cartItem));

        ItemDto result = productService.getProductById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Ball");
        assertThat(result.getCount()).isEqualTo(3);
    }

    @Test
    void getProductById_notInCart_returnsZeroCount() {
        Product product = new Product(1L, "Ball", "desc", "/img.jpg", BigDecimal.valueOf(100));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByProductId(1L)).thenReturn(Optional.empty());

        ItemDto result = productService.getProductById(1L);

        assertThat(result.getCount()).isEqualTo(0);
    }

    @Test
    void getProducts_noSearch_returnsAllProducts() {
        List<Product> products = List.of(
                new Product(1L, "Apple", null, null, BigDecimal.valueOf(50)),
                new Product(2L, "Banana", null, null, BigDecimal.valueOf(30))
        );
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(products));
        when(cartItemRepository.findAllByProductIdIn(anyCollection())).thenReturn(List.of());

        ProductsPage result = productService.getProducts(null, SortType.NO, 1, 5);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst()).hasSize(3); // 2 products + 1 stub
        assertThat(result.getSearch()).isNull();
    }

    @Test
    void getProducts_withSearch_filtersProducts() {
        List<Product> filtered = List.of(new Product(1L, "Ball", null, null, BigDecimal.valueOf(100)));
        when(productRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(filtered));
        when(cartItemRepository.findAllByProductIdIn(anyCollection())).thenReturn(List.of());

        ProductsPage result = productService.getProducts("ball", SortType.NO, 1, 5);

        assertThat(result.getSearch()).isEqualTo("ball");
        verify(productRepository).findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                eq("ball"), eq("ball"), any(Pageable.class));
    }

    @Test
    void getProducts_alphaSort_passesTitleSortToRepository() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.ALPHA, 1, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("title"));
    }

    @Test
    void getProducts_priceSort_passesPriceSortToRepository() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.PRICE, 1, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("price"));
    }

    @Test
    void getProducts_pagination_secondPage_passesCorrectPageable() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.NO, 2, 2);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1); // 0-based: page 2 → index 1
        assertThat(captor.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void getProducts_paging_hasNextAndPrevious() {
        List<Product> products = List.of(new Product(1L, "X", null, null, BigDecimal.valueOf(10)));
        Page<Product> page = new PageImpl<>(products, PageRequest.of(1, 5), 15);
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(cartItemRepository.findAllByProductIdIn(anyCollection())).thenReturn(List.of());

        ProductsPage result = productService.getProducts(null, SortType.NO, 2, 5);

        assertThat(result.getPaging().getPageNumber()).isEqualTo(2);
        assertThat(result.getPaging().isHasPrevious()).isTrue();
        assertThat(result.getPaging().isHasNext()).isTrue();
    }

    @Test
    void getProducts_splitIntoRows_padsLastRowWithStubs() {
        List<Product> products = List.of(
                new Product(1L, "AAA", null, null, BigDecimal.valueOf(10)),
                new Product(2L, "BBB", null, null, BigDecimal.valueOf(20))
        );
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(products));
        when(cartItemRepository.findAllByProductIdIn(anyCollection())).thenReturn(List.of());

        ProductsPage result = productService.getProducts(null, SortType.NO, 1, 5);

        List<ItemDto> row = result.getItems().getFirst();
        assertThat(row).hasSize(3);
        assertThat(row.get(2).getId()).isEqualTo(-1L);
    }

    @Test
    void getProducts_pageNumberZero_normalizedToOne() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.NO, 0, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0); // 0-based index of page 1
    }

    @Test
    void getProducts_pageNumberNegative_normalizedToOne() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.NO, -5, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void getProducts_invalidPageSize_normalizedToDefault() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.NO, 1, 7);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getProducts_validPageSize_usedAsIs() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.getProducts(null, SortType.NO, 1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }
}
