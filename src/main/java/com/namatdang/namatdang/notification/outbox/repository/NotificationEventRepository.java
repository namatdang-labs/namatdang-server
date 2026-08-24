package com.namatdang.namatdang.notification.outbox.repository;

import com.namatdang.namatdang.notification.outbox.entity.NotificationEvent;
import com.namatdang.namatdang.notification.outbox.entity.NotificationEventStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    boolean existsBySourceRequestKey(String sourceRequestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM NotificationEvent event
            WHERE event.id = :eventId
            """)
    Optional<NotificationEvent> findByIdForUpdate(@Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM NotificationEvent event
            WHERE (
                event.status = :pendingStatus
                AND (event.nextRetryAt IS NULL OR event.nextRetryAt <= :now)
            ) OR (
                event.status = :publishingStatus
                AND event.publishingStartedAt <= :staleBefore
            )
            ORDER BY event.id ASC
            """)
    List<NotificationEvent> findPublishableEventsForUpdate(
            @Param("pendingStatus") NotificationEventStatus pendingStatus,
            @Param("publishingStatus") NotificationEventStatus publishingStatus,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );
}
