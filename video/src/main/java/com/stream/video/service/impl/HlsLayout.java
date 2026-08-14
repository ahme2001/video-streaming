package com.stream.video.service.impl;

import com.stream.video.config.HlsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Responsible for persisting segment files after finish processing
 * Or Deleting segment-files which this video failed in processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HlsLayout {

    private final HlsProperties properties;

    public Path outputDir(String videoId) {
        return properties.outputRoot().resolve(videoId);
    }

    public Path newStagingDir(String videoId) {
        return properties.stagingRoot().resolve(videoId + "-" + UUID.randomUUID());
    }

    // Moves a finished staging directory into place.
    public void publish(Path stagingDir, String videoId) throws IOException {
        Path target = outputDir(videoId);

        // ATOMIC_MOVE refuses to overwrite
        Files.move(stagingDir, target, StandardCopyOption.ATOMIC_MOVE);
        log.info("Published HLS output for {} to {}", videoId, target);
    }

    // Delete failed processing video
    public void deleteQuietly(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException e) {
            // Leftovers cost disk space but nothing else; failing here would mask the real error.
            log.warn("Could not delete {}", dir, e);
        }
    }
}
