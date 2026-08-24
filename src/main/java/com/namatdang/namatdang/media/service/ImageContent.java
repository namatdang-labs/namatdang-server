package com.namatdang.namatdang.media.service;

public record ImageContent(byte[] bytes, String contentType, String etag) {
}
