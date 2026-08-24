package com.namatdang.namatdang.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.namatdang.namatdang.exception.BusinessLogicException;
import com.namatdang.namatdang.exception.ExceptionCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CwebpEncoderTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void invokesExecutableWithoutAShellWithQualityMetadataAndMemoryOptions() throws IOException {
        Path executable = executable("""
                #!/bin/sh
                output=''
                previous=''
                for argument in "$@"; do
                  if [ "$previous" = '-o' ]; then output="$argument"; fi
                  previous="$argument"
                done
                printf 'RIFF\\004\\000\\000\\000WEBP' > "$output"
                """);
        CwebpEncoder encoder = new CwebpEncoder(executable, Duration.ofSeconds(1), temporaryDirectory);
        Path input = Path.of("/tmp/input name.png");
        Path output = Path.of("/tmp/output name.webp");

        List<String> command = encoder.command(input, output);
        byte[] encoded = encoder.encode(new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB));

        assertThat(command).containsSubsequence("-q", "80");
        assertThat(command).containsSubsequence("-metadata", "none");
        assertThat(command).contains("-mt", "-low_memory", input.toString(), output.toString());
        assertThat(command.getFirst()).isEqualTo(executable.toString());
        assertThat(new String(encoded, StandardCharsets.ISO_8859_1)).isEqualTo("RIFF\u0004\u0000\u0000\u0000WEBP");
        assertTemporaryDirectoryIsEmpty();
    }

    @Test
    void mapsNonZeroExitAndBoundsCapturedProcessOutput() throws IOException {
        Path executable = executable("""
                #!/bin/sh
                dd if=/dev/zero bs=1024 count=80 2>/dev/null
                exit 7
                """);
        CwebpEncoder encoder = new CwebpEncoder(executable, Duration.ofSeconds(1), temporaryDirectory);

        assertThatThrownBy(() -> encoder.encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception -> {
                    assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE);
                    assertThat(exception.getCause().getMessage()).contains("code 7", "[truncated]");
                    assertThat(exception.getCause().getMessage().length())
                            .isLessThan(CwebpEncoder.PROCESS_OUTPUT_LIMIT_BYTES + 100);
                });
        assertTemporaryDirectoryIsEmpty();
    }

    @Test
    void terminatesTimedOutProcessAndRemovesTemporaryFiles() throws IOException {
        Path executable = executable("""
                #!/bin/sh
                sleep 5
                """);
        CwebpEncoder encoder = new CwebpEncoder(executable, Duration.ofMillis(50), temporaryDirectory);
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> encoder.encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)))
                .isInstanceOfSatisfying(BusinessLogicException.class, exception -> {
                    assertThat(exception.getExceptionCode()).isEqualTo(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE);
                    assertThat(exception.getCause().getMessage()).contains("timed out");
                });

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
        assertTemporaryDirectoryIsEmpty();
    }

    @Test
    void rejectsRelativeConfiguredExecutablePath() {
        assertThatThrownBy(() -> new CwebpEncoder("bin/cwebp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void installedCwebpProducesADecodableWebp() throws IOException {
        Path executable = installedCwebp();
        Assumptions.assumeTrue(executable != null, "cwebp is not installed in a supported path");
        CwebpEncoder encoder = new CwebpEncoder(executable, Duration.ofSeconds(3), temporaryDirectory);
        BufferedImage source = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);

        byte[] encoded;
        try {
            encoded = encoder.encode(source);
        } finally {
            source.flush();
        }

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        try {
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(16);
            assertThat(decoded.getHeight()).isEqualTo(8);
        } finally {
            if (decoded != null) {
                decoded.flush();
            }
        }
        assertTemporaryDirectoryIsEmpty();
    }

    private Path executable(String script) throws IOException {
        Path path = temporaryDirectory.resolve("fake cwebp " + System.nanoTime());
        Files.writeString(path, script, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }

    private Path installedCwebp() {
        for (String candidate : List.of("/opt/homebrew/bin/cwebp", "/usr/bin/cwebp")) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return path;
            }
        }
        return null;
    }

    private void assertTemporaryDirectoryIsEmpty() throws IOException {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertThat(files.filter(path -> path.getFileName().toString().startsWith("namatdang-image-")))
                    .isEmpty();
        }
    }
}
