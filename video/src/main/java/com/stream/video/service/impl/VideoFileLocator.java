package com.stream.video.service.impl;

import com.stream.video.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves a stored file against the configured folder. Only the file name of the stored
 * value is used, so a path that somehow made it into the database cannot walk out of the
 * video folder.
 */
@Service
public class VideoFileLocator {

    @Value("${video.folder}")
    private String dir;

    public Path locate(String videoId, String storedPath) {
        Path baseDir = Path.of(dir).toAbsolutePath().normalize();
        Path fileName = storedPath == null ? null : Path.of(storedPath).getFileName();
        if (fileName == null) {
            throw new NotFoundException("video file is missing for id: " + videoId);
        }

        Path file = baseDir.resolve(fileName).normalize();
        if (!file.startsWith(baseDir) || !Files.isReadable(file)) {
            throw new NotFoundException("video file is missing for id: " + videoId);
        }
        return file;
    }
}
