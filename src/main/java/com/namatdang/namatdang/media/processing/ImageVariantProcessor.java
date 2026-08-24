package com.namatdang.namatdang.media.processing;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import com.namatdang.namatdang.media.ImageVariant;
import com.namatdang.namatdang.media.processing.ImageValidator.ValidatedImage;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageVariantProcessor {

    static final int MAX_CONCURRENT_PROCESSING = 1;
    private static final int MAX_IMAGE_EDGE = 10_000;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;

    private final ImageValidator imageValidator;
    private final CwebpEncoder encoder;
    private final Duration acquireTimeout;
    private final Semaphore processingSlots;

    @Autowired
    public ImageVariantProcessor(ImageValidator imageValidator,
                                 CwebpEncoder encoder,
                                 @Value("${media.images.processor.acquire-timeout:30s}") Duration acquireTimeout) {
        this(imageValidator, encoder, acquireTimeout, new Semaphore(MAX_CONCURRENT_PROCESSING, true));
    }

    ImageVariantProcessor(ImageValidator imageValidator,
                          CwebpEncoder encoder,
                          Duration acquireTimeout,
                          Semaphore processingSlots) {
        if (acquireTimeout == null || acquireTimeout.isZero() || acquireTimeout.isNegative()) {
            throw new IllegalArgumentException("Image processor acquire timeout must be positive");
        }
        this.imageValidator = imageValidator;
        this.encoder = encoder;
        this.acquireTimeout = acquireTimeout;
        this.processingSlots = processingSlots;
    }

    public ImageVariantSet process(MultipartFile file) {
        boolean acquired = acquireProcessingSlot();
        try {
            ValidatedImage validatedImage = imageValidator.validate(file);
            if (validatedImage.bytes() == null
                    || validatedImage.bytes().length == 0
                    || validatedImage.bytes().length > ImageValidator.MAX_IMAGE_SIZE_BYTES
                    || !isSupportedSourceType(validatedImage)) {
                throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
            }
            int orientation = exifOrientation(validatedImage.bytes());
            BufferedImage decoded = decode(validatedImage, orientation);
            BufferedImage oriented = null;
            try {
                oriented = applyOrientation(decoded, orientation);
                if (oriented != decoded) {
                    decoded.flush();
                }
                return createVariants(oriented);
            } finally {
                if (oriented != null) {
                    oriented.flush();
                } else {
                    decoded.flush();
                }
            }
        } finally {
            if (acquired) {
                processingSlots.release();
            }
        }
    }

    private boolean isSupportedSourceType(ValidatedImage image) {
        if (image.contentType() == null || image.extension() == null) {
            return false;
        }
        return switch (image.contentType()) {
            case "image/jpeg" -> "jpg".equals(image.extension());
            case "image/png" -> "png".equals(image.extension());
            case "image/webp" -> "webp".equals(image.extension());
            default -> false;
        };
    }

    private boolean acquireProcessingSlot() {
        try {
            if (!processingSlots.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BusinessLogicException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE,
                        new IllegalStateException("Timed out waiting for an image processing slot"));
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessLogicException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE, exception);
        }
    }

    private BufferedImage decode(ValidatedImage image, int orientation) {
        try (ImageInputStream stream = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(image.bytes()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                ensureAllowedDimensions(width, height);
                DecodePlan decodePlan = decodePlan(width, height, orientation);
                ImageReadParam readParameters = reader.getDefaultReadParam();
                Crop sourceRegion = decodePlan.sourceRegion();
                readParameters.setSourceRegion(new Rectangle(
                        sourceRegion.x(),
                        sourceRegion.y(),
                        sourceRegion.width(),
                        sourceRegion.height()));
                if (decodePlan.subsampling() > 1) {
                    readParameters.setSourceSubsampling(
                            decodePlan.subsampling(), decodePlan.subsampling(), 0, 0);
                }
                BufferedImage decoded = reader.read(0, readParameters);
                if (decoded == null
                        || decoded.getWidth() != decodePlan.decodedWidth()
                        || decoded.getHeight() != decodePlan.decodedHeight()) {
                    if (decoded != null) {
                        decoded.flush();
                    }
                    throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (BusinessLogicException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE, exception);
        }
    }

    static DecodePlan decodePlan(int width, int height, int orientation) {
        boolean swapsAxes = orientation >= 5;
        int minimumX = width;
        int minimumY = height;
        int maximumX = 0;
        int maximumY = 0;
        int subsampling = Integer.MAX_VALUE;

        for (ImageVariant variant : ImageVariant.values()) {
            int rawTargetWidth = swapsAxes ? variant.targetHeight() : variant.targetWidth();
            int rawTargetHeight = swapsAxes ? variant.targetWidth() : variant.targetHeight();
            Crop crop = centerCrop(width, height, rawTargetWidth, rawTargetHeight);
            minimumX = Math.min(minimumX, crop.x());
            minimumY = Math.min(minimumY, crop.y());
            maximumX = Math.max(maximumX, crop.x() + crop.width());
            maximumY = Math.max(maximumY, crop.y() + crop.height());
            int qualityPreservingSubsampling = Math.max(
                    1,
                    Math.min(crop.width() / rawTargetWidth, crop.height() / rawTargetHeight));
            subsampling = Math.min(subsampling, qualityPreservingSubsampling);
        }

        Crop sourceRegion = new Crop(
                minimumX,
                minimumY,
                maximumX - minimumX,
                maximumY - minimumY);
        return new DecodePlan(
                sourceRegion,
                subsampling,
                divideRoundingUp(sourceRegion.width(), subsampling),
                divideRoundingUp(sourceRegion.height(), subsampling));
    }

    private static int divideRoundingUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private void ensureAllowedDimensions(int width, int height) {
        if (width <= 0 || height <= 0
                || width > MAX_IMAGE_EDGE || height > MAX_IMAGE_EDGE
                || (long) width * height > MAX_IMAGE_PIXELS) {
            throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
        }
    }

    private ImageVariantSet createVariants(BufferedImage source) {
        EnumMap<ImageVariant, ProcessedImage> variants = new EnumMap<>(ImageVariant.class);

        for (ImageVariant variant : ImageVariant.values()) {
            BufferedImage rendered = renderVariant(source, variant);
            try {
                variants.put(variant, new ProcessedImage(
                        encoder.encode(rendered),
                        "image/webp",
                        rendered.getWidth(),
                        rendered.getHeight()));
            } finally {
                if (rendered != source) {
                    rendered.flush();
                }
            }
        }
        return new ImageVariantSet(variants);
    }

    static BufferedImage renderVariant(BufferedImage source, ImageVariant variant) {
        Crop crop = centerCrop(
                source.getWidth(),
                source.getHeight(),
                variant.targetWidth(),
                variant.targetHeight());
        Dimensions dimensions = outputDimensions(crop, variant);
        if (crop.x() == 0 && crop.y() == 0
                && crop.width() == source.getWidth() && crop.height() == source.getHeight()
                && dimensions.width() == source.getWidth() && dimensions.height() == source.getHeight()) {
            return source;
        }

        BufferedImage rendered = newCanvas(source, dimensions.width(), dimensions.height());
        Graphics2D graphics = rendered.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(
                    source,
                    0,
                    0,
                    dimensions.width(),
                    dimensions.height(),
                    crop.x(),
                    crop.y(),
                    crop.x() + crop.width(),
                    crop.y() + crop.height(),
                    null);
        } finally {
            graphics.dispose();
        }
        return rendered;
    }

    static Crop centerCrop(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        long sourceRatio = (long) sourceWidth * targetHeight;
        long targetRatio = (long) sourceHeight * targetWidth;
        if (sourceRatio > targetRatio) {
            int cropWidth = Math.min(
                    sourceWidth,
                    Math.max(1, (int) Math.round((double) sourceHeight * targetWidth / targetHeight)));
            return new Crop((sourceWidth - cropWidth) / 2, 0, cropWidth, sourceHeight);
        }
        if (sourceRatio < targetRatio) {
            int cropHeight = Math.min(
                    sourceHeight,
                    Math.max(1, (int) Math.round((double) sourceWidth * targetHeight / targetWidth)));
            return new Crop(0, (sourceHeight - cropHeight) / 2, sourceWidth, cropHeight);
        }
        return new Crop(0, 0, sourceWidth, sourceHeight);
    }

    private static Dimensions outputDimensions(Crop crop, ImageVariant variant) {
        if (crop.width() >= variant.targetWidth() && crop.height() >= variant.targetHeight()) {
            return new Dimensions(variant.targetWidth(), variant.targetHeight());
        }

        double scale = Math.min(
                1.0,
                Math.min(
                        (double) variant.targetWidth() / crop.width(),
                        (double) variant.targetHeight() / crop.height()));
        return new Dimensions(
                Math.max(1, (int) Math.round(crop.width() * scale)),
                Math.max(1, (int) Math.round(crop.height() * scale)));
    }

    static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation == 1) {
            return source;
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        boolean swapsAxes = orientation >= 5;
        BufferedImage destination = newCanvas(
                source,
                swapsAxes ? sourceHeight : sourceWidth,
                swapsAxes ? sourceWidth : sourceHeight);

        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, sourceWidth, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, sourceWidth, sourceHeight);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, sourceHeight);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, sourceHeight, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, sourceHeight, sourceWidth);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, sourceWidth);
            default -> throw new BusinessLogicException(ExceptionCode.INVALID_IMAGE);
        };

        Graphics2D graphics = destination.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return destination;
    }

    static int exifOrientation(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(input);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory == null) {
                return 1;
            }
            Integer orientation = directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return orientation != null && orientation >= 1 && orientation <= 8 ? orientation : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static BufferedImage newCanvas(BufferedImage source, int width, int height) {
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        return new BufferedImage(width, height, imageType);
    }

    record Crop(int x, int y, int width, int height) {
    }

    record DecodePlan(Crop sourceRegion, int subsampling, int decodedWidth, int decodedHeight) {
    }

    private record Dimensions(int width, int height) {
    }
}
