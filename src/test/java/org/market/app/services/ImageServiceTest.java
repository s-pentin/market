package org.market.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private ImageService imageService;

    @Test
    void findImage_fileExists_returnsOptionalWithResource() {
        Resource resource = mock(Resource.class);
        when(resource.exists()).thenReturn(true);
        when(resourceLoader.getResource("classpath:static/images/football.png")).thenReturn(resource);

        Optional<Resource> result = imageService.findImage("football.png");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(resource);
    }

    @Test
    void findImage_fileNotExists_returnsEmpty() {
        Resource resource = mock(Resource.class);
        when(resource.exists()).thenReturn(false);
        when(resourceLoader.getResource("classpath:static/images/missing.png")).thenReturn(resource);

        Optional<Resource> result = imageService.findImage("missing.png");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveMediaType_png_returnsImagePng() {
        assertThat(imageService.resolveMediaType("photo.png")).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void resolveMediaType_jpg_returnsImageJpeg() {
        assertThat(imageService.resolveMediaType("photo.jpg")).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void resolveMediaType_jpeg_returnsImageJpeg() {
        assertThat(imageService.resolveMediaType("photo.jpeg")).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void resolveMediaType_gif_returnsImageGif() {
        assertThat(imageService.resolveMediaType("photo.gif")).isEqualTo(MediaType.IMAGE_GIF);
    }

    @Test
    void resolveMediaType_unknownExtension_returnsOctetStream() {
        assertThat(imageService.resolveMediaType("file.bmp")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void resolveMediaType_noExtension_returnsOctetStream() {
        assertThat(imageService.resolveMediaType("filename")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void resolveMediaType_nullOrEmpty_returnsOctetStream() {
        assertThat(imageService.resolveMediaType(null)).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(imageService.resolveMediaType("")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void isValidFileName_shouldAcceptValidNames() {
        assertThat(imageService.isValidFileName("ball.png")).isTrue();
        assertThat(imageService.isValidFileName("my_image_123.jpg")).isTrue();
        assertThat(imageService.isValidFileName("photo-1.jpeg")).isTrue();
        assertThat(imageService.isValidFileName("file_with_underscore.gif")).isTrue();
    }

    @Test
    void isValidFileName_shouldRejectInvalidNames() {
        assertThat(imageService.isValidFileName(null)).isFalse();
        assertThat(imageService.isValidFileName("")).isFalse();
        assertThat(imageService.isValidFileName("   ")).isFalse();
        assertThat(imageService.isValidFileName("../hack.png")).isFalse();
        assertThat(imageService.isValidFileName("/etc/passwd")).isFalse();
        assertThat(imageService.isValidFileName("file@name.png")).isFalse();
        assertThat(imageService.isValidFileName("file#name.png")).isFalse();
        assertThat(imageService.isValidFileName(".hidden.png")).isFalse();
    }

    @Test
    void findImage_shouldReturnEmptyForInvalidFilename() {
        assertThat(imageService.findImage("../malicious.png")).isEmpty();
        assertThat(imageService.findImage(null)).isEmpty();
    }
}
