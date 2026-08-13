package com.stream.video.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Verifies at boot that HLS can actually run: the roots exist and both binaries are on PATH.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HlsStartupCheck {

    private static final Duration VERSION_PROBE_TIMEOUT = Duration.ofSeconds(10);

    private final HlsProperties properties;

    @PostConstruct
    void verifyEnvironment() {
        createRoots();
        log.info("HLS output root: {}", properties.outputRoot());
        log.info("HLS staging root: {}", properties.stagingRoot());
        log.info("Using {}", firstLineOf(properties.ffmpegBinary()));
        log.info("Using {}", firstLineOf(properties.ffprobeBinary()));
    }

    private void createRoots() {
        try {
            // The HLS muxer does not create missing parent directories, it just fails.
            Files.createDirectories(properties.outputRoot());
            Files.createDirectories(properties.stagingRoot());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the HLS directories", e);
        }
    }


    private String firstLineOf(String binary) {
        Process process;
        try {
            process = new ProcessBuilder(binary, "-version")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "HLS needs '" + binary + "' but it could not be started. Install it, or point "
                            + "the hls.*-binary property at its absolute path.", e);
        }

        try {
            String output;
            try (InputStream stdout = process.getInputStream()) {
                output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (!process.waitFor(VERSION_PROBE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(binary + " -version did not finish within "
                        + VERSION_PROBE_TIMEOUT.toSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        binary + " -version exited with " + process.exitValue() + ": " + output.strip());
            }
            return output.lines().findFirst().orElse(binary + " (no version reported)");

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the version of " + binary, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing " + binary, e);
        }
    }
}
