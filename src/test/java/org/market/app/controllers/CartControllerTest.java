package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.ProductsInCart;
import org.market.app.models.Action;
import org.market.app.services.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    private ProductsInCart emptyCart() {
        return ProductsInCart.builder().items(List.of()).totalCost(BigDecimal.valueOf(0)).build();
    }

    @Test
    void getCart_returns200WithItemsAndTotal() throws Exception {
        when(cartService.getAllProductsInCart()).thenReturn(emptyCart());

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attributeExists("items", "total"));
    }

    @Test
    void getCart_modelHasCorrectTotal() throws Exception {
        ProductsInCart cart = ProductsInCart.builder().items(List.of()).totalCost(BigDecimal.valueOf(500)).build();
        when(cartService.getAllProductsInCart()).thenReturn(cart);

        mockMvc.perform(get("/cart/items"))
                .andExpect(model().attribute("total", BigDecimal.valueOf(500)));
    }

    @Test
    void updateCart_plus_redirectsToCart() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .param("id", "1")
                        .param("action", "PLUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart/items"));

        verify(cartService).changeCount(1L, Action.PLUS);
    }

    @Test
    void updateCart_delete_redirectsToCart() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .param("id", "2")
                        .param("action", "DELETE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart/items"));

        verify(cartService).changeCount(2L, Action.DELETE);
    }
}
