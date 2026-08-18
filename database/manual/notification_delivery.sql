-- Flyway가 아닌 운영자 수동 적용용 DDL이다. 적용 전 대상 DB와 백업 정책을 확인한다.
CREATE TABLE notification_event_consumptions (
    event_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_notification_event_consumptions_processed (processed_at)
);

CREATE TABLE push_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    fcm_registration_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    sending_started_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_deliveries_notification_registration
        UNIQUE (notification_id, fcm_registration_id),
    CONSTRAINT fk_push_deliveries_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT chk_push_deliveries_retry_count CHECK (retry_count >= 0),
    INDEX idx_push_deliveries_registration (fcm_registration_id),
    INDEX idx_push_deliveries_retry (status, next_retry_at, id),
    INDEX idx_push_deliveries_recovery (status, sending_started_at, id)
);
