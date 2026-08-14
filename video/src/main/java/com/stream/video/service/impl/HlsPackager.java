package com.stream.video.service.impl;

import com.stream.video.config.HlsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Turns one source file into a playlist and its segments.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HlsPackager {

    public static final String PLAYLIST_NAME = "index.m3u8";
    private static final String SEGMENT_PATTERN = "segment_%05d.ts";

    private final HlsProperties properties;
    private final FfmpegRunner runner;


    public FfmpegRunner.Result packageVideo(Path source, Path videoDir) throws IOException, InterruptedException {
        Path renditionDir = videoDir.resolve(properties.defaultRendition());
        Files.createDirectories(renditionDir);

        FfmpegRunner.Result result = runner.run(command(source, renditionDir), properties.processingTimeout());
        if (result.succeeded()) {
            log.info("Packaged {} into {}", source.getFileName(), renditionDir);
        }
        return result;
    }

    List<String> command(Path source, Path renditionDir) {
        int segmentSeconds = properties.segmentDurationSeconds();

        return List.of(
                properties.ffmpegBinary(),
                "-hide_banner",
                // ffmpeg reads stdin for interactive keys; without this it can sit waiting on it
                "-nostdin",
                // without -y an existing output file makes ffmpeg ask "Overwrite? [y/N]" and block
                "-y",
                "-loglevel", "warning",
                "-i", source.toString(),

                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",

                // A segment must open on a keyframe, so keyframes are forced onto the segment
                // boundaries. Expressed in seconds rather than as a GOP length in frames, which
                // would have to be recomputed for every source frame rate.
                "-force_key_frames", "expr:gte(t,n_forced*" + segmentSeconds + ")",

                "-f", "hls",
                "-hls_time", String.valueOf(segmentSeconds),
                // keeps every segment in the playlist and appends EXT-X-ENDLIST; the default is
                // a live sliding window that would drop the start of the video
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", renditionDir.resolve(SEGMENT_PATTERN).toString(),

                renditionDir.resolve(PLAYLIST_NAME).toString()
        );
    }
}
