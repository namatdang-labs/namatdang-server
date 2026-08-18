package com.namatdang.namatdang.notification.handler.repository;

import com.namatdang.namatdang.notification.handler.entity.NotificationEventConsumption;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventConsumptionRepository
        extends JpaRepository<NotificationEventConsumption, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM NotificationEventConsumption consumption
            WHERE consumption.processedAt < :cutoff
            """)
    void deleteAllProcessedBefore(@Param("cutoff") LocalDateTime cutoff);
}
