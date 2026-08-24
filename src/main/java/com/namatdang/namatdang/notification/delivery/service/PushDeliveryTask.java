package com.namatdang.namatdang.notification.delivery.service;

import com.namatdang.namatdang.notification.push.PushMessage;

public record PushDeliveryTask(
        Long deliveryId,
        Long fcmRegistrationId,
        PushMessage message
) {
}
