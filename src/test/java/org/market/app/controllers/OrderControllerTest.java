package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.dto.OrderDto;
import org.market.app.exceptions.OrderNotFoundException;
import org.market.app.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void getOrders_returns200WithOrdersList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of());

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attributeExists("orders"));
    }

    @Test
    void getOrderById_returns200WithNewOrderFalseByDefault() throws Exception {
        OrderDto order = OrderDto.builder().id(1L).totalSum(BigDecimal.valueOf(100)).items(List.of()).build();
        when(orderService.getOrderById(1L)).thenReturn(order);

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("order"))
                .andExpect(model().attribute("order", order))
                .andExpect(model().attribute("newOrder", false));
    }

    @Test
    void getOrderById_withNewOrderTrue_modelHasTrue() throws Exception {
        OrderDto order = OrderDto.builder().id(1L).totalSum(BigDecimal.valueOf(100)).items(List.of()).build();
        when(orderService.getOrderById(1L)).thenReturn(order);

        mockMvc.perform(get("/orders/1").param("newOrder", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("newOrder", true));
    }

    @Test
    void getOrderById_notFound_rendersNotFoundView() throws Exception {
        when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException());

        mockMvc.perform(get("/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("not_found"));
    }
}
