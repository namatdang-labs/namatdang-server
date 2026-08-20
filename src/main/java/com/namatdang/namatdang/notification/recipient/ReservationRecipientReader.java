package com.namatdang.namatdang.notification.recipient;

import java.util.Optional;

public interface ReservationRecipientReader {

    Optional<ReservationNotificationRecipients> findByReservationId(Long reservationId);
}
