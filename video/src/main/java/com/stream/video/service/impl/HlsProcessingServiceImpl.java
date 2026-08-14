package com.stream.video.service.impl;

import com.stream.video.config.HlsExecutorConfig;
import com.stream.video.exception.NotFoundException;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.HlsProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@Slf4j
public class HlsProcessingServiceImpl implements HlsProcessingService {

    /** Matches the hlsError column, which the failure message must fit into. */
    private static final int MAX_ERROR_LENGTH = 2000;

    private final VideoRepository videoRepository;
    private final VideoFileLocator fileLocator;
    private final MediaProbe mediaProbe;
    private final HlsPackager packager;
    private final HlsLayout layout;
    private final ThreadPoolTaskExecutor executor;

    public HlsProcessingServiceImpl(VideoRepository videoRepository, VideoFileLocator fileLocator,
                                    MediaProbe mediaProbe, HlsPackager packager, HlsLayout layout,
                                    @Qualifier(HlsExecutorConfig.HLS_EXECUTOR) ThreadPoolTaskExecutor executor) {
        this.videoRepository = videoRepository;
        this.fileLocator = fileLocator;
        this.mediaProbe = mediaProbe;
        this.packager = packager;
        this.layout = layout;
        this.executor = executor;
    }

    @Override
    public void submit(String videoId) {
        try {
            executor.execute(() -> process(videoId));
        } catch (TaskRejectedException e) {
            log.error("The packaging queue is full, {} will not be packaged", videoId);
            fail(videoId, "the packaging queue was full");
        }
    }

    private void process(String videoId) {
        log.info("Packaging video {} for HLS", videoId);
        videoRepository.markProcessing(videoId);

        Path stagingDir = layout.newStagingDir(videoId);
        try {
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new NotFoundException("video with following id not found: " + videoId));
            Path source = fileLocator.locate(videoId, video.getFilePath());

            double duration = mediaProbe.durationSeconds(source);
            FfmpegRunner.Result result = packager.packageVideo(source, stagingDir);
            if (!result.succeeded()) {
                fail(videoId, "ffmpeg " + result.describeFailure() + ": " + result.output());
                return;
            }

            // Only now does the output become visible to players.
            layout.publish(stagingDir, videoId);
            videoRepository.markReady(videoId, duration);
            log.info("Video {} is ready for HLS playback ({}s)", videoId, duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Packaging of {} was interrupted", videoId);
            fail(videoId, "packaging was interrupted");
        } catch (Exception e) {
            log.error("Packaging of {} failed", videoId, e);
            fail(videoId, e.toString());
        } finally {
            layout.deleteQuietly(stagingDir);
        }
    }

    private void fail(String videoId, String error) {
        videoRepository.markFailed(videoId, truncate(error));
    }

    private String truncate(String error) {
        String stripped = error.strip();
        return stripped.length() <= MAX_ERROR_LENGTH
                ? stripped
                : stripped.substring(stripped.length() - MAX_ERROR_LENGTH);
    }
}
