package com.namatdang.namatdang.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = readPublicKey(properties.publicKeyBase64());
        RSAPrivateKey privateKey = readPrivateKey(properties.privateKeyBase64());
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withPublicKey(readPublicKey(properties.publicKeyBase64())).build();
    }

    private RSAPrivateKey readPrivateKey(String encoded) {
        try {
            requireKey(encoded, "JWT_PRIVATE_KEY_BASE64");
            byte[] key = Base64.getDecoder().decode(encoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(key));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT_PRIVATE_KEY_BASE64 설정을 확인해 주세요.", exception);
        }
    }

    private RSAPublicKey readPublicKey(String encoded) {
        try {
            requireKey(encoded, "JWT_PUBLIC_KEY_BASE64");
            byte[] key = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(key));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT_PUBLIC_KEY_BASE64 설정을 확인해 주세요.", exception);
        }
    }

    private void requireKey(String encoded, String name) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
    }
}
