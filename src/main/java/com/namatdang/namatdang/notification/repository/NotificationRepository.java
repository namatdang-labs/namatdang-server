package com.namatdang.namatdang.notification.repository;

import com.namatdang.namatdang.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

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
}
