package com.namatdang.namatdang.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImageVariantContractTests {

    @Test
    void exposesStableVariantNamesAndDimensions() {
        assertThat(ImageVariant.THUMBNAIL.requestValue()).isEqualTo("thumbnail");
        assertThat(ImageVariant.THUMBNAIL.targetWidth()).isEqualTo(320);
        assertThat(ImageVariant.THUMBNAIL.targetHeight()).isEqualTo(320);
        assertThat(ImageVariant.CARD.fileName()).isEqualTo("card.webp");
        assertThat(ImageVariant.CARD.targetWidth()).isEqualTo(768);
        assertThat(ImageVariant.CARD.targetHeight()).isEqualTo(432);
        assertThat(ImageVariant.DETAIL.targetWidth()).isEqualTo(1_600);
        assertThat(ImageVariant.DETAIL.targetHeight()).isEqualTo(1_200);
        assertThat(ImageVariant.fromRequestValue("detail")).isEqualTo(ImageVariant.DETAIL);

        assertThatThrownBy(() -> ImageVariant.fromRequestValue("original"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
