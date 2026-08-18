package com.namatdang.namatdang.notification.push;

public record PushTarget(
        Long registrationId,
        Long userId,
        String registrationToken
) {

    public PushTarget {
        requirePositive(registrationId, "registrationId");
        requirePositive(userId, "userId");
        if (registrationToken == null || registrationToken.isBlank()) {
            throw new IllegalArgumentException("registrationToken은 필수입니다.");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }
}
