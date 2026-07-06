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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CartService cartService;

    private ProductsPage emptyPage() {
        Paging paging = new Paging();
        paging.setPageNumber(1);
        paging.setPageSize(5);
        return ProductsPage.builder()
                .items(List.of())
                .paging(paging)
                .sort(SortType.NO)
                .build();
    }

    @Test
    void getProducts_returns200WithModel() throws Exception {
        when(productService.getProducts(any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attributeExists("items", "sort", "paging"));
    }

    @Test
    void getProducts_rootPath_returns200() throws Exception {
        when(productService.getProducts(any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("items"));
    }

    @Test
    void getProducts_withSearch_passesSearchToService() throws Exception {
        when(productService.getProducts(eq("ball"), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/items").param("search", "ball"))
                .andExpect(status().isOk());

        verify(productService).getProducts(eq("ball"), any(), any(), any());
    }

    @Test
    void getProducts_withSortAlpha_passesSortToService() throws Exception {
        when(productService.getProducts(any(), eq(SortType.ALPHA), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/items").param("sort", "ALPHA"))
                .andExpect(status().isOk());

        verify(productService).getProducts(any(), eq(SortType.ALPHA), any(), any());
    }

    @Test
    void getProductById_returns200WithItem() throws Exception {
        ItemDto item = ItemDto.builder().id(1L).title("Ball").price(BigDecimal.valueOf(100)).count(0).build();
        when(productService.getProductById(1L)).thenReturn(item);

        mockMvc.perform(get("/items/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", item));
    }

    @Test
    void postItems_plus_redirectsBackToItems() throws Exception {
        mockMvc.perform(post("/items")
                        .param("id", "1")
                        .param("action", "PLUS")
                        .param("sort", "NO")
                        .param("pageNumber", "1")
                        .param("pageSize", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/items*"));

        verify(cartService).changeCount(1L, Action.PLUS);
    }

    @Test
    void postItemById_plus_redirectsToItem() throws Exception {
        mockMvc.perform(post("/items/1").param("action", "PLUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/items/1"));

        verify(cartService).changeCount(1L, Action.PLUS);
    }

    @Test
    void getProductById_notFound_rendersNotFoundView() throws Exception {
        when(productService.getProductById(99L)).thenThrow(new ProductNotFoundException());

        mockMvc.perform(get("/items/99"))
                .andExpect(status().isOk())
                .andExpect(view().name("not_found"));
    }
}
