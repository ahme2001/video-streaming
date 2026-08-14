package com.stream.video.service.impl;

import com.stream.video.config.HlsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Reads metadata out of a media file with ffprobe. not a scan of the file.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MediaProbe {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);

    private final HlsProperties properties;
    private final FfmpegRunner runner;

    /**
     * Asks for duration of video so the answer is a single number on a single line,
     */
    public double durationSeconds(Path file) throws IOException, InterruptedException {
        List<String> command = List.of(
                properties.ffprobeBinary(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.toString()
        );

        FfmpegRunner.Result result = runner.run(command, PROBE_TIMEOUT);
        if (!result.succeeded()) {
            throw new IllegalStateException(
                    "ffprobe " + result.describeFailure() + " for " + file + ": " + result.output());
        }

        String duration = result.output().strip();
        try {
            return Double.parseDouble(duration);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ffprobe reported no usable duration for " + file
                    + " (got '" + duration + "')", e);
        }
    }
}
