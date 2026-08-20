package com.namatdang.namatdang.idempotency.entity;

public enum IdempotencyStatus {

    /**
     * 요청을 선점했고 도메인 작업이 아직 끝나지 않았다. 처리에 실패하면 도메인 변경과 함께
     * 이 기록도 롤백되므로, 커밋된 PROCESSING은 같은 트랜잭션이 진행 중이라는 뜻이다.
     */
    PROCESSING,

    /**
     * 최초 성공 응답까지 저장됐다. 같은 키·같은 요청은 이 응답을 그대로 반환한다.
     */
    COMPLETED
}
