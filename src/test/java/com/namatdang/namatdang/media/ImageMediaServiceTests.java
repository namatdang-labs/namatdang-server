package com.namatdang.namatdang.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ImageMediaServiceTests {

    @Test
    void onlyLoadsTheCurrentImmutableVersion() {
        InMemoryImageStorage storage = new InMemoryImageStorage();
        ImageMediaService service = service(storage);
        MockMultipartFile file = new MockMultipartFile(
                "image", "store.jpg", "image/jpeg", TestImages.jpeg());

        String key = service.store(ImageKind.STORE, file);
        String version = ImageUrls.versionOf(key);

        assertThat(key).matches("images/stores/[0-9a-f-]{36}");
        assertThat(storage.content).containsOnlyKeys(
                ImageVariantKeys.key(key, ImageVariant.THUMBNAIL),
                ImageVariantKeys.key(key, ImageVariant.CARD),
                ImageVariantKeys.key(key, ImageVariant.DETAIL));
        ImageContent card = service.load(key, ImageVariant.CARD, version);
        assertThat(card.bytes()).containsExactly(2);
        assertThat(card.contentType()).isEqualTo("image/webp");
        assertThat(storage.lastReadKey).isEqualTo(ImageVariantKeys.key(key, ImageVariant.CARD));
        assertThatThrownBy(() -> service.load(key, "outdated"))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.IMAGE_NOT_FOUND));
        assertThat(storage.readCount).isEqualTo(1);
    }

    @Test
    void legacySingleObjectsServeEveryVariantWithoutMigration() {
        InMemoryImageStorage storage = new InMemoryImageStorage();
        ImageMediaService service = service(storage);
        String legacyKey = "images/deals/9/legacy.jpg";
        storage.write(legacyKey, "image/jpeg", TestImages.jpeg());
        String version = ImageUrls.versionOf(legacyKey);

        for (ImageVariant variant : ImageVariant.values()) {
            ImageContent image = service.load(legacyKey, variant, version);
            assertThat(image.bytes()).containsExactly(TestImages.jpeg());
            assertThat(image.contentType()).isEqualTo("image/jpeg");
            assertThat(storage.lastReadKey).isEqualTo(legacyKey);
        }
    }

    @Test
    void cleansEveryAttemptedVariantWhenAStorageWriteFails() {
        InMemoryImageStorage storage = new InMemoryImageStorage();
        storage.failOnWriteNumber = 2;
        ImageMediaService service = service(storage);
        MockMultipartFile file = new MockMultipartFile(
                "image", "deal.jpg", "image/jpeg", TestImages.jpeg());

        assertThatThrownBy(() -> service.store(ImageKind.DEAL, file))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode())
                                .isEqualTo(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE));

        assertThat(storage.content).isEmpty();
        assertThat(storage.deletedKeys).hasSize(2)
                .allMatch(key -> key.startsWith("images/deals/"));
    }

    @Test
    void leavesOutsideTransactionUploadForExplicitCleanupAndDeletesOldObjectsOnlyAfterCommit() {
        InMemoryImageStorage storage = new InMemoryImageStorage();
        ImageMediaService service = service(storage);
        MockMultipartFile file = new MockMultipartFile(
                "image", "store.jpg", "image/jpeg", TestImages.jpeg());

        TransactionSynchronizationManager.initSynchronization();
        try {
            String newKey = service.store(ImageKind.STORE, file);
            assertThat(storage.content.keySet()).allMatch(key -> key.startsWith(newKey + "/"));
            assertThat(storage.content).hasSize(3);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            service.deleteImmediately(newKey);
            assertThat(storage.content).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        String oldGroup = ImageVariantKeys.groupKey(
                ImageKind.STORE, 7L, java.util.UUID.fromString("00000000-0000-0000-0000-000000000007"));
        for (ImageVariant variant : ImageVariant.values()) {
            storage.write(ImageVariantKeys.key(oldGroup, variant), "image/webp", new byte[]{7});
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteAfterCommit(oldGroup);
            assertThat(storage.content).hasSize(3);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertThat(storage.content).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void immediatelyCleansLegacySingleObjectsAndLegacyEntityGroups() {
        InMemoryImageStorage storage = new InMemoryImageStorage();
        ImageMediaService service = service(storage);
        String legacyObject = "images/deals/9/legacy.jpg";
        storage.write(legacyObject, "image/jpeg", TestImages.jpeg());
        String oldGroup = ImageVariantKeys.groupKey(
                ImageKind.DEAL, 9L, java.util.UUID.fromString("00000000-0000-0000-0000-000000000009"));
        for (ImageVariant variant : ImageVariant.values()) {
            storage.write(ImageVariantKeys.key(oldGroup, variant), "image/webp", new byte[]{9});
        }

        service.deleteImmediately(legacyObject);
        service.deleteImmediately(oldGroup);

        assertThat(storage.content).isEmpty();
        assertThat(storage.deletedKeys).contains(legacyObject);
        assertThat(storage.deletedKeys).contains(
                ImageVariantKeys.key(oldGroup, ImageVariant.THUMBNAIL),
                ImageVariantKeys.key(oldGroup, ImageVariant.CARD),
                ImageVariantKeys.key(oldGroup, ImageVariant.DETAIL));
    }

    private ImageMediaService service(InMemoryImageStorage storage) {
        ImageVariantProcessor processor = mock(ImageVariantProcessor.class);
        EnumMap<ImageVariant, ProcessedImage> variants = new EnumMap<>(ImageVariant.class);
        variants.put(ImageVariant.THUMBNAIL, new ProcessedImage(new byte[]{1}, "image/webp", 320, 200));
        variants.put(ImageVariant.CARD, new ProcessedImage(new byte[]{2}, "image/webp", 768, 480));
        variants.put(ImageVariant.DETAIL, new ProcessedImage(new byte[]{3}, "image/webp", 1600, 1000));
        when(processor.process(any())).thenReturn(new ImageVariantSet(variants));
        return new ImageMediaService(processor, storage);
    }

    private static final class InMemoryImageStorage implements ImageStorage {

        private final Map<String, byte[]> content = new HashMap<>();
        private final Set<String> deletedKeys = new HashSet<>();
        private int readCount;
        private int writeCount;
        private int failOnWriteNumber = -1;
        private String lastReadKey;

        @Override
        public void write(String key, String contentType, byte[] bytes) {
            writeCount++;
            if (writeCount == failOnWriteNumber) {
                throw new ImageStorageException("simulated write failure", null);
            }
            content.put(key, bytes);
        }

        @Override
        public Optional<byte[]> read(String key) {
            readCount++;
            lastReadKey = key;
            return Optional.ofNullable(content.get(key));
        }

        @Override
        public void delete(String key) {
            deletedKeys.add(key);
            content.remove(key);
        }
    }
}
