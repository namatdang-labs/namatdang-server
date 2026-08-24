package com.namatdang.namatdang.media;

import java.util.Arrays;

public enum ImageVariant {
    THUMBNAIL("thumbnail", 320, 320),
    CARD("card", 768, 432),
    DETAIL("detail", 1_600, 1_200);

    private final String requestValue;
    private final int targetWidth;
    private final int targetHeight;

    ImageVariant(String requestValue, int targetWidth, int targetHeight) {
        this.requestValue = requestValue;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
    }

    public String requestValue() {
        return requestValue;
    }

    public int targetWidth() {
        return targetWidth;
    }

    public int targetHeight() {
        return targetHeight;
    }

    public String fileName() {
        return requestValue + ".webp";
    }

    public static ImageVariant fromRequestValue(String value) {
        return Arrays.stream(values())
                .filter(variant -> variant.requestValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown image variant: " + value));
    }
}
