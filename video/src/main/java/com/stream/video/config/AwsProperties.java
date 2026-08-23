package com.stream.video.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything the application needs to talk to S3. The bucket holds both the uploaded
 * originals and the packaged HLS output, each under its own key prefix.
 */
@Validated
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(

        @NotBlank String region,

        @NotBlank String accessKeyId,

        @NotBlank String secretKey,

        @NotNull @Valid S3 s3
) {

    public record S3(

            @NotBlank String bucket,

            /** Where uploaded originals are stored, e.g. {@code videos/}. */
            @NotBlank String videoPrefix,

            /** Where packaged playlists and segments are stored, e.g. {@code hls/}. */
            @NotBlank String hlsPrefix
    ) {

        public S3 {
            videoPrefix = asPrefix(videoPrefix);
            hlsPrefix = asPrefix(hlsPrefix);
        }

        /**
         * S3 has no directories, only key prefixes, so a missing trailing slash would
         * silently glue the prefix onto the first file name.
         */
        private static String asPrefix(String value) {
            if (value == null || value.isBlank()) {
                return value;
            }
            String trimmed = value.strip();
            return trimmed.endsWith("/") ? trimmed : trimmed + "/";
        }
    }
}
