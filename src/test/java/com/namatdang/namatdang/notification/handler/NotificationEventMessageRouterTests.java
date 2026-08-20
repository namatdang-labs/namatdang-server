package com.namatdang.namatdang.notification.handler;

import static com.namatdang.namatdang.notification.fixture.NotificationScenarioFixture.dealCreatedMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.namatdang.namatdang.notification.event.NotificationEventMessage;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationEventMessageRouterTests {

    private final DealCreatedNotificationHandler dealHandler = mock(DealCreatedNotificationHandler.class);
    private final ReservationNotificationHandler reservationHandler = mock(ReservationNotificationHandler.class);
    private final NotificationEventMessageRouter router =
            new NotificationEventMessageRouter(dealHandler, reservationHandler);

    @Test
    void routeDealEventToDealHandler() {
        NotificationEventMessage message = dealCreatedMessage();

        router.handle(message);

        verify(dealHandler).handle(message);
        verifyNoInteractions(reservationHandler);
    }

    @Test
    void routeReservationEventToReservationHandler() {
        NotificationEventMessage message = NotificationEventMessage.create(
                21_001L,
                NotificationEventType.RESERVATION_CONFIRMED,
                null,
                22_001L,
                23_001L,
                LocalDateTime.of(2026, 8, 20, 17, 0)
        );

        router.handle(message);

        verify(reservationHandler).handle(message);
        verifyNoInteractions(dealHandler);
    }
}
