package com.namatdang.namatdang.media.storage;

import com.namatdang.namatdang.media.ImageKind;
import com.namatdang.namatdang.media.ImageVariant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ImageVariantKeys {

    private static final Pattern GROUP_KEY = Pattern.compile(
            "images/(stores|deals)/(?:[1-9][0-9]*/)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private ImageVariantKeys() {
    }

    public static String newGroupKey(ImageKind kind) {
        return groupKey(kind, UUID.randomUUID());
    }

    static String groupKey(ImageKind kind, UUID imageId) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(imageId, "imageId");
        return "images/%s/%s".formatted(kind.directory(), imageId);
    }

    static String groupKey(ImageKind kind, long entityId, UUID imageId) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(imageId, "imageId");
        if (entityId <= 0) {
            throw new IllegalArgumentException("Image entity id must be positive");
        }
        return "images/%s/%d/%s".formatted(kind.directory(), entityId, imageId);
    }

    public static String key(String groupKey, ImageVariant variant) {
        Objects.requireNonNull(groupKey, "groupKey");
        Objects.requireNonNull(variant, "variant");
        if (!GROUP_KEY.matcher(groupKey).matches()) {
            throw new IllegalArgumentException("Invalid image group key");
        }
        return groupKey + "/" + variant.fileName();
    }

}
