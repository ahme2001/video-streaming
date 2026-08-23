package com.stream.video.service.hls.impl;

import com.stream.video.config.HlsProperties;
import com.stream.video.service.ffmpeg.FfmpegRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns one source file into a playlist per quality level.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HlsPackager {

    public static final String MASTER_PLAYLIST = "master.m3u8";
    public static final String PLAYLIST_NAME = "index.m3u8";
    public static final String PLAYLIST_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    public static final String SEGMENT_CONTENT_TYPE = "video/mp2t";
    public static final String PLAYLIST_SUFFIX = ".m3u8";
    public static final String SEGMENT_SUFFIX = ".ts";
    private static final String SEGMENT_PATTERN = "segment_%05d.ts";
    /** The names the pattern above can produce. */
    public static final java.util.regex.Pattern SEGMENT_NAME =
            java.util.regex.Pattern.compile("segment_\\d{5}\\.ts");

    private final HlsProperties properties;
    private final FfmpegRunner runner;

    public FfmpegRunner.Result packageVideo(Path source, Path videoDir) throws IOException, InterruptedException {
        // ffmpeg writes into the per-rendition directories but does not create them.
        for (HlsProperties.Rendition rendition : properties.renditions()) {
            Files.createDirectories(videoDir.resolve(rendition.name()));
        }

        FfmpegRunner.Result result = runner.run(command(source, videoDir), properties.processingTimeout());
        if (result.succeeded()) {
            log.info("Packaged {} into {}", source.getFileName(), videoDir);
        }
        return result;
    }

    List<String> command(Path source, Path videoDir) {
        List<HlsProperties.Rendition> renditions = properties.renditions();
        int segmentSeconds = properties.segmentDurationSeconds();

        List<String> command = new ArrayList<>(List.of(
                properties.ffmpegBinary(),
                "-hide_banner",
                // ffmpeg reads stdin for interactive keys; without this it can sit waiting on it
                "-nostdin",
                // without -y an existing output makes ffmpeg ask "Overwrite? [y/N]" and block
                "-y",
                "-loglevel", "warning",
                "-i", source.toString()
        ));

        // One video and one audio output per rendition, all decoded from the same input.
        for (int i = 0; i < renditions.size(); i++) {
            command.addAll(List.of("-map", "0:v:0", "-map", "0:a:0"));
        }

        command.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-c:a", "aac"));

        for (int i = 0; i < renditions.size(); i++) {
            HlsProperties.Rendition rendition = renditions.get(i);
            command.addAll(List.of(
                    // -2 keeps the width even, which h264 requires, and preserves the aspect ratio
                    "-filter:v:" + i, "scale=-2:" + rendition.height(),
                    "-b:v:" + i, rendition.videoBitrateKbps() + "k",
                    "-b:a:" + i, rendition.audioBitrateKbps() + "k"
            ));
        }

        command.addAll(List.of(
                // A segment must open on a keyframe. Forcing them onto the same timestamps in
                // every rendition is what lets a player switch quality at a segment boundary.
                "-force_key_frames", "expr:gte(t,n_forced*" + segmentSeconds + ")",

                "-var_stream_map", variantMap(renditions),
                "-master_pl_name", MASTER_PLAYLIST,

                "-f", "hls",
                "-hls_time", String.valueOf(segmentSeconds),
                // keeps every segment in the playlist and appends EXT-X-ENDLIST;
                "-hls_playlist_type", "vod",

                // Writes each segment as .ts.tmp and renames it only once it is complete,
                "-hls_flags", "temp_file",

                // %v expands to the rendition name from the variant map
                "-hls_segment_filename", videoDir.resolve("%v").resolve(SEGMENT_PATTERN).toString(),

                videoDir.resolve("%v").resolve(PLAYLIST_NAME).toString()
        ));

        return command;
    }

    /** Tells ffmpeg which output streams belong together, and what to call each variant. */
    private String variantMap(List<HlsProperties.Rendition> renditions) {
        return java.util.stream.IntStream.range(0, renditions.size())
                .mapToObj(i -> "v:" + i + ",a:" + i + ",name:" + renditions.get(i).name())
                .collect(Collectors.joining(" "));
    }
}
