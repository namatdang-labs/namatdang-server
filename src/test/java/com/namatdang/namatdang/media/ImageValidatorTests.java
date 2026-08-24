package com.namatdang.namatdang.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageValidatorTests {

    private final ImageValidator validator = new ImageValidator();

    @Test
    void detectsSupportedImageTypesFromFileSignature() {
        assertThat(validator.validate(file("photo.jpg", "image/jpeg",
                                           TestImages.jpeg())).contentType())
                .isEqualTo("image/jpeg");
        assertThat(validator.validate(file("photo.png", "image/png",
                                           TestImages.png())).contentType())
                .isEqualTo("image/png");
        assertThat(validator.validate(file("photo.webp", "image/webp",
                                           TestImages.webp())).contentType())
                .isEqualTo("image/webp");
    }

    @Test
    void rejectsContentThatOnlyClaimsToBeAnImage() {
        MockMultipartFile file = file("attack.svg", "image/png", "<svg/>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.INVALID_IMAGE));
    }

    @Test
    void rejectsImagesLargerThanFifteenMegabytes() {
        byte[] bytes = new byte[(int) ImageValidator.MAX_IMAGE_SIZE_BYTES + 1];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;

        assertThatThrownBy(() -> validator.validate(file("large.jpg", "image/jpeg", bytes)))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.IMAGE_TOO_LARGE));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("image", name, contentType, content);
    }
}
