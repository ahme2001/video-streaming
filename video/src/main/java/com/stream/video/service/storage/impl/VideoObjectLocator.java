package com.stream.video.service.storage.impl;

import com.stream.video.config.AwsProperties;
import com.stream.video.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Turns the name stored on a video row into the S3 key its bytes live under. Only the
 * file name of the stored value is used, so a value that somehow made it into the
 * database cannot point the key outside the video prefix.
 */
@Service
@RequiredArgsConstructor
public class VideoObjectLocator {

    private final AwsProperties properties;

    public String key(String videoId, String storedPath) {
        Path fileName = storedPath == null || storedPath.isBlank()
                ? null
                : Path.of(storedPath).getFileName();

        if (fileName == null || fileName.toString().isBlank()) {
            throw new NotFoundException("video file is missing for id: " + videoId);
        }
        return properties.s3().videoPrefix() + fileName;
    }
}
