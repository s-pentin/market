package org.market.app.controllers;

import org.junit.jupiter.api.Test;
import org.market.app.services.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    @Test
    void getImage_notFound_returns404() throws Exception {
        when(imageService.findImage("nonexistent.jpg")).thenReturn(Optional.empty());

        mockMvc.perform(get("/images/nonexistent.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getImage_invalidFilename_returns404() throws Exception {
        mockMvc.perform(get("/images/../hack.png"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/images/null"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/images/file@name.png"))
                .andExpect(status().isNotFound());
    }
}
