package com.namatdang.namatdang.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class ImageControllerTests {

    @Test
    void immutableCachingRequiresAValidatedVersionParameter() {
        ImageQueryService queryService = mock(ImageQueryService.class);
        ImageContent image = new ImageContent(TestImages.webp(), "image/webp", "abc123");
        when(queryService.getStoreImage(7L, ImageVariant.DETAIL, null)).thenReturn(image);
        when(queryService.getStoreImage(7L, ImageVariant.CARD, "abc123")).thenReturn(image);
        ImageController controller = new ImageController(queryService);

        ResponseEntity<byte[]> unversioned = controller.getStoreImage(7L, "detail", null);
        ResponseEntity<byte[]> versioned = controller.getStoreImage(7L, "card", "abc123");

        assertThat(unversioned.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-cache, max-age=0, must-revalidate");
        assertThat(versioned.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("max-age=31536000, public, immutable");
        assertThat(versioned.getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(versioned.getHeaders().getContentType()).hasToString("image/webp");
    }

    @Test
    void rejectsUnknownImageVariants() {
        ImageController controller = new ImageController(mock(ImageQueryService.class));

        assertThatThrownBy(() -> controller.getDealImage(9L, "original", null))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.INVALID_INPUT_VALUE));
    }
}
