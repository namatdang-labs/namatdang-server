package com.namatdang.namatdang.security;

import com.namatdang.namatdang.exception.ErrorResponse;
import com.namatdang.namatdang.exception.ExceptionCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ExceptionCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(code.getCode(), code.getMessage()));
    }
}
