package com.stream.video.config;

import com.stream.video.service.ffmpeg.FfmpegRunner;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

/**
 * Verifies at boot that HLS can actually run: the work directory exists,
 * ffmpeg are on PATH, and the bucket of s3 can be reached.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HlsStartupCheck {

    private static final Duration VERSION_PROBE_TIMEOUT = Duration.ofSeconds(10);

    private final HlsProperties properties;
    private final AwsProperties awsProperties;
    private final FfmpegRunner runner;
    private final S3Client s3Client;

    @PostConstruct
    void verifyEnvironment() {
        createWorkDir();
        verifyBucket();
        log.info("HLS work directory: {}", properties.workDir());
        log.info("Using {}", firstLineOf(properties.ffmpegBinary()));
    }

    private void createWorkDir() {
        try {
            Files.createDirectories(properties.workDir());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the HLS work directory", e);
        }
    }

    private void verifyBucket() {
        String bucket = awsProperties.s3().bucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (SdkException e) {
            throw new IllegalStateException(
                    "S3 bucket '" + bucket + "' is not reachable in region " + awsProperties.region()
                            + ". Check AWS_S3_BUCKET, AWS_REGION and the credentials.", e);
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
