package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.exceptions.EmptyCartException;
import org.market.app.usecases.PlaceOrderUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceOrderUseCase placeOrderUseCase;

    @Test
    void buy_redirectsToOrderPageWithNewOrderTrue() throws Exception {
        when(placeOrderUseCase.execute()).thenReturn(42L);

        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/42?newOrder=true"));
    }

    @Test
    void buy_emptyCart_redirectsToCartWithFlashError() throws Exception {
        when(placeOrderUseCase.execute()).thenThrow(new EmptyCartException());

        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart/items"))
                .andExpect(flash().attributeExists("error"));
    }
}
