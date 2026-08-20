package com.namatdang.namatdang.notification.fixture;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.push.PushTarget;
import java.time.LocalDateTime;

public final class NotificationScenarioFixture {

    public static final Long EVENT_ID = 9_001L;
    public static final Long DEAL_ID = 1_001L;
    public static final Long STORE_ID = 2_001L;
    public static final Long FAVORITE_USER_A = 3_001L;
    public static final Long FAVORITE_USER_B = 3_002L;
    public static final Long UNRELATED_USER = 3_003L;
    public static final Long REGISTRATION_A1 = 4_001L;
    public static final Long REGISTRATION_A2 = 4_002L;
    public static final Long REGISTRATION_B1 = 4_003L;
    public static final Long REGISTRATION_UNRELATED = 4_004L;

    private NotificationScenarioFixture() {
    }

    public static NotificationEventMessage dealCreatedMessage() {
        return NotificationEventMessage.create(
                EVENT_ID,
                NotificationEventType.DEAL_CREATED,
                DEAL_ID,
                null,
                STORE_ID,
                LocalDateTime.of(2026, 8, 16, 12, 0)
        );
    }

    public static PushTarget target(Long registrationId, Long userId, String token) {
        return new PushTarget(registrationId, userId, token);
    }
}
