package com.namatdang.namatdang.notification.delivery.repository;

import com.namatdang.namatdang.notification.delivery.entity.PushDelivery;
import com.namatdang.namatdang.notification.delivery.entity.PushDeliveryStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {

    List<PushDelivery> findAllByNotificationIdIn(Collection<Long> notificationIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM PushDelivery delivery
            WHERE delivery.notificationId IN (
                SELECT notification.id
                FROM Notification notification
                WHERE notification.createdAt < :cutoff
            )
            """)
    void deleteAllByNotificationCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT delivery FROM PushDelivery delivery WHERE delivery.id = :deliveryId")
    Optional<PushDelivery> findByIdForUpdate(@Param("deliveryId") Long deliveryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT delivery
            FROM PushDelivery delivery
            WHERE (
                delivery.status = :pendingStatus
                AND (delivery.nextRetryAt IS NULL OR delivery.nextRetryAt <= :now)
            ) OR (
                delivery.status = :sendingStatus
                AND delivery.sendingStartedAt <= :staleBefore
            )
            ORDER BY delivery.id ASC
            """)
    List<PushDelivery> findSendableDeliveriesForUpdate(
            @Param("pendingStatus") PushDeliveryStatus pendingStatus,
            @Param("sendingStatus") PushDeliveryStatus sendingStatus,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );
}
