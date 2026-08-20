package com.namatdang.namatdang.reservation.entity;

import com.namatdang.namatdang.deal.entity.DealItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약 당시의 상품명·판매가·수량 스냅샷. 딜과 품목이 수정돼도 이 값은 바뀌지 않는다(RSV-11).
 * 응답은 원본 DealItem의 현재 값이 아니라 이 스냅샷을 사용한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservation_items",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_reservation_items_reservation_id_deal_item_id",
               columnNames = {"reservation_id", "deal_item_id"}),
       indexes = @Index(name = "idx_reservation_items_deal_item_id", columnList = "deal_item_id"))
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_item_id", nullable = false)
    private DealItem dealItem;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int salePrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long subtotal;

    public ReservationItem(DealItem dealItem, int quantity) {
        this.dealItem = dealItem;
        this.name = dealItem.getName();
        this.salePrice = dealItem.getSalePrice();
        this.quantity = quantity;
        this.subtotal = (long) dealItem.getSalePrice() * quantity;
    }

    void assignReservation(Reservation reservation) {
        this.reservation = reservation;
    }
}
