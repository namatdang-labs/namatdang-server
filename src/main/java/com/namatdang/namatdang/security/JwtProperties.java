package com.namatdang.namatdang.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String privateKeyBase64,
        String publicKeyBase64,
        long accessTokenExpirationSeconds
) {
}
