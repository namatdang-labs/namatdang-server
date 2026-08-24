package com.namatdang.namatdang.media.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.media.ImageVariant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageVariantSetTests {

    @Test
    void requiresAllThreeVariantsAndDefensivelyCopiesBytes() {
        byte[] source = new byte[]{1, 2, 3};
        ProcessedImage image = new ProcessedImage(source, "image/webp", 10, 5);
        source[0] = 9;

        EnumMap<ImageVariant, ProcessedImage> variants = new EnumMap<>(ImageVariant.class);
        variants.put(ImageVariant.THUMBNAIL, image);
        variants.put(ImageVariant.CARD, image);
        variants.put(ImageVariant.DETAIL, image);
        ImageVariantSet set = new ImageVariantSet(variants);

        byte[] returned = set.get(ImageVariant.CARD).bytes();
        returned[1] = 9;
        assertThat(set.get(ImageVariant.CARD).bytes()).containsExactly(1, 2, 3);
        assertThat(set.variants()).containsOnlyKeys(ImageVariant.values());
        assertThatThrownBy(() -> new ImageVariantSet(Map.of(ImageVariant.CARD, image)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
