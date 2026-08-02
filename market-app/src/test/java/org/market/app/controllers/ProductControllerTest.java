package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.ItemDto;
import org.market.app.dto.Paging;
import org.market.app.dto.ProductsPage;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.models.Action;
import org.market.app.models.SortType;
import org.market.app.services.CartService;
import org.market.app.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest({ProductController.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProductService productService;

    @MockBean
    private CartService cartService;

    private Mono<ProductsPage> emptyPage() {
        Paging paging = new Paging();
        paging.setPageNumber(1);
        paging.setPageSize(5);
        return Mono.just(ProductsPage.builder()
                .items(List.of())
                .paging(paging)
                .sort(SortType.NO)
                .build());
    }

    @Test
    void getProducts_returns200() {
        when(productService.getProducts(any(), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/items")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getProducts_rootPath_returns200() {
        when(productService.getProducts(any(), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getProducts_withSearch_passesSearchToService() {
        when(productService.getProducts(eq("ball"), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/items?search=ball")
                .exchange()
                .expectStatus().isOk();

        verify(productService).getProducts(eq("ball"), any(), any(), any());
    }

    @Test
    void getProducts_withSortAlpha_passesSortToService() {
        when(productService.getProducts(any(), eq(SortType.ALPHA), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/items?sort=ALPHA")
                .exchange()
                .expectStatus().isOk();

        verify(productService).getProducts(any(), eq(SortType.ALPHA), any(), any());
    }

    @Test
    void getProductById_returns200WithItem() {
        ItemDto item = ItemDto.builder().id(1L).title("Ball").price(BigDecimal.valueOf(100)).count(0).build();
        when(productService.getProductById(1L)).thenReturn(Mono.just(item));

        webTestClient.get().uri("/items/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Ball"));
    }

    @Test
    void postItems_plus_redirectsBackToItems() {
        when(cartService.changeCount(1L, Action.PLUS)).thenReturn(Mono.empty());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "1");
        form.add("action", Action.PLUS.name());
        form.add("sort", SortType.NO.name());
        form.add("pageNumber", "1");
        form.add("pageSize", "5");

        webTestClient.post().uri("/items")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/items"));

        verify(cartService).changeCount(1L, Action.PLUS);
    }

    @Test
    void postItemById_plus_redirectsToItem() {
        when(cartService.changeCount(1L, Action.PLUS)).thenReturn(Mono.empty());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action", Action.PLUS.name());

        webTestClient.post().uri("/items/1")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/items/1"));

        verify(cartService).changeCount(1L, Action.PLUS);
    }

    @Test
    void getProductById_notFound_returns404() {
        when(productService.getProductById(99L)).thenReturn(Mono.error(new ProductNotFoundException()));

        webTestClient.get().uri("/items/99")
                .exchange()
                .expectStatus().isNotFound();
    }
}