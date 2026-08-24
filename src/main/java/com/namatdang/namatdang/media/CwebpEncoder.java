package com.namatdang.namatdang.media;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CwebpEncoder {

    static final int QUALITY = 80;
    static final int PROCESS_OUTPUT_LIMIT_BYTES = 64 * 1024;
    private static final long ENCODED_IMAGE_LIMIT_BYTES = 12L * 1024 * 1024;
    private static final Duration DEFAULT_PROCESS_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TERMINATION_GRACE_PERIOD = Duration.ofMillis(250);
    private static final Logger log = LoggerFactory.getLogger(CwebpEncoder.class);

    private final String executable;
    private final Duration processTimeout;
    private final Path temporaryDirectoryParent;

    @Autowired
    public CwebpEncoder(@Value("${media.images.processor.cwebp-path:}") String configuredExecutable) {
        this(resolveExecutable(configuredExecutable), DEFAULT_PROCESS_TIMEOUT, null);
    }

    CwebpEncoder(Path executable, Duration processTimeout, Path temporaryDirectoryParent) {
        this(executable.toString(), processTimeout, temporaryDirectoryParent);
    }

    private CwebpEncoder(String executable, Duration processTimeout, Path temporaryDirectoryParent) {
        if (!StringUtils.hasText(executable)) {
            throw new IllegalArgumentException("cwebp executable must be configured");
        }
        if (processTimeout == null || processTimeout.isZero() || processTimeout.isNegative()) {
            throw new IllegalArgumentException("cwebp timeout must be positive");
        }
        this.executable = executable;
        this.processTimeout = processTimeout;
        this.temporaryDirectoryParent = temporaryDirectoryParent;
    }

    public byte[] encode(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("Image to encode must have positive dimensions");
        }

        Path workDirectory = null;
        Path input = null;
        Path output = null;
        try {
            workDirectory = createWorkDirectory();
            input = workDirectory.resolve("input.png");
            output = workDirectory.resolve("output.webp");
            writeMetadataFreePng(image, input);
            executeEncoder(input, output);
            return readEncodedImage(output);
        } catch (BusinessLogicException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw processingUnavailable("Failed to prepare or read cwebp output", exception);
        } finally {
            deleteQuietly(output);
            deleteQuietly(input);
            deleteQuietly(workDirectory);
        }
    }

    List<String> command(Path input, Path output) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(
                "-quiet",
                "-q", Integer.toString(QUALITY),
                "-metadata", "none",
                "-mt",
                "-low_memory",
                input.toString(),
                "-o", output.toString()));
        return List.copyOf(command);
    }

    private Path createWorkDirectory() throws IOException {
        if (temporaryDirectoryParent == null) {
            return Files.createTempDirectory("namatdang-image-");
        }
        return Files.createTempDirectory(temporaryDirectoryParent, "namatdang-image-");
    }

    private void writeMetadataFreePng(BufferedImage image, Path input) throws IOException {
        try (OutputStream stream = Files.newOutputStream(input)) {
            if (!ImageIO.write(image, "png", stream)) {
                throw new IOException("PNG ImageIO writer is unavailable");
            }
        }
    }

    private void executeEncoder(Path input, Path output) {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command(input, output));
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
        } catch (IOException exception) {
            throw processingUnavailable("cwebp executable is unavailable", exception);
        }

        AtomicReference<ProcessOutput> processOutput = new AtomicReference<>();
        AtomicReference<IOException> outputFailure = new AtomicReference<>();
        Thread outputReader = Thread.ofVirtual().name("cwebp-output-reader").start(() -> {
            try (InputStream stream = process.getInputStream()) {
                processOutput.set(readBoundedOutput(stream));
            } catch (IOException exception) {
                outputFailure.set(exception);
            }
        });

        try {
            if (!process.waitFor(processTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                joinOutputReader(outputReader);
                throw processingUnavailable("cwebp timed out after " + processTimeout, null);
            }
            joinOutputReader(outputReader);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            outputReader.interrupt();
            Thread.currentThread().interrupt();
            throw processingUnavailable("cwebp execution was interrupted", exception);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        if (outputFailure.get() != null) {
            throw processingUnavailable("Failed to collect cwebp process output", outputFailure.get());
        }
        if (process.exitValue() != 0) {
            ProcessOutput captured = processOutput.get();
            String detail = captured == null ? "" : captured.formatted();
            throw processingUnavailable("cwebp exited with code " + process.exitValue() + detail, null);
        }
    }

    private void terminate(Process process) throws InterruptedException {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(TERMINATION_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(TERMINATION_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void joinOutputReader(Thread outputReader) throws InterruptedException {
        outputReader.join(TERMINATION_GRACE_PERIOD.toMillis());
        if (outputReader.isAlive()) {
            outputReader.interrupt();
            outputReader.join(TERMINATION_GRACE_PERIOD.toMillis());
        }
    }

    private byte[] readEncodedImage(Path output) throws IOException {
        if (!Files.isRegularFile(output)) {
            throw new IOException("cwebp did not create an output file");
        }
        long size = Files.size(output);
        if (size <= 0 || size > ENCODED_IMAGE_LIMIT_BYTES) {
            throw new IOException("cwebp output size is outside the allowed range");
        }
        byte[] bytes = Files.readAllBytes(output);
        if (!isWebp(bytes)) {
            throw new IOException("cwebp output is not a WebP image");
        }
        return bytes;
    }

    private static ProcessOutput readBoundedOutput(InputStream stream) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream(PROCESS_OUTPUT_LIMIT_BYTES);
        byte[] buffer = new byte[4 * 1024];
        boolean truncated = false;
        int read;
        while ((read = stream.read(buffer)) != -1) {
            int remaining = PROCESS_OUTPUT_LIMIT_BYTES - captured.size();
            if (remaining > 0) {
                captured.write(buffer, 0, Math.min(remaining, read));
            }
            if (read > remaining) {
                truncated = true;
            }
        }
        return new ProcessOutput(captured.toString(StandardCharsets.UTF_8), truncated);
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
    }

    private static String resolveExecutable(String configuredExecutable) {
        if (StringUtils.hasText(configuredExecutable)) {
            Path configuredPath = Path.of(configuredExecutable.strip());
            if (!configuredPath.isAbsolute()) {
                throw new IllegalArgumentException("Configured cwebp path must be absolute");
            }
            return configuredPath.normalize().toString();
        }

        for (String candidate : List.of("/opt/homebrew/bin/cwebp", "/usr/bin/cwebp")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "/usr/bin/cwebp";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to remove temporary image processing path: {}", path, exception);
        }
    }

    private BusinessLogicException processingUnavailable(String detail, Throwable cause) {
        IllegalStateException processingFailure = new IllegalStateException(detail, cause);
        return new BusinessLogicException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE, processingFailure);
    }

    private record ProcessOutput(String text, boolean truncated) {

        private String formatted() {
            if (!StringUtils.hasText(text) && !truncated) {
                return "";
            }
            return ": " + text.strip() + (truncated ? " [truncated]" : "");
        }
    }
}
