package com.namatdang.namatdang.media.processing;

import com.namatdang.namatdang.media.ImageVariant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ImageVariantSet {

    private final Map<ImageVariant, ProcessedImage> variants;

    public ImageVariantSet(Map<ImageVariant, ProcessedImage> variants) {
        Objects.requireNonNull(variants, "variants");
        EnumMap<ImageVariant, ProcessedImage> copy = new EnumMap<>(ImageVariant.class);
        copy.putAll(variants);
        if (copy.size() != ImageVariant.values().length) {
            throw new IllegalArgumentException("Every image variant must be present");
        }
        for (ImageVariant variant : ImageVariant.values()) {
            Objects.requireNonNull(copy.get(variant), "Missing image variant: " + variant);
        }
        this.variants = Collections.unmodifiableMap(copy);
    }

    public ProcessedImage get(ImageVariant variant) {
        return variants.get(Objects.requireNonNull(variant, "variant"));
    }

    public Map<ImageVariant, ProcessedImage> variants() {
        return variants;
    }

}
