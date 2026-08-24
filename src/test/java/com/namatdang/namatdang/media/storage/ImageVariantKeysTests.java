package com.namatdang.namatdang.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.media.ImageKind;
import com.namatdang.namatdang.media.ImageVariant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImageVariantKeysTests {

    @Test
    void createsVariantKeysOnlyBelowAValidatedGroupPrefix() {
        UUID imageId = UUID.fromString("f6a00427-ded4-4e0e-a0ce-6279d7f782bb");
        String group = ImageVariantKeys.groupKey(ImageKind.DEAL, 42L, imageId);
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
}
