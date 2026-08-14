package com.stream.video.config;

import com.stream.video.service.impl.FfmpegRunner;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

/**
 * Verifies at boot that HLS can actually run: the roots exist and both binaries are on PATH.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HlsStartupCheck {

    private static final Duration VERSION_PROBE_TIMEOUT = Duration.ofSeconds(10);

    private final HlsProperties properties;
    private final FfmpegRunner runner;

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
        FfmpegRunner.Result result;
        try {
            result = runner.run(List.of(binary, "-version"), VERSION_PROBE_TIMEOUT);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "HLS needs '" + binary + "' but it could not be started. Install it, or point "
                            + "the hls.*-binary property at its absolute path.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing " + binary, e);
        }

        if (!result.succeeded()) {
            throw new IllegalStateException(
                    binary + " -version " + result.describeFailure() + ": " + result.output());
        }
        return result.output().lines().findFirst().orElse(binary + " (no version reported)");
    }
}
