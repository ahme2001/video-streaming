package com.stream.video.service.dash.impl;

import com.stream.video.config.HlsExecutorConfig;
import com.stream.video.exception.NotFoundException;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.dash.DashProcessingService;
import com.stream.video.service.ffmpeg.FfmpegRunner;
import com.stream.video.service.storage.ObjectStorage;
import com.stream.video.service.storage.impl.VideoObjectLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@Slf4j
public class DashProcessingServiceImpl implements DashProcessingService {

    /** Matches the dashError column, which the failure message must fit into. */
    private static final int MAX_ERROR_LENGTH = 2000;

    private final VideoRepository videoRepository;
    private final VideoObjectLocator objectLocator;
    private final ObjectStorage objectStorage;
    private final DashPackager packager;
    private final DashLayout layout;
    private final ThreadPoolTaskExecutor executor;

    public DashProcessingServiceImpl(VideoRepository videoRepository, VideoObjectLocator objectLocator,
                                     ObjectStorage objectStorage, DashPackager packager, DashLayout layout,
                                     @Qualifier(HlsExecutorConfig.HLS_EXECUTOR) ThreadPoolTaskExecutor executor) {
        this.videoRepository = videoRepository;
        this.objectLocator = objectLocator;
        this.objectStorage = objectStorage;
        this.packager = packager;
        this.layout = layout;
        this.executor = executor;
    }

    @Override
    public void submit(String videoId) {
        try {
            // The same single-threaded executor as HLS: ffmpeg already uses every core, so
            // running the two packagings at once would only make them compete.
            executor.execute(() -> process(videoId));
        } catch (TaskRejectedException e) {
            log.error("The packaging queue is full, {} will not be packaged for DASH", videoId);
            fail(videoId, "the packaging queue was full");
        }
    }

    private void process(String videoId) {
        log.info("Packaging video {} for DASH", videoId);
        videoRepository.markDashProcessing(videoId);

        Path stagingDir = layout.newStagingDir(videoId);
        Path source = null;
        try {
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new NotFoundException("video with following id not found: " + videoId));

            String key = objectLocator.key(videoId, video.getFilePath());
            source = layout.newSourceFile(videoId, key);
            objectStorage.downloadTo(key, source);
            log.debug("Downloaded {} to {}", key, source);

            layout.deleteOutput(videoId);

            java.nio.file.Files.createDirectories(stagingDir);
            FfmpegRunner.Result result = packager.packageVideo(source, stagingDir);
            if (!result.succeeded()) {
                fail(videoId, "ffmpeg " + result.describeFailure() + ": " + result.output());
                return;
            }

            layout.uploadOutput(stagingDir, videoId);
            videoRepository.markDashReady(videoId);
            log.info("Video {} is ready for DASH playback", videoId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("DASH packaging of {} was interrupted", videoId);
            fail(videoId, "packaging was interrupted");
        } catch (Exception e) {
            log.error("DASH packaging of {} failed", videoId, e);
            fail(videoId, e.toString());
        } finally {
            layout.deleteQuietly(stagingDir);
            if (source != null) {
                layout.deleteQuietly(source);
            }
        }
    }

    private void fail(String videoId, String error) {
        videoRepository.markDashFailed(videoId, truncate(error));
    }

    private String truncate(String error) {
        String stripped = error.strip();
        return stripped.length() <= MAX_ERROR_LENGTH
                ? stripped
                : stripped.substring(stripped.length() - MAX_ERROR_LENGTH);
    }
}
