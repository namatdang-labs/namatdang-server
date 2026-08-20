package com.namatdang.namatdang.notification.recipient;

public record ReservationNotificationRecipients(
        Long consumerUserId,
        Long ownerUserId,
        Long storeId
) {

    public ReservationNotificationRecipients {
        requirePositive(consumerUserId, "consumerUserId");
        requirePositive(ownerUserId, "ownerUserId");
        requirePositive(storeId, "storeId");
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "는 양수여야 합니다.");
        }
    }
}
