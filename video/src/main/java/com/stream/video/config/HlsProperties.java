package com.stream.video.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "hls")
public record HlsProperties(

        /**
         * Scratch space: the downloaded source, and each segment for the moment between
         * ffmpeg finishing it and it being uploaded to S3.
         */
        @NotNull Path workDir,

        @Positive int segmentDurationSeconds,

        @NotEmpty @Valid List<Rendition> renditions,

        @NotBlank String ffmpegBinary,

        @NotNull Duration processingTimeout
) {

    /**
     * One quality level. The name doubles as the directory it is packaged into and as the
     * path segment players ask for.
     */
    public record Rendition(
            @NotBlank String name,
            @Positive int height,
            @Positive int videoBitrateKbps,
            @Positive int audioBitrateKbps
    ) {
    }

    public HlsProperties {
        workDir = absolute(workDir);
    }

    private static Path absolute(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    public boolean hasRendition(String name) {
        return renditions.stream().anyMatch(rendition -> rendition.name().equals(name));
    }
}
