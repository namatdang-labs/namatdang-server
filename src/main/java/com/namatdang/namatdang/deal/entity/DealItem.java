package com.namatdang.namatdang.deal.entity;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "deal_items", indexes = {
        @Index(name = "idx_deal_items_deal_id", columnList = "deal_id"),
        @Index(name = "idx_deal_items_deal_id_status", columnList = "deal_id, status")
})
public class DealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int remainingQuantity;

    @Column(nullable = false)
    private int originalPrice;

    @Column(nullable = false)
    private int salePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealItemStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public DealItem(String name, int totalQuantity, int originalPrice, int salePrice) {
        this.name = name.strip();
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.originalPrice = originalPrice;
        this.salePrice = salePrice;
        this.status = DealItemStatus.SELLING;
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

    void assignDeal(Deal deal) {
        this.deal = deal;
    }

    public boolean canDecrease(int quantity) {
        return remainingQuantity >= quantity;
    }

    /**
     * 남은 수량을 차감한다. 재고가 모자라면 INV-01 위반이므로 마지막 방어선으로 막는다.
     * 호출 전에 {@link #canDecrease(int)}로 전 품목을 먼저 확인해야 전체 무산(INV-07)이 성립한다.
     */
    public void decrease(int quantity) {
        if (!canDecrease(quantity)) {
            throw new BusinessLogicException(ExceptionCode.OUT_OF_STOCK);
        }

        this.remainingQuantity -= quantity;
        refreshStatus();
    }

    /**
     * 취소로 되돌아온 수량을 복원한다. 등록 수량을 넘어서면 INV-02 위반이므로 막는다.
     */
    public void restore(int quantity) {
        if (remainingQuantity + quantity > totalQuantity) {
            throw new BusinessLogicException(ExceptionCode.INVALID_INPUT_VALUE);
        }

        this.remainingQuantity += quantity;
        refreshStatus();
    }

    public boolean isSoldOut() {
        return status == DealItemStatus.SOLD_OUT;
    }

    /**
     * 정가 대비 할인율(%). 정가가 판매가 이하이면 0을 반환한다.
     */
    public int discountRate() {
        if (originalPrice <= salePrice) {
            return 0;
        }
        return (originalPrice - salePrice) * 100 / originalPrice;
    }

    private void refreshStatus() {
        this.status = remainingQuantity == 0 ? DealItemStatus.SOLD_OUT : DealItemStatus.SELLING;
    }
}
