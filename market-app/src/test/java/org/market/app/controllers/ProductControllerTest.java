package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.ItemDto;
import org.market.app.dto.Paging;
import org.market.app.dto.ProductsPage;
import org.market.app.exceptions.ProductNotFoundException;
import org.market.app.models.Action;
import org.market.app.models.Role;
import org.market.app.models.SortType;
import org.market.app.models.User;
import org.market.app.security.AppUserDetails;
import org.market.app.security.SecurityConfig;
import org.market.app.services.CartService;
import org.market.app.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest({ProductController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProductService productService;

    @MockBean
    private CartService cartService;

    @MockBean
    private ReactiveUserDetailsService reactiveUserDetailsService;

    private UsernamePasswordAuthenticationToken auth() {
        AppUserDetails principal = new AppUserDetails(new User(1L, "customer1", "hash", Role.CUSTOMER, true));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

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
        when(productService.getProducts(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/items")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getProducts_rootPath_returns200() {
        when(productService.getProducts(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getProducts_withSearch_passesSearchToService() {
        when(productService.getProducts(eq("ball"), any(), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/items?search=ball")
                .exchange()
                .expectStatus().isOk();

        verify(productService).getProducts(eq("ball"), any(), any(), any(), any());
    }

    @Test
    void getProducts_withSortAlpha_passesSortToService() {
        when(productService.getProducts(any(), eq(SortType.ALPHA), any(), any(), any())).thenReturn(emptyPage());

        webTestClient.get().uri("/items?sort=ALPHA")
                .exchange()
                .expectStatus().isOk();

        verify(productService).getProducts(any(), eq(SortType.ALPHA), any(), any(), any());
    }

    @Test
    void getProductById_returns200WithItem() {
        ItemDto item = ItemDto.builder().id(1L).title("Ball").price(BigDecimal.valueOf(100)).count(0).build();
        when(productService.getProductById(eq(1L), any())).thenReturn(Mono.just(item));

        webTestClient.get().uri("/items/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Ball"));
    }

    @Test
    void postItems_plus_redirectsBackToItems() {
        when(cartService.changeCount(eq(1L), eq(1L), eq(Action.PLUS))).thenReturn(Mono.empty());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "1");
        form.add("action", Action.PLUS.name());
        form.add("sort", SortType.NO.name());
        form.add("pageNumber", "1");
        form.add("pageSize", "5");

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth()))
                .post().uri("/items")
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", loc -> assertThat(loc).contains("/items"));

        verify(cartService).changeCount(eq(1L), eq(1L), eq(Action.PLUS));
    }

    @Test
    void postItems_asAnonymous_redirectsToLogin() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/items")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("id=1&action=PLUS")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/login.*");
    }

    @Test
    void getProductById_notFound_returns404() {
        when(productService.getProductById(eq(99L), any())).thenReturn(Mono.error(new ProductNotFoundException()));

        webTestClient.get().uri("/items/99")
                .exchange()
                .expectStatus().isNotFound();
    }
}