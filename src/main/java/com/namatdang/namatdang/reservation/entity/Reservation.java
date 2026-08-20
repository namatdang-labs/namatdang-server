package com.namatdang.namatdang.reservation.entity;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservations",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_reservations_consumer_id_deal_id",
               columnNames = {"consumer_id", "deal_id"}),
       indexes = {
               @Index(name = "idx_reservations_consumer_id_status_id", columnList = "consumer_id, status, id"),
               @Index(name = "idx_reservations_deal_id_status_id", columnList = "deal_id, status, id")
       })
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consumer_id", nullable = false)
    private User consumer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;

    @Column(nullable = false)
    private long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    @BatchSize(size = 100)
    private List<ReservationItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime pickedUpAt;

    public Reservation(User consumer, Deal deal) {
        this.consumer = consumer;
        this.deal = deal;
        this.status = ReservationStatus.RESERVED;
        this.totalAmount = 0L;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 품목 스냅샷을 추가하고 총액을 다시 계산한다. 총액은 클라이언트가 보낸 값을 믿지 않고
     * 항상 서버가 스냅샷 소계의 합으로 확정한다.
     */
    public void addItem(ReservationItem item) {
        items.add(item);
        item.assignReservation(this);
        this.totalAmount += item.getSubtotal();
    }

    /**
     * 판매 마감시각과 무관하게 RESERVED인 예약만 취소할 수 있다. 이미 취소된 예약은
     * 수량을 다시 복원하지 않도록 false를 반환하고, 수령 완료된 예약은 거절한다(INV-03, INV-06).
     *
     * @return 이번 호출로 실제 상태가 바뀌었으면 true
     */
    public boolean cancel(LocalDateTime now) {
        if (status == ReservationStatus.CANCELED) {
            return false;
        }

        if (status == ReservationStatus.PICKED_UP) {
            throw new BusinessLogicException(ExceptionCode.RESERVATION_NOT_CANCELABLE);
        }

        this.status = ReservationStatus.CANCELED;
        this.canceledAt = now;
        return true;
    }

    /**
     * 판매 마감시각과 무관하게 RESERVED인 예약만 수령 완료할 수 있다. 이미 수령 완료된 예약은
     * 현재 결과를 그대로 두고, 취소된 예약은 거절한다(INV-06).
     *
     * @return 이번 호출로 실제 상태가 바뀌었으면 true
     */
    public boolean pickUp(LocalDateTime now) {
        if (status == ReservationStatus.PICKED_UP) {
            return false;
        }

        if (status == ReservationStatus.CANCELED) {
            throw new BusinessLogicException(ExceptionCode.RESERVATION_NOT_PICKUPABLE);
        }

        this.status = ReservationStatus.PICKED_UP;
        this.pickedUpAt = now;
        return true;
    }

    public boolean isOwnedBy(Long consumerId) {
        return consumer.getId().equals(consumerId);
    }
}
