package org.market.app.services;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class ImageService {
    private static final Map<String, MediaType> MEDIA_TYPES = Map.of(
            "jpg",  MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png",  MediaType.IMAGE_PNG,
            "gif",  MediaType.IMAGE_GIF
    );

    private final ResourceLoader resourceLoader;

    public ImageService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Optional<Resource> findImage(String filename) {
        Resource resource = resourceLoader.getResource("classpath:static/images/" + filename);
        return resource.exists() ? Optional.of(resource) : Optional.empty();
    }

    public MediaType resolveMediaType(String filename) {
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        return MEDIA_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}