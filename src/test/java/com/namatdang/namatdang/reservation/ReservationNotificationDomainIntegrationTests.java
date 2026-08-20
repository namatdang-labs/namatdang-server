package com.namatdang.namatdang.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.notification.event.NotificationEventType;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEvent;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEventStatus;
import com.namatdang.namatdang.notification.outbox.repository.NotificationEventRepository;
import com.namatdang.namatdang.notification.recipient.ReservationNotificationRecipients;
import com.namatdang.namatdang.notification.recipient.ReservationRecipientReader;
import com.namatdang.namatdang.reservation.entity.Reservation;
import com.namatdang.namatdang.reservation.repository.ReservationRepository;
import com.namatdang.namatdang.store.entity.Store;
import com.namatdang.namatdang.user.entity.User;
import com.namatdang.namatdang.user.entity.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationNotificationDomainIntegrationTests extends ReservationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private ReservationRecipientReader reservationRecipientReader;

    @Test
    void reservationCreationAndCancellationRecordDistinctEventsOnce() throws Exception {
        User owner = saveUser(UserRole.OWNER);
        User consumer = saveUser(UserRole.CONSUMER);
        Store store = saveStore(owner);
        Deal deal = saveDeal(store, 5, 3);
        String createKey = uniqueValue();

        String created = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", createKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(
                                deal.getId(),
                                deal.getItems().getFirst().getId(),
                                1
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reservationId = reservationIdOf(created);

        // 동일한 생성 요청 재현은 새로운 Outbox 이벤트를 만들지 않는다.
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", createKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(
                                deal.getId(),
                                deal.getItems().getFirst().getId(),
                                1
                        )))
                .andExpect(status().isCreated());

        String cancelKey = uniqueValue();
        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", cancelKey))
                .andExpect(status().isOk());

        // 취소 응답 재현도 새로운 Outbox 이벤트를 만들지 않는다.
        mockMvc.perform(post("/api/v1/reservations/{reservationId}/cancel", reservationId)
                        .header("Authorization", bearerToken(consumer))
                        .header("Idempotency-Key", cancelKey))
                .andExpect(status().isOk());

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReservationNotificationRecipients recipients = reservationRecipientReader
                .findByReservationId(reservationId)
                .orElseThrow();
        assertThat(recipients.consumerUserId()).isEqualTo(consumer.getId());
        assertThat(recipients.ownerUserId()).isEqualTo(owner.getId());
        assertThat(recipients.storeId()).isEqualTo(store.getId());

        List<NotificationEvent> events = notificationEventRepository.findAll().stream()
                .filter(event -> Long.valueOf(reservationId).equals(event.getReservationId()))
                .toList();

        assertThat(events).hasSize(2);
        NotificationEvent confirmed = eventOfType(events, NotificationEventType.RESERVATION_CONFIRMED);
        assertThat(confirmed.getDealId()).isNull();
        assertThat(confirmed.getStoreId()).isEqualTo(store.getId());
        assertThat(confirmed.getOccurredAt()).isEqualTo(reservation.getCreatedAt());
        assertThat(confirmed.getSourceRequestKey())
                .isEqualTo("RESERVATION:%d:CONFIRMED".formatted(reservationId));
        assertThat(confirmed.getStatus()).isEqualTo(NotificationEventStatus.PENDING);

        NotificationEvent canceled = eventOfType(events, NotificationEventType.RESERVATION_CANCELED);
        assertThat(canceled.getDealId()).isNull();
        assertThat(canceled.getStoreId()).isEqualTo(store.getId());
        assertThat(canceled.getOccurredAt()).isEqualTo(reservation.getCanceledAt());
        assertThat(canceled.getSourceRequestKey())
                .isEqualTo("RESERVATION:%d:CANCELED".formatted(reservationId));
        assertThat(canceled.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
    }

    private long reservationIdOf(String responseBody) {
        Number reservationId = JsonPath.parse(responseBody).read("$.reservationId", Number.class);
        return reservationId.longValue();
    }

    private NotificationEvent eventOfType(
            List<NotificationEvent> events,
            NotificationEventType eventType
    ) {
        return events.stream()
                .filter(event -> event.getEventType() == eventType)
                .findFirst()
                .orElseThrow();
    }
}
