package com.namatdang.namatdang.media;

import java.util.Objects;

public record ProcessedImage(byte[] bytes, String contentType, int width, int height) {

    public ProcessedImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(contentType, "contentType");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Processed image bytes must not be empty");
        }
        if (!"image/webp".equals(contentType)) {
            throw new IllegalArgumentException("Processed images must use image/webp");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Processed image dimensions must be positive");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
