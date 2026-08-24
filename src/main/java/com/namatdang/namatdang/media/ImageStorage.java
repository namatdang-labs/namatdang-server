package com.namatdang.namatdang.media;

import java.util.Optional;

public interface ImageStorage {

    void write(String key, String contentType, byte[] bytes);

    Optional<byte[]> read(String key);

    void delete(String key);
}
