package com.namatdang.namatdang.deal.entity;

public enum DealStatus {

    /** 판매중. */
    SELLING,

    /** 모든 품목이 품절되어 종료. 예약 취소로 수량이 복원되면 SELLING으로 돌아온다. */
    ENDED,

    /**
     * 판매 마감시각이 지나 마감됨. 되돌아가지 않는다.
     * <p>
     * TODO: #27 - 이 값으로 전이시키는 코드가 아직 없다. 마감 여부는 조회 시점에
     * {@link Deal#displayStatus}가 파생시킬 뿐이고 DB의 status는 SELLING/ENDED로 남는다.
     */
    CLOSED,

    /**
     * 사장님이 취소함(DEAL-03).
     * <p>
     * TODO: #7 - 딜 취소 API(POST /owner/deals/{dealId}/cancel)가 미구현이라 이 값으로
     * 전이시키는 코드가 없다. 유효 예약이 없을 때만 취소 가능하므로 예약 도메인 이후에 구현한다.
     */
    CANCELED
}
