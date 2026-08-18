package com.namatdang.namatdang.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;

    public static ErrorResponse from(ExceptionCode exceptionCode) {
        return new ErrorResponse(exceptionCode.getCode(), exceptionCode.getMessage());
    }
}
