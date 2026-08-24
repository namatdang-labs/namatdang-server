package com.namatdang.namatdang.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class TestJwtKeys {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwtKeys() {
    }

    public static String privateKeyBase64() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
    }

    public static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("테스트 RSA 키 생성에 실패했습니다.", exception);
        }
    }
}
