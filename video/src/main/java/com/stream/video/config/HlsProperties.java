package com.stream.video.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "hls")
public record HlsProperties(

        @NotNull Path outputRoot,

        @NotNull Path stagingRoot,

        @Positive int segmentDurationSeconds,

        @NotBlank String defaultRendition,

        @NotBlank String ffmpegBinary,

        @NotBlank String ffprobeBinary,

        @NotNull Duration processingTimeout
) {

    public HlsProperties {
        outputRoot = absolute(outputRoot);
        stagingRoot = absolute(stagingRoot);
    }

    private static Path absolute(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }
}
