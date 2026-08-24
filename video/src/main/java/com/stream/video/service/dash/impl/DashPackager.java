package com.stream.video.service.dash.impl;

import com.stream.video.config.HlsProperties;
import com.stream.video.service.ffmpeg.FfmpegRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Packages one source file as MPEG-DASH: an MPD manifest describing every quality level,
 * and fragmented-mp4 segments the player fetches as it goes.
 *
 * <p>The output is flat, unlike HLS: ffmpeg numbers the representations rather than naming
 * directories after them, so a 360p/240p ladder with audio becomes streams 0 to 3.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashPackager {

    public static final String MANIFEST = "manifest.mpd";
    public static final String MANIFEST_CONTENT_TYPE = "application/dash+xml";
    public static final String SEGMENT_SUFFIX = ".m4s";
    public static final String SEGMENT_CONTENT_TYPE = "video/mp4";

    /** The names ffmpeg's default templates produce, and the only ones playback serves. */
    public static final Pattern SEGMENT_NAME =
            Pattern.compile("(init-stream\\d+|chunk-stream\\d+-\\d{5})\\.m4s");

    // The renditions and encode settings are the same ladder HLS uses; only the muxer differs.
    private final HlsProperties properties;
    private final FfmpegRunner runner;

    public FfmpegRunner.Result packageVideo(Path source, Path videoDir) throws IOException, InterruptedException {
        FfmpegRunner.Result result = runner.run(command(source, videoDir), properties.processingTimeout());
        if (result.succeeded()) {
            log.info("Packaged {} as DASH into {}", source.getFileName(), videoDir);
        }
        return result;
    }

    List<String> command(Path source, Path videoDir) {
        List<HlsProperties.Rendition> renditions = properties.renditions();
        int segmentSeconds = properties.segmentDurationSeconds();

        List<String> command = new ArrayList<>(List.of(
                properties.ffmpegBinary(),
                "-hide_banner",
                "-nostdin",
                "-y",
                "-loglevel", "warning",
                "-i", source.toString()
        ));

        // One video and one audio stream per rendition, all decoded from the same input.
        for (int i = 0; i < renditions.size(); i++) {
            command.addAll(List.of("-map", "0:v:0", "-map", "0:a:0"));
        }

        command.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-c:a", "aac"));

        for (int i = 0; i < renditions.size(); i++) {
            HlsProperties.Rendition rendition = renditions.get(i);
            command.addAll(List.of(
                    "-filter:v:" + i, "scale=-2:" + rendition.height(),
                    "-b:v:" + i, rendition.videoBitrateKbps() + "k",
                    "-b:a:" + i, rendition.audioBitrateKbps() + "k"
            ));
        }

        command.addAll(List.of(
                // Segments must start on a keyframe at the same timestamps in every
                // representation, which is what lets the player switch quality cleanly.
                "-force_key_frames", "expr:gte(t,n_forced*" + segmentSeconds + ")",

                // Video representations go in one adaptation set and audio in another, so
                // the player switches video quality without re-picking an audio track.
                "-adaptation_sets", adaptationSets(renditions),

                "-f", "dash",
                "-seg_duration", String.valueOf(segmentSeconds),
                // SegmentTemplate + SegmentTimeline: the manifest describes the segments by
                // pattern and duration instead of listing every URL.
                "-use_template", "1",
                "-use_timeline", "1",

                videoDir.resolve(MANIFEST).toString()
        ));

        return command;
    }

    /** Groups the mapped streams: the even ones are video, the odd ones their audio. */
    private String adaptationSets(List<HlsProperties.Rendition> renditions) {
        String video = IntStream.range(0, renditions.size())
                .map(i -> i * 2)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        String audio = IntStream.range(0, renditions.size())
                .map(i -> i * 2 + 1)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        return "id=0,streams=" + video + " id=1,streams=" + audio;
    }
}
