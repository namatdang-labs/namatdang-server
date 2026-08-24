package com.namatdang.namatdang.media;

import com.namatdang.namatdang.deal.entity.Deal;
import com.namatdang.namatdang.store.entity.Store;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class ImageUrls {

    private ImageUrls() {
    }

    public static String forStore(Store store) {
        return forStore(store, ImageVariant.DETAIL);
    }

    public static String forStore(Store store, ImageVariant variant) {
        if (store.getImageKey() == null) {
            return null;
        }

        return "/api/v1/stores/%d/image?variant=%s&v=%s".formatted(
                store.getId(), requestValue(variant), versionOf(store.getImageKey()));
    }

    public static String forDeal(Deal deal) {
        return forDeal(deal, ImageVariant.DETAIL);
    }

    public static String forDeal(Deal deal, ImageVariant variant) {
        if (deal.getImageKey() == null) {
            return null;
        }

        return "/api/v1/deals/%d/image?variant=%s&v=%s".formatted(
                deal.getId(), requestValue(variant), versionOf(deal.getImageKey()));
    }

    static String versionOf(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private static String requestValue(ImageVariant variant) {
        return variant.name().toLowerCase(Locale.ROOT);
    }
}
