package com.namatdang.namatdang.notification.fixture;

import com.namatdang.namatdang.notification.recipient.ReservationNotificationRecipients;
import com.namatdang.namatdang.notification.recipient.ReservationRecipientReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakeReservationRecipientReader implements ReservationRecipientReader {

    private final Map<Long, ReservationNotificationRecipients> recipientsByReservationId = new HashMap<>();

    @Override
    public Optional<ReservationNotificationRecipients> findByReservationId(Long reservationId) {
        return Optional.ofNullable(recipientsByReservationId.get(reservationId));
    }

    public void setRecipients(
            Long reservationId,
            ReservationNotificationRecipients recipients
    ) {
        recipientsByReservationId.put(reservationId, recipients);
    }

    public void reset() {
        recipientsByReservationId.clear();
    }
}
