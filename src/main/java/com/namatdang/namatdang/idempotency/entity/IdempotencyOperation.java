package com.namatdang.namatdang.idempotency.entity;

/**
 * 멱등키를 요구하는 쓰기 작업의 종류. 유일성 기준이 사용자·작업·키 조합이므로,
 * 서로 다른 작업이 같은 키를 써도 충돌하지 않는다.
 * <p>
 * TODO: #28 - Deal 등록도 이 저장 구조를 재사용한다. DEAL_CREATE를 추가하고
 * OwnerDealController의 X-Request-Id 처리를 여기로 옮긴다.
 */
public enum IdempotencyOperation {

    RESERVATION_CREATE,

    RESERVATION_CANCEL
}
