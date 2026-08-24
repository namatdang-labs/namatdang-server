package com.namatdang.namatdang.media;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImageController {

    private final ImageQueryService imageQueryService;

    @GetMapping("/api/v1/stores/{storeId}/image")
    public ResponseEntity<byte[]> getStoreImage(@PathVariable Long storeId,
                                                @RequestParam(defaultValue = "detail") String variant,
                                                @RequestParam(required = false) String v) {
        return imageResponse(imageQueryService.getStoreImage(storeId, parseVariant(variant), v), v != null);
    }

    @GetMapping("/api/v1/deals/{dealId}/image")
    public ResponseEntity<byte[]> getDealImage(@PathVariable Long dealId,
                                               @RequestParam(defaultValue = "detail") String variant,
                                               @RequestParam(required = false) String v) {
        return imageResponse(imageQueryService.getDealImage(dealId, parseVariant(variant), v), v != null);
    }

    private ResponseEntity<byte[]> imageResponse(ImageContent image, boolean immutableVersion) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length)
                .eTag('"' + image.etag() + '"')
                .header("X-Content-Type-Options", "nosniff");

        if (immutableVersion) {
            response.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        } else {
            response.header(HttpHeaders.CACHE_CONTROL, "no-cache, max-age=0, must-revalidate");
        }

        return response.body(image.bytes());
    }

    private ImageVariant parseVariant(String value) {
        try {
            return ImageVariant.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE, exception);
        }
    }
}
