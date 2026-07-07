package org.market.app.integration;

import org.junit.jupiter.api.Test;
import org.market.app.infra.TestPostgresContainer;
import org.market.app.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class FullUserFlowIntegrationTest extends TestPostgresContainer {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    /**
     * 1. Открываем каталог
     * 2. Добавляем товар в корзину
     * 3. Открываем корзину
     * 4. Оформляем заказ
     * 5. Проверяем страницу заказа
     * 6. Проверяем, что корзина пуста
     */
    @Test
    void fullUserFlow_buyProduct_shouldCreateOrderAndClearCart() throws Exception {

        Long productId = productRepository.findAll().get(0).getId();

        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/items")
                        .param("id", productId.toString())
                        .param("action", "PLUS"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("items", hasSize(greaterThan(0))));

        String orderUrl = mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*?newOrder=true"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        assert orderUrl != null;
        mockMvc.perform(get(orderUrl))
                .andExpect(status().isOk())
                .andExpect(view().name("order"));

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("items", hasSize(0)));
    }
}