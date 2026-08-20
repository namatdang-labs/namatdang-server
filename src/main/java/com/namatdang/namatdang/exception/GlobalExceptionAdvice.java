package com.namatdang.namatdang.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ErrorResponse> handleBusinessLogicException(BusinessLogicException exception) {
        ExceptionCode exceptionCode = exception.getExceptionCode();

        return ResponseEntity.status(exceptionCode.getStatus())
                .body(ErrorResponse.from(exceptionCode));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            // 필수 Idempotency-Key 누락도 다른 요청 오류와 같은 형식으로 응답한다.
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.from(ExceptionCode.INVALID_INPUT_VALUE));
    }
}
