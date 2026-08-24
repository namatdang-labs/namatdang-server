package com.namatdang.namatdang.media.processing;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageValidator {

    public static final long MAX_IMAGE_SIZE_BYTES = 15L * 1024 * 1024;
    private static final int MAX_IMAGE_EDGE = 10_000;
    private static final long MAX_IMAGE_PIXELS = 25_000_000;

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessLogicException(ExceptionCode.IMAGE_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessLogicException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE, exception);
        }

        ImageType imageType = detectType(bytes);
        ImageDimensions dimensions = dimensionsOf(imageType, bytes);
        if (imageType == null || dimensions == null || !dimensions.isAllowed()) {
            throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
        }

        return new ValidatedImage(bytes, imageType.contentType, imageType.extension);
    }

    private ImageDimensions dimensionsOf(ImageType imageType, byte[] bytes) {
        if (imageType == null) {
            return null;
        }
        return switch (imageType) {
            case JPEG -> jpegDimensions(bytes);
            case PNG -> pngDimensions(bytes);
            case WEBP -> webpDimensions(bytes);
        };
    }

    private ImageDimensions jpegDimensions(byte[] bytes) {
        if (bytes.length < 4
                || Byte.toUnsignedInt(bytes[bytes.length - 2]) != 0xFF
                || Byte.toUnsignedInt(bytes[bytes.length - 1]) != 0xD9) {
            return null;
        }

        int offset = 2;
        while (offset + 3 < bytes.length) {
            if (Byte.toUnsignedInt(bytes[offset]) != 0xFF) {
                return null;
            }
            while (offset < bytes.length && Byte.toUnsignedInt(bytes[offset]) == 0xFF) {
                offset++;
            }
            if (offset >= bytes.length) {
                return null;
            }

            int marker = Byte.toUnsignedInt(bytes[offset++]);
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) {
                continue;
            }
            if (offset + 1 >= bytes.length) {
                return null;
            }

            int segmentLength = unsignedShort(bytes, offset);
            if (segmentLength < 2 || offset + segmentLength > bytes.length) {
                return null;
            }
            if (isStartOfFrame(marker) && segmentLength >= 7) {
                int height = unsignedShort(bytes, offset + 3);
                int width = unsignedShort(bytes, offset + 5);
                return new ImageDimensions(width, height);
            }
            offset += segmentLength;
        }
        return null;
    }

    private boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF
                && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private ImageDimensions pngDimensions(byte[] bytes) {
        byte[] iend = new byte[]{0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        if (bytes.length < 33
                || !Arrays.equals(Arrays.copyOfRange(bytes, 12, 16), "IHDR".getBytes(StandardCharsets.US_ASCII))
                || !Arrays.equals(Arrays.copyOfRange(bytes, bytes.length - iend.length, bytes.length), iend)) {
            return null;
        }
        return new ImageDimensions(signedInt(bytes, 16, false), signedInt(bytes, 20, false));
    }

    private ImageDimensions webpDimensions(byte[] bytes) {
        if (bytes.length < 30 || Integer.toUnsignedLong(signedInt(bytes, 4, true)) + 8 != bytes.length) {
            return null;
        }

        int chunkSize = signedInt(bytes, 16, true);
        if (chunkSize < 0 || 20L + chunkSize > bytes.length) {
            return null;
        }
        String chunkType = new String(bytes, 12, 4, StandardCharsets.US_ASCII);

        if ("VP8X".equals(chunkType) && chunkSize >= 10) {
            int width = 1 + unsignedInt24(bytes, 24);
            int height = 1 + unsignedInt24(bytes, 27);
            return new ImageDimensions(width, height);
        }
        if ("VP8L".equals(chunkType) && chunkSize >= 5
                && Byte.toUnsignedInt(bytes[20]) == 0x2F) {
            int first = Byte.toUnsignedInt(bytes[21]);
            int second = Byte.toUnsignedInt(bytes[22]);
            int third = Byte.toUnsignedInt(bytes[23]);
            int fourth = Byte.toUnsignedInt(bytes[24]);
            int width = 1 + first + ((second & 0x3F) << 8);
            int height = 1 + ((second & 0xC0) >> 6) + (third << 2) + ((fourth & 0x0F) << 10);
            return new ImageDimensions(width, height);
        }
        if ("VP8 ".equals(chunkType) && chunkSize >= 10
                && Byte.toUnsignedInt(bytes[23]) == 0x9D
                && Byte.toUnsignedInt(bytes[24]) == 0x01
                && Byte.toUnsignedInt(bytes[25]) == 0x2A) {
            int width = unsignedShortLittleEndian(bytes, 26) & 0x3FFF;
            int height = unsignedShortLittleEndian(bytes, 28) & 0x3FFF;
            return new ImageDimensions(width, height);
        }
        return null;
    }

    private int unsignedShort(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) << 8 | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private int unsignedShortLittleEndian(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | Byte.toUnsignedInt(bytes[offset + 1]) << 8;
    }

    private int unsignedInt24(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | Byte.toUnsignedInt(bytes[offset + 1]) << 8
                | Byte.toUnsignedInt(bytes[offset + 2]) << 16;
    }

    private int signedInt(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return Byte.toUnsignedInt(bytes[offset])
                    | Byte.toUnsignedInt(bytes[offset + 1]) << 8
                    | Byte.toUnsignedInt(bytes[offset + 2]) << 16
                    | Byte.toUnsignedInt(bytes[offset + 3]) << 24;
        }
        return Byte.toUnsignedInt(bytes[offset]) << 24
                | Byte.toUnsignedInt(bytes[offset + 1]) << 16
                | Byte.toUnsignedInt(bytes[offset + 2]) << 8
                | Byte.toUnsignedInt(bytes[offset + 3]);
    }

    private ImageType detectType(byte[] bytes) {
        if (startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF})) {
            return ImageType.JPEG;
        }
        if (startsWith(bytes, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ImageType.PNG;
        }
        if (bytes.length >= 12
                && Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return ImageType.WEBP;
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    public record ValidatedImage(byte[] bytes, String contentType, String extension) {
    }

    private record ImageDimensions(int width, int height) {

        private boolean isAllowed() {
            return width > 0 && height > 0
                    && width <= MAX_IMAGE_EDGE && height <= MAX_IMAGE_EDGE
                    && (long) width * height <= MAX_IMAGE_PIXELS;
        }
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }
}
