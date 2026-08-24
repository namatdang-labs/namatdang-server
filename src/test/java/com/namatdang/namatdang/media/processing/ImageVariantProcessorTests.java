package com.namatdang.namatdang.media.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.media.processing.ImageValidator.ValidatedImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ImageVariantProcessorTests {

    private static final byte[] ENCODED_WEBP = "RIFF\u0004\u0000\u0000\u0000WEBP"
            .getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ONE_PIXEL_WEBP = Base64.getDecoder().decode(
            "UklGRjwAAABXRUJQVlA4IDAAAAAQAgCdASoBAAEAAgA0JaACdLoB+AH4AAPIAP7iRr/2dypQt/tw//W/+oID9IAAAAA=");

    @Test
    void acceptsJpegPngAndWebpInputs() {
        CwebpEncoder encoder = encoderReturningWebp();
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        assertThat(processor.process(file("photo.jpg", "image/jpeg", encodedImage("jpg", 8, 4)))
                           .variants()).hasSize(3);
        assertThat(processor.process(file("photo.png", "image/png", encodedImage("png", 8, 4)))
                           .variants()).hasSize(3);
        assertThat(processor.process(file("photo.webp", "image/webp", ONE_PIXEL_WEBP))
                           .variants()).hasSize(3);
    }

    @Test
    void createsSquareCardAndFourByThreeVariantsFromALandscapeImage() {
        List<Dimensions> encodedDimensions = new ArrayList<>();
        CwebpEncoder encoder = mock(CwebpEncoder.class);
        when(encoder.encode(any())).thenAnswer(invocation -> {
            BufferedImage image = invocation.getArgument(0);
            encodedDimensions.add(new Dimensions(image.getWidth(), image.getHeight()));
            return ENCODED_WEBP;
        });
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        ImageVariantSet result = processor.process(
                file("photo.jpg", "image/jpeg", encodedImage("jpg", 3_200, 1_800)));

        assertThat(encodedDimensions).containsExactly(
                new Dimensions(320, 320),
                new Dimensions(768, 432),
                new Dimensions(1_600, 1_200));
        assertThat(result.get(ImageVariant.THUMBNAIL).width()).isEqualTo(320);
        assertThat(result.get(ImageVariant.DETAIL).height()).isEqualTo(1_200);
    }

    @Test
    void centerCropsPortraitImagesWithoutUpscalingTheDetailVariant() {
        List<Dimensions> encodedDimensions = new ArrayList<>();
        CwebpEncoder encoder = mock(CwebpEncoder.class);
        when(encoder.encode(any())).thenAnswer(invocation -> {
            BufferedImage image = invocation.getArgument(0);
            encodedDimensions.add(new Dimensions(image.getWidth(), image.getHeight()));
            return ENCODED_WEBP;
        });
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        processor.process(file("photo.png", "image/png", encodedImage("png", 1_000, 4_000)));

        assertThat(encodedDimensions).containsExactly(
                new Dimensions(320, 320),
                new Dimensions(768, 432),
                new Dimensions(1_000, 750));
    }

    @Test
    void centerCropsSmallImagesWithoutUpscaling() {
        CwebpEncoder encoder = encoderReturningWebp();
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        ImageVariantSet result = processor.process(
                file("photo.png", "image/png", encodedImage("png", 200, 100)));

        assertThat(result.get(ImageVariant.THUMBNAIL).width()).isEqualTo(100);
        assertThat(result.get(ImageVariant.THUMBNAIL).height()).isEqualTo(100);
        assertThat(result.get(ImageVariant.CARD).width()).isEqualTo(178);
        assertThat(result.get(ImageVariant.CARD).height()).isEqualTo(100);
        assertThat(result.get(ImageVariant.DETAIL).width()).isEqualTo(133);
        assertThat(result.get(ImageVariant.DETAIL).height()).isEqualTo(100);
        verify(encoder, times(3)).encode(any());
    }

    @Test
    void appliesEveryExifOrientation() {
        BufferedImage source = numberedImage();
        int[][][] expected = {
                {{1, 2, 3}, {4, 5, 6}},
                {{3, 2, 1}, {6, 5, 4}},
                {{6, 5, 4}, {3, 2, 1}},
                {{4, 5, 6}, {1, 2, 3}},
                {{1, 4}, {2, 5}, {3, 6}},
                {{4, 1}, {5, 2}, {6, 3}},
                {{6, 3}, {5, 2}, {4, 1}},
                {{3, 6}, {2, 5}, {1, 4}}
        };

        for (int orientation = 1; orientation <= 8; orientation++) {
            BufferedImage transformed = ImageVariantProcessor.applyOrientation(source, orientation);
            assertThat(pixelValues(transformed)).as("EXIF orientation %s", orientation)
                    .isDeepEqualTo(expected[orientation - 1]);
            if (transformed != source) {
                transformed.flush();
            }
        }
        source.flush();
    }

    @Test
    void readsEveryExifOrientationValueFromJpegMetadata() {
        byte[] jpeg = encodedImage("jpg", 3, 2);

        for (int orientation = 1; orientation <= 8; orientation++) {
            assertThat(ImageVariantProcessor.exifOrientation(withExifOrientation(jpeg, orientation)))
                    .isEqualTo(orientation);
        }
    }

    @Test
    void acquiresTheSinglePermitBeforeValidationAndTimesOutTheNextRequest() throws Exception {
        ImageValidator validator = mock(ImageValidator.class);
        CountDownLatch validationEntered = new CountDownLatch(1);
        CountDownLatch releaseValidation = new CountDownLatch(1);
        byte[] bytes = encodedImage("png", 2, 1);
        when(validator.validate(any())).thenAnswer(invocation -> {
            validationEntered.countDown();
            if (!releaseValidation.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test validator was not released");
            }
            return new ValidatedImage(bytes, "image/png", "png");
        });
        CwebpEncoder encoder = encoderReturningWebp();
        ImageVariantProcessor processor = processor(validator, encoder, Duration.ofMillis(100));
        MultipartFile input = file("photo.png", "image/png", bytes);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ImageVariantSet> first = executor.submit(() -> processor.process(input));
            assertThat(validationEntered.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> processor.process(input))
                    .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                            assertThat(exception.getExceptionCode())
                                    .isEqualTo(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE));
            verify(validator, times(1)).validate(any());

            releaseValidation.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS).variants()).hasSize(3);
        } finally {
            releaseValidation.countDown();
        }
    }

    @Test
    void centerCropsTheDecodeRegionBeforeCalculatingSafeSubsampling() {
        ImageVariantProcessor.DecodePlan regular = ImageVariantProcessor.decodePlan(4_000, 3_000, 1);
        ImageVariantProcessor.DecodePlan panorama = ImageVariantProcessor.decodePlan(10_000, 1_000, 1);
        ImageVariantProcessor.DecodePlan rotatedPanorama = ImageVariantProcessor.decodePlan(10_000, 1_000, 6);

        assertThat(regular.sourceRegion()).isEqualTo(new ImageVariantProcessor.Crop(0, 0, 4_000, 3_000));
        assertThat(regular.subsampling()).isEqualTo(2);
        assertThat(regular.decodedWidth()).isEqualTo(2_000);
        assertThat(regular.decodedHeight()).isEqualTo(1_500);
        assertThat(panorama.sourceRegion())
                .isEqualTo(new ImageVariantProcessor.Crop(4_111, 0, 1_778, 1_000));
        assertThat(panorama.subsampling()).isEqualTo(1);
        assertThat(rotatedPanorama.sourceRegion())
                .isEqualTo(new ImageVariantProcessor.Crop(4_500, 0, 1_000, 1_000));
        assertThat(rotatedPanorama.decodedWidth()).isEqualTo(1_000);
        assertThat(rotatedPanorama.decodedHeight()).isEqualTo(1_000);
    }

    @Test
    void appliesOrientationAfterSubsampledDecodeBeforeCalculatingVariants() {
        List<Dimensions> encodedDimensions = new ArrayList<>();
        CwebpEncoder encoder = mock(CwebpEncoder.class);
        when(encoder.encode(any())).thenAnswer(invocation -> {
            BufferedImage image = invocation.getArgument(0);
            encodedDimensions.add(new Dimensions(image.getWidth(), image.getHeight()));
            return ENCODED_WEBP;
        });
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));
        byte[] portraitByExif = withExifOrientation(encodedImage("jpg", 4_000, 1_000), 6);

        processor.process(file("photo.jpg", "image/jpeg", portraitByExif));

        assertThat(encodedDimensions).containsExactly(
                new Dimensions(320, 320),
                new Dimensions(768, 432),
                new Dimensions(1_000, 750));
    }

    @Test
    void usesTheCenterOfTheSourceForCoverCrop() {
        BufferedImage source = numberedImage(4, 2);

        BufferedImage thumbnail = ImageVariantProcessor.renderVariant(source, ImageVariant.THUMBNAIL);

        try {
            assertThat(pixelValues(thumbnail)).isDeepEqualTo(new int[][]{{2, 3}, {6, 7}});
        } finally {
            thumbnail.flush();
            source.flush();
        }
    }

    @Test
    void preservesTheLeftAndRightEdgesNeededByTheFinalSixteenByNineCard() {
        BufferedImage source = new BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            source.setRGB(0, y, 0x00FF_0000);
            source.setRGB(15, y, 0x0000_00FF);
        }
        List<int[][]> encodedPixels = new ArrayList<>();
        CwebpEncoder encoder = mock(CwebpEncoder.class);
        when(encoder.encode(any())).thenAnswer(invocation -> {
            encodedPixels.add(pixelValues(invocation.getArgument(0)));
            return ENCODED_WEBP;
        });
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        try {
            processor.process(file("wide.png", "image/png", encodedImage(source, "png")));
        } finally {
            source.flush();
        }

        int[][] card = encodedPixels.get(ImageVariant.CARD.ordinal());
        assertThat(card).hasDimensions(9, 16);
        assertThat(card[4][0]).isEqualTo(0x00FF_0000);
        assertThat(card[4][15]).isEqualTo(0x0000_00FF);
    }

    @Test
    void keepsTheCentralCompositionOfAPortraitSource() {
        BufferedImage source = new BufferedImage(4, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, y + 1);
            }
        }
        List<int[][]> encodedPixels = new ArrayList<>();
        CwebpEncoder encoder = mock(CwebpEncoder.class);
        when(encoder.encode(any())).thenAnswer(invocation -> {
            encodedPixels.add(pixelValues(invocation.getArgument(0)));
            return ENCODED_WEBP;
        });
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        try {
            processor.process(file("portrait.png", "image/png", encodedImage(source, "png")));
        } finally {
            source.flush();
        }

        int[][] thumbnail = encodedPixels.get(ImageVariant.THUMBNAIL.ordinal());
        assertThat(thumbnail).hasDimensions(4, 4);
        assertThat(thumbnail[0][0]).isEqualTo(5);
        assertThat(thumbnail[3][0]).isEqualTo(8);
    }

    @Test
    void keepsCorrectPixelsAndDimensionsAfterExifSixAndEightAxisSwap() {
        BufferedImage source = numberedImage(4, 2);

        BufferedImage clockwise = ImageVariantProcessor.applyOrientation(source, 6);
        BufferedImage clockwiseCard = ImageVariantProcessor.renderVariant(clockwise, ImageVariant.CARD);
        BufferedImage counterClockwise = ImageVariantProcessor.applyOrientation(source, 8);
        BufferedImage counterClockwiseCard = ImageVariantProcessor.renderVariant(
                counterClockwise, ImageVariant.CARD);

        try {
            assertThat(pixelValues(clockwiseCard)).isDeepEqualTo(new int[][]{{6, 2}});
            assertThat(pixelValues(counterClockwiseCard)).isDeepEqualTo(new int[][]{{3, 7}});
        } finally {
            clockwiseCard.flush();
            clockwise.flush();
            counterClockwiseCard.flush();
            counterClockwise.flush();
            source.flush();
        }
    }

    @Test
    void rejectsUndecodableContentBeforeAnyEncoding() {
        CwebpEncoder encoder = encoderReturningWebp();
        ImageVariantProcessor processor = processor(encoder, Duration.ofSeconds(1));

        assertThatThrownBy(() -> processor.process(
                file("broken.jpg", "image/jpeg", headerOnlyJpeg())))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.INVALID_IMAGE));
        verify(encoder, times(0)).encode(any());
    }

    @Test
    void rejectsAValidatedImageContractThatClaimsAnUnsupportedType() {
        CwebpEncoder encoder = encoderReturningWebp();
        ImageValidator validator = mock(ImageValidator.class);
        when(validator.validate(any())).thenReturn(
                new ValidatedImage(encodedImage("png", 2, 1), "image/gif", "gif"));
        ImageVariantProcessor processor = processor(validator, encoder, Duration.ofSeconds(1));

        assertThatThrownBy(() -> processor.process(
                file("photo.gif", "image/gif", encodedImage("png", 2, 1))))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception ->
                        assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.INVALID_IMAGE));
        verify(encoder, times(0)).encode(any());
    }

    private ImageVariantProcessor processor(CwebpEncoder encoder, Duration timeout) {
        return processor(new ImageValidator(), encoder, timeout);
    }

    private ImageVariantProcessor processor(ImageValidator validator,
                                            CwebpEncoder encoder,
                                            Duration timeout) {
        return new ImageVariantProcessor(
                validator,
                encoder,
                timeout,
                new Semaphore(ImageVariantProcessor.MAX_CONCURRENT_PROCESSING, true));
    }

    private CwebpEncoder encoderReturningWebp() {
        CwebpEncoder encoder = mock(CwebpEncoder.class);
        when(encoder.encode(any())).thenReturn(ENCODED_WEBP);
        return encoder;
    }

    private MultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("image", name, contentType, bytes);
    }

    private byte[] encodedImage(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try {
            return encodedImage(image, format);
        } finally {
            image.flush();
        }
    }

    private byte[] encodedImage(BufferedImage image, String format) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("Missing ImageIO writer: " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test image", exception);
        }
    }

    private BufferedImage numberedImage() {
        return numberedImage(3, 2);
    }

    private BufferedImage numberedImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int value = 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, value++);
            }
        }
        return image;
    }

    private int[][] pixelValues(BufferedImage image) {
        int[][] values = new int[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                values[y][x] = image.getRGB(x, y) & 0x00FF_FFFF;
            }
        }
        return values;
    }

    private byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] exif = new byte[]{
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2A,
                0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12,
                0x00, 0x03,
                0x00, 0x00, 0x00, 0x01,
                0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] result = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }

    private byte[] headerOnlyJpeg() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xC0,
                0x00, 0x11,
                0x08,
                0x00, 0x02,
                0x00, 0x03,
                0x03,
                0x01, 0x11, 0x00,
                0x02, 0x11, 0x00,
                0x03, 0x11, 0x00,
                (byte) 0xFF, (byte) 0xD9
        };
    }

    private record Dimensions(int width, int height) {
    }
}
