package com.namatdang.namatdang.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;
}
