package com.namatdang.namatdang.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다."),
    USER_EMAIL_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "이메일 또는 비밀번호를 확인해 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 인증 토큰입니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다."),
    OWNER_HAS_STORES(HttpStatus.CONFLICT, "OWNER_HAS_STORES", "등록된 매장이 있어 탈퇴할 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    DEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "DEAL_NOT_FOUND", "딜을 찾을 수 없습니다."),
    DEAL_NOT_RESERVABLE(HttpStatus.CONFLICT, "DEAL_NOT_RESERVABLE", "예약할 수 없는 딜입니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "OUT_OF_STOCK", "재고가 부족해 예약하지 못했습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."),
    RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "RESERVATION_ALREADY_EXISTS", "이미 예약한 딜입니다."),
    RESERVATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELABLE", "취소할 수 없는 예약입니다."),
    RESERVATION_NOT_PICKUPABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_PICKUPABLE", "수령 완료할 수 없는 예약입니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "같은 멱등키로 다른 요청을 보낼 수 없습니다."),
    CONCURRENT_REQUEST_CONFLICT(HttpStatus.CONFLICT, "CONCURRENT_REQUEST_CONFLICT", "같은 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
    PUSH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "PUSH_TOKEN_NOT_FOUND", "Push 토큰을 찾을 수 없습니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "등록된 이미지를 찾을 수 없습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "JPG, PNG, WebP 이미지 파일을 선택해 주세요."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "이미지는 15MB 이하로 선택해 주세요."),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE",
                              "이미지를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ExceptionCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
