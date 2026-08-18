package com.namatdang.namatdang.notification.push;

public record PushMessage(
        String title,
        String body,
        String linkUrl
) {

    public PushMessage {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 필수입니다.");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body는 필수입니다.");
        }
    }
}
