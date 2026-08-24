package com.namatdang.namatdang.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.media.processing.ImageValidator;
import com.namatdang.namatdang.media.processing.ImageVariantProcessor;
import com.namatdang.namatdang.media.processing.ImageVariantSet;
import com.namatdang.namatdang.media.processing.ProcessedImage;
import com.namatdang.namatdang.media.storage.ImageStorage;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestImageStorageConfiguration {

    @Bean
    @Primary
    ImageStorage testImageStorage() {
        return new InMemoryImageStorage();
    }

    @Bean
    @Primary
    ImageVariantProcessor testImageVariantProcessor() {
        ImageVariantProcessor processor = mock(ImageVariantProcessor.class);
        EnumMap<ImageVariant, ProcessedImage> variants = new EnumMap<>(ImageVariant.class);
        for (ImageVariant variant : ImageVariant.values()) {
            variants.put(variant, new ProcessedImage(TestImages.webp(), "image/webp", 1, 1));
        }
        when(processor.process(any())).thenAnswer(invocation -> {
            new ImageValidator().validate(invocation.getArgument(0));
            return new ImageVariantSet(variants);
        });
        return processor;
    }

    private static final class InMemoryImageStorage implements ImageStorage {

        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void write(String key, String contentType, byte[] bytes) {
            objects.put(key, Arrays.copyOf(bytes, bytes.length));
        }

        @Override
        public Optional<byte[]> read(String key) {
            byte[] bytes = objects.get(key);
            return bytes == null
                    ? Optional.empty()
                    : Optional.of(Arrays.copyOf(bytes, bytes.length));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }
}
