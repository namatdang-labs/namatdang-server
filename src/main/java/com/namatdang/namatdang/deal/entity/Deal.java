package com.namatdang.namatdang.deal.entity;

import com.namatdang.namatdang.store.entity.Store;
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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
@Table(name = "deals", indexes = {
        @Index(name = "idx_deals_store_id", columnList = "store_id"),
        @Index(name = "idx_deals_store_id_status", columnList = "store_id, status"),
        @Index(name = "idx_deals_store_created_at_id", columnList = "store_id, created_at, id"),
        @Index(name = "idx_deals_sales_ends_at", columnList = "sales_ends_at")
})
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "sales_ends_at", nullable = false)
    private LocalDateTime salesEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStatus status;

    @Lob
    private String description;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    @BatchSize(size = 100)
    private List<DealItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

    private LocalDateTime canceledAt;

    public Deal(Store store, LocalDateTime salesEndsAt, String description) {
        this.store = store;
        this.salesEndsAt = salesEndsAt;
        this.description = description == null ? null : description.strip();
        this.status = DealStatus.SELLING;
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

    public void addItem(DealItem item) {
        items.add(item);
        item.assignDeal(this);
    }

    /**
     * 예약을 받을 수 있는 상태인지 판단한다. 자동 마감 배치가 없으므로 마감시각도 함께 확인한다.
     */
    public boolean isReservable(LocalDateTime now) {
        return status == DealStatus.SELLING && salesEndsAt.isAfter(now);
    }

    /**
     * 외부에 보여줄 상태. 자동 마감 배치가 아직 없으므로, 마감시각이 지난 판매중·종료 딜은
     * 저장된 상태와 무관하게 CLOSED로 보이게 한다.
     * <p>
     * TODO: #27 - 자동 마감 배치가 status를 CLOSED로 전이시키게 되면 이 파생 로직을 지우고
     * DTO들이 {@code getStatus()}를 쓰도록 되돌린다.
     */
    public DealStatus displayStatus(LocalDateTime now) {
        boolean beforeClosing = status == DealStatus.SELLING || status == DealStatus.ENDED;

        if (beforeClosing && !salesEndsAt.isAfter(now)) {
            return DealStatus.CLOSED;
        }

        return status;
    }

    public void markEnded() {
        this.status = DealStatus.ENDED;
    }

    public void markSelling() {
        this.status = DealStatus.SELLING;
    }

    public boolean isEnded() {
        return status == DealStatus.ENDED;
    }

    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public int lowestSalePrice() {
        return items.stream()
                .mapToInt(DealItem::getSalePrice)
                .min()
                .orElse(0);
    }
}
