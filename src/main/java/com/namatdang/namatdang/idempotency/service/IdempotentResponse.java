package com.namatdang.namatdang.idempotency.service;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 멱등 작업의 결과. 최초 요청이면 새로 만든 DTO를, 재시도면 저장해 둔 최초 성공 응답을 그대로
 * 돌려준다. 재시도는 직렬화를 다시 거치지 않고 저장된 본문을 그대로 내보내므로, 이후 딜이나
 * 품목이 수정돼도 최초 응답과 정확히 같은 내용을 반환한다.
 */
public class IdempotentResponse {

    private final int status;
    private final Object body;
    private final String storedBody;

    private IdempotentResponse(int status, Object body, String storedBody) {
        this.status = status;
        this.body = body;
        this.storedBody = storedBody;
    }

    public static IdempotentResponse created(Object body) {
        return new IdempotentResponse(201, body, null);
    }

    public static IdempotentResponse ok(Object body) {
        return new IdempotentResponse(200, body, null);
    }

    public static IdempotentResponse replayed(int status, String storedBody) {
        return new IdempotentResponse(status, null, storedBody);
    }

    public ResponseEntity<Object> toResponseEntity() {
        if (storedBody != null) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(storedBody);
        }

        return ResponseEntity.status(status).body(body);
    }
}
