package com.namatdang.namatdang.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

public final class TestImages {

    private TestImages() {
    }

    public static byte[] jpeg() {
        return encoded("jpg");
    }

    public static byte[] png() {
        return encoded("png");
    }

    public static byte[] webp() {
        byte[] bytes = new byte[30];
        copyAscii(bytes, 0, "RIFF");
        bytes[4] = 22;
        copyAscii(bytes, 8, "WEBP");
        copyAscii(bytes, 12, "VP8X");
        bytes[16] = 10;
        return bytes;
    }

    private static byte[] encoded(String format) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFD7904);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("Test image writer is unavailable: " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test image", exception);
        } finally {
            image.flush();
        }
    }

    private static void copyAscii(byte[] target, int offset, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, target, offset, source.length);
    }
}
