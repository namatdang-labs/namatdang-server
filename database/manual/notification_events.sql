-- Flyway가 아닌 운영자 수동 적용용 DDL이다. 적용 전 대상 DB와 백업 정책을 확인한다.
CREATE TABLE notification_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deal_id BIGINT NULL,
    reservation_id BIGINT NULL,
    store_id BIGINT NULL,
    event_type VARCHAR(50) NOT NULL,
    source_request_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    publishing_started_at DATETIME(6) NULL,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    last_error TEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_events_source_request UNIQUE (source_request_key),
    CONSTRAINT chk_notification_events_source CHECK (
        (deal_id IS NOT NULL AND reservation_id IS NULL)
        OR (deal_id IS NULL AND reservation_id IS NOT NULL)
    ),
    CONSTRAINT chk_notification_events_retry_count CHECK (retry_count >= 0),
    INDEX idx_notification_events_deal (deal_id),
    INDEX idx_notification_events_reservation (reservation_id),
    INDEX idx_notification_events_publish_retry (status, next_retry_at, id),
    INDEX idx_notification_events_publish_recovery (status, publishing_started_at, id)
);
