package com.stream.video.service.dash.impl;

import com.stream.video.config.AwsProperties;
import com.stream.video.config.HlsProperties;
import com.stream.video.service.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashLayout {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final HlsProperties properties;
    private final AwsProperties awsProperties;
    private final ObjectStorage objectStorage;

    /** The manifest and every segment of one video sit under this prefix. */
    public String outputPrefix(String videoId) {
        return awsProperties.s3().dashPrefix() + videoId + "/";
    }

    public String manifestKey(String videoId) {
        return outputPrefix(videoId) + DashPackager.MANIFEST;
    }

    public String segmentKey(String videoId, String segment) {
        return outputPrefix(videoId) + segment;
    }

    public Path newStagingDir(String videoId) {
        return properties.workDir().resolve(videoId + "-dash-" + UUID.randomUUID());
    }

    public Path newSourceFile(String videoId, String key) {
        int dot = key.lastIndexOf('.');
        String extension = dot < 0 ? "" : key.substring(dot);
        return properties.workDir().resolve(videoId + "-dash-source-" + UUID.randomUUID() + extension);
    }

    public void uploadOutput(Path stagingDir, String videoId) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(stagingDir)) {
            files = walk.filter(Files::isRegularFile)
                    // Manifest last: it is only meaningful once the segments it names exist.
                    .sorted(Comparator.comparing(file -> isManifest(file)))
                    .toList();
        }

        for (Path file : files) {
            objectStorage.upload(segmentKey(videoId, file.getFileName().toString()), file, contentTypeFor(file));
            Files.delete(file);
        }
        log.info("Uploaded {} DASH files for {}", files.size(), videoId);
    }

    /** Clears the output of an earlier attempt, which may have produced more segments. */
    public void deleteOutput(String videoId) {
        objectStorage.deleteByPrefix(outputPrefix(videoId));
    }

    public void deleteQuietly(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Could not delete {}", path, e);
        }
    }

    private static boolean isManifest(Path file) {
        return file.getFileName().toString().endsWith(".mpd");
    }

    private String contentTypeFor(Path file) {
        String name = file.getFileName().toString();
        if (isManifest(file)) {
            return DashPackager.MANIFEST_CONTENT_TYPE;
        }
        if (name.endsWith(DashPackager.SEGMENT_SUFFIX)) {
            return DashPackager.SEGMENT_CONTENT_TYPE;
        }
        return DEFAULT_CONTENT_TYPE;
    }
}
