package com.namatdang.namatdang.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded() {
        return ResponseEntity.status(ExceptionCode.IMAGE_TOO_LARGE.getStatus())
                .body(ErrorResponse.from(ExceptionCode.IMAGE_TOO_LARGE));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMultipartRequest() {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.from(ExceptionCode.INVALID_IMAGE));
    }

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
            MissingServletRequestPartException.class,
            // 필수 Idempotency-Key 누락도 다른 요청 오류와 같은 형식으로 응답한다.
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.from(ExceptionCode.INVALID_INPUT_VALUE));
    }
}
