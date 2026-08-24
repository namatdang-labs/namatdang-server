package com.namatdang.namatdang.media;

public record ImageContent(byte[] bytes, String contentType, String etag) {
}
