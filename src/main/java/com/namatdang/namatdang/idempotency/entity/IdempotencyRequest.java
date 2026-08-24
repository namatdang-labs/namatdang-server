package com.namatdang.namatdang.idempotency.entity;

import com.namatdang.namatdang.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 멱등키가 필요한 쓰기 요청을 선점하고, 최초 성공 응답을 도메인 변경과 같은 트랜잭션에 저장한다.
 * 처리에 실패하면 도메인 변경과 이 기록이 함께 롤백된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "idempotency_requests",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_idempotency_requests_user_operation_key",
               columnNames = {"user_id", "operation", "idempotency_key"}))
public class IdempotencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyOperation operation;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /**
     * 같은 키에 다른 요청 본문을 보냈는지 판단하는 SHA-256 값.
     */
    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    private Integer responseStatus;

    @Lob
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    public IdempotencyRequest(User user, IdempotencyOperation operation, String idempotencyKey, String requestHash) {
        this.user = user;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.PROCESSING;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean matches(String otherRequestHash) {
        return requestHash.equals(otherRequestHash);
    }

    /**
     * 최초 성공 응답을 기록한다. 도메인 작업과 같은 트랜잭션에서 호출해야 한다.
     */
    public void complete(int responseStatus, String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.completedAt = LocalDateTime.now();
    }
}
