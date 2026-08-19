package com.namatdang.namatdang.notification.repository;

import com.namatdang.namatdang.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByEventId(Long eventId);

    Slice<Notification> findByRecipientUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
            Long recipientUserId,
            LocalDateTime createdAt,
            Pageable pageable
    );

    Slice<Notification> findByRecipientUserIdAndCreatedAtGreaterThanEqualAndIdLessThanOrderByIdDesc(
            Long recipientUserId,
            LocalDateTime createdAt,
            Long id,
            Pageable pageable
    );

    long countByRecipientUserIdAndReadFalseAndCreatedAtGreaterThanEqual(
            Long recipientUserId,
            LocalDateTime createdAt
    );

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    void deleteAllCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
