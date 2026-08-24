package com.namatdang.namatdang.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
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

    @Test
    void createsVariantKeysOnlyBelowAValidatedGroupPrefix() {
        UUID imageId = UUID.fromString("f6a00427-ded4-4e0e-a0ce-6279d7f782bb");
        String group = ImageVariantKeys.groupKey(
                ImageKind.DEAL,
                42L,
                imageId);
        String entityIndependentGroup = ImageVariantKeys.groupKey(ImageKind.DEAL, imageId);

        assertThat(group).isEqualTo("images/deals/42/f6a00427-ded4-4e0e-a0ce-6279d7f782bb");
        assertThat(entityIndependentGroup)
                .isEqualTo("images/deals/f6a00427-ded4-4e0e-a0ce-6279d7f782bb");
        assertThat(ImageVariantKeys.key(group, ImageVariant.CARD))
                .isEqualTo(group + "/card.webp");
        assertThat(ImageVariantKeys.key(entityIndependentGroup, ImageVariant.CARD))
                .isEqualTo(entityIndependentGroup + "/card.webp");
        assertThatThrownBy(() -> ImageVariantKeys.key("images/deals/42/../other", ImageVariant.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

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
