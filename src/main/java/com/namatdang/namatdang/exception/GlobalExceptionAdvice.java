package com.namatdang.namatdang.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ErrorResponse> handleBusinessLogicException(BusinessLogicException exception) {
        ExceptionCode exceptionCode = exception.getExceptionCode();

        return ResponseEntity.status(exceptionCode.getStatus())
                .body(new ErrorResponse(exceptionCode.getCode(), exceptionCode.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        ExceptionCode.INVALID_INPUT_VALUE.getCode(),
                        ExceptionCode.INVALID_INPUT_VALUE.getMessage()
                ));
    }
}
