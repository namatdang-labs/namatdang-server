package com.namatdang.namatdang.media;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImageMediaService {

    private static final Logger log = LoggerFactory.getLogger(ImageMediaService.class);

    private final ImageVariantProcessor imageVariantProcessor;
    private final ImageStorage imageStorage;

    public String store(ImageKind kind, MultipartFile file) {
        ImageVariantSet images = imageVariantProcessor.process(file);
        String groupKey = ImageVariantKeys.newGroupKey(kind);
        List<String> writtenKeys = new ArrayList<>(ImageVariant.values().length);

        try {
            for (ImageVariant variant : ImageVariant.values()) {
                ProcessedImage image = images.get(variant);
                String key = ImageVariantKeys.key(groupKey, variant);
                writtenKeys.add(key);
                imageStorage.write(key, image.contentType(), image.bytes());
            }
        } catch (ImageStorageException exception) {
            writtenKeys.forEach(this::deleteObjectQuietly);
            throw new BusinessLogicException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE, exception);
        }

        return groupKey;
    }

    public ImageContent load(String key, String requestedVersion) {
        return load(key, ImageVariant.DETAIL, requestedVersion);
    }

    public ImageContent load(String key, ImageVariant variant, String requestedVersion) {
        String currentVersion = ImageUrls.versionOf(key);
        if (requestedVersion != null && !currentVersion.equals(requestedVersion)) {
            throw new BusinessLogicException(ExceptionCode.IMAGE_NOT_FOUND);
        }

        String objectKey = objectKey(key, variant);
        try {
            byte[] bytes = imageStorage.read(objectKey)
                    .orElseThrow(() -> new BusinessLogicException(ExceptionCode.IMAGE_NOT_FOUND));
            return new ImageContent(bytes, contentTypeFor(objectKey), ImageUrls.versionOf(objectKey));
        } catch (ImageStorageException exception) {
            throw new BusinessLogicException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE, exception);
        }
    }

    public void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    public void deleteImmediately(String key) {
        if (key != null) {
            deleteQuietly(key);
        }
    }

    private void deleteQuietly(String key) {
        if (isLegacyObjectKey(key)) {
            deleteObjectQuietly(key);
            return;
        }

        for (ImageVariant variant : ImageVariant.values()) {
            deleteObjectQuietly(ImageVariantKeys.key(key, variant));
        }
    }

    private void deleteObjectQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (ImageStorageException exception) {
            log.warn("Failed to remove obsolete image object: {}", key, exception);
        }
    }

    private String contentTypeFor(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        throw new BusinessLogicException(ExceptionCode.IMAGE_NOT_FOUND);
    }

    private String objectKey(String key, ImageVariant variant) {
        return isLegacyObjectKey(key) ? key : ImageVariantKeys.key(key, variant);
    }

    private boolean isLegacyObjectKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg")
                || normalized.endsWith(".png")
                || normalized.endsWith(".webp");
    }
}
