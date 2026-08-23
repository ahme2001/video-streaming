package com.stream.video.service.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Path;


public interface ObjectStorage {

    void upload(String key, InputStream content, long contentLength, String contentType);

    /** Streams a local file to storage without reading it into memory. */
    void upload(String key, Path file, String contentType);

    byte[] readAllBytes(String key);

    /**
     * A resource that opens its stream only when the response is written, so a large
     * object is never held in memory.
     */
    Resource openStream(String key);

    void downloadTo(String key, Path target);

    void deleteByPrefix(String keyPrefix);
}
