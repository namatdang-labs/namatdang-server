package com.namatdang.namatdang.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class IntegrationTestSupport {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.private-key-base64", TestJwtKeys::privateKeyBase64);
        registry.add("security.jwt.public-key-base64", TestJwtKeys::publicKeyBase64);
    }
}
