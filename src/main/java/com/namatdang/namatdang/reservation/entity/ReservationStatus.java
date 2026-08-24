package com.namatdang.namatdang.reservation.entity;

public enum ReservationStatus {

    /**
     * 결제가 완료되어 수령을 기다리는 상태.
     */
    RESERVED,

    /**
     * 취소되어 수량을 판매 가능 수량으로 복원한 최종 상태.
     */
    CANCELED,

    /**
     * 상품 전달을 확인한 최종 상태. 재고 수량은 변경하지 않는다.
     */
    PICKED_UP
}
