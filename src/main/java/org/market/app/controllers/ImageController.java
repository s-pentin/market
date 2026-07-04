package org.market.app.controllers;

import org.market.app.services.ImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        return imageService.findImage(filename)
                .map(resource -> ResponseEntity.ok()
                        .contentType(imageService.resolveMediaType(filename))
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }
}