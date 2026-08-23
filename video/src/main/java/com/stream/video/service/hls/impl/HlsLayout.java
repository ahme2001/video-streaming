package com.stream.video.service.hls.impl;

import com.stream.video.config.AwsProperties;
import com.stream.video.config.HlsProperties;
import com.stream.video.service.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class HlsLayout {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String INCOMPLETE_SUFFIX = ".tmp";

    private final HlsProperties properties;
    private final AwsProperties awsProperties;
    private final ObjectStorage objectStorage;

    public String outputPrefix(String videoId) {
        return awsProperties.s3().hlsPrefix() + videoId + "/";
    }
    public String masterPlaylistKey(String videoId) {
        return outputPrefix(videoId) + HlsPackager.MASTER_PLAYLIST;
    }
    public String mediaPlaylistKey(String videoId, String rendition) {
        return outputPrefix(videoId) + rendition + "/" + HlsPackager.PLAYLIST_NAME;
    }

    public String segmentKey(String videoId, String rendition, String segment) {
        return outputPrefix(videoId) + rendition + "/" + segment;
    }

    /** ffmpeg can only write to a local path, so it packages into here first. */
    public Path newStagingDir(String videoId) {
        return properties.workDir().resolve(videoId + "-" + UUID.randomUUID());
    }

    /**
     * Where the source object is downloaded to before ffmpeg reads it. The encoder seeks
     * all over its input, which it cannot do over a stream, so this one file is local.
     */
    public Path newSourceFile(String videoId, String key) {
        int dot = key.lastIndexOf('.');
        String extension = dot < 0 ? "" : key.substring(dot);
        return properties.workDir().resolve(videoId + "-source-" + UUID.randomUUID() + extension);
    }

    /**
     * Uploads the segments ffmpeg has finished, and removes each one from disk. Safe to
     * call while the encode is still running: a segment still being written is named
     * {@code .ts.tmp} and only gets its real name once it is complete.
     */
    public void uploadFinishedSegments(Path stagingDir, String videoId) throws IOException {
        upload(stagingDir, videoId, file -> file.toString().endsWith(HlsPackager.SEGMENT_SUFFIX));
    }

    /** Uploads whatever the encode left behind: the last segments and the playlists. */
    public void uploadRemaining(Path stagingDir, String videoId) throws IOException {
        upload(stagingDir, videoId, file -> !file.toString().endsWith(INCOMPLETE_SUFFIX));
    }

    private void upload(Path stagingDir, String videoId, Predicate<Path> finished) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(stagingDir)) {
            files = walk.filter(Files::isRegularFile).filter(finished).toList();
        }

        for (Path file : files) {
            // The directory layout ffmpeg produced becomes the key, so the relative URIs
            // inside the playlists still resolve.
            String relative = stagingDir.relativize(file).toString().replace(File.separatorChar, '/');
            objectStorage.upload(outputPrefix(videoId) + relative, file, contentTypeFor(file));
            Files.delete(file);
        }
    }

    public void deleteOutput(String videoId) {
        objectStorage.deleteByPrefix(outputPrefix(videoId));
    }

    public void deleteQuietly(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            // Leftovers cost disk space but nothing else; failing here would mask the real error.
            log.warn("Could not delete {}", path, e);
        }
    }

    private String contentTypeFor(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(HlsPackager.PLAYLIST_SUFFIX)) {
            return HlsPackager.PLAYLIST_CONTENT_TYPE;
        }
        if (name.endsWith(HlsPackager.SEGMENT_SUFFIX)) {
            return HlsPackager.SEGMENT_CONTENT_TYPE;
        }
        return DEFAULT_CONTENT_TYPE;
    }
}
