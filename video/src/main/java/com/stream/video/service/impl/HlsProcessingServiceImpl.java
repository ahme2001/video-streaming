package com.stream.video.service.impl;

import com.stream.video.config.HlsExecutorConfig;
import com.stream.video.exception.NotFoundException;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.HlsProcessingService;
import com.stream.video.service.ffmpeg.FfmpegRunner;
import com.stream.video.service.storage.ObjectStorage;
import com.stream.video.service.storage.impl.VideoObjectLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class HlsProcessingServiceImpl implements HlsProcessingService {

    /** Matches the hlsError column, which the failure message must fit into. */
    private static final int MAX_ERROR_LENGTH = 2000;

    /** How often finished segments are swept off the disk and into S3. */
    private static final Duration UPLOAD_INTERVAL = Duration.ofSeconds(1);

    private final VideoRepository videoRepository;
    private final VideoObjectLocator objectLocator;
    private final ObjectStorage objectStorage;
    private final HlsPackager packager;
    private final HlsLayout layout;
    private final ThreadPoolTaskExecutor executor;

    public HlsProcessingServiceImpl(VideoRepository videoRepository, VideoObjectLocator objectLocator,
                                    ObjectStorage objectStorage, HlsPackager packager, HlsLayout layout,
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
        Path source = null;
        try {
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new NotFoundException("video with following id not found: " + videoId));

            String key = objectLocator.key(videoId, video.getFilePath());
            source = layout.newSourceFile(videoId, key);
            objectStorage.downloadTo(key, source);
            log.debug("Downloaded {} to {}", key, source);

            // Anything an earlier attempt left under the prefix has to go before this one starts writing into it.
            layout.deleteOutput(videoId);

            FfmpegRunner.Result result = packageAndUpload(videoId, source, stagingDir);
            if (!result.succeeded()) {
                fail(videoId, "ffmpeg " + result.describeFailure() + ": " + result.output());
                return;
            }

            // Everything is in S3 by now; this is what makes it visible to players.
            videoRepository.markReady(videoId);
            log.info("Video {} is ready for HLS playback", videoId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Packaging of {} was interrupted", videoId);
            fail(videoId, "packaging was interrupted");
        } catch (Exception e) {
            log.error("Packaging of {} failed", videoId, e);
            fail(videoId, e.toString());
        } finally {
            layout.deleteQuietly(stagingDir);
            if (source != null) {
                layout.deleteQuietly(source);
            }
        }
    }

    /**
     * Encodes, with an uploader running alongside so each finished segment is in S3 and
     * off the disk while ffmpeg is still working on the next one. Only the segment being
     * written is ever held locally.
     */
    private FfmpegRunner.Result packageAndUpload(String videoId, Path source, Path stagingDir)
            throws IOException, InterruptedException {

        // The uploader starts watching this directory immediately, so it has to exist before ffmpeg gets around to creating it.
        Files.createDirectories(stagingDir);

        AtomicBoolean encoding = new AtomicBoolean(true);
        AtomicReference<Exception> uploadFailure = new AtomicReference<>();
        // Declare thread which responsible for uploading process
        Thread uploader = Thread.ofVirtual().name("hls-upload-" + videoId).start(() -> {
            try {
                while (encoding.get()) {
                    layout.uploadFinishedSegments(stagingDir, videoId);
                    Thread.sleep(UPLOAD_INTERVAL);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Recorded rather than thrown: nothing is waiting on this thread yet, and
                // the encode is stopped by the caller a moment later anyway.
                uploadFailure.set(e);
            }
        });

        FfmpegRunner.Result result;
        try {
            result = packager.packageVideo(source, stagingDir);
        } finally {
            encoding.set(false);
            uploader.join();
        }

        if (uploadFailure.get() != null) {
            throw new IllegalStateException("failed to upload a segment", uploadFailure.get());
        }
        if (result.succeeded()) {
            layout.uploadRemaining(stagingDir, videoId);
        }
        return result;
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
