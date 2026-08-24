package com.namatdang.namatdang.reservation.adapter;

import com.namatdang.namatdang.notification.recipient.ReservationNotificationRecipients;
import com.namatdang.namatdang.notification.recipient.ReservationRecipientReader;
import com.namatdang.namatdang.reservation.repository.ReservationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReservationNotificationRecipientAdapter implements ReservationRecipientReader {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationNotificationRecipients> findByReservationId(Long reservationId) {
        return reservationRepository.findNotificationTargetById(reservationId)
                .map(reservation -> new ReservationNotificationRecipients(
                        reservation.getConsumer().getId(),
                        reservation.getDeal().getStore().getOwner().getId(),
                        reservation.getDeal().getStore().getId()
                ));
    }
}
