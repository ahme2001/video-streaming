package com.stream.video.service.storage.impl;

import org.springframework.core.io.AbstractResource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;

/**
 * An S3 object exposed as a Spring {@link org.springframework.core.io.Resource}.
 * The length is known up front so the response can carry a Content-Length, while the
 * body is fetched only when the converter asks for the stream, which keeps whole videos
 * out of heap.
 */
class S3ObjectResource extends AbstractResource {

    private final S3Client s3Client;
    private final String bucket;
    private final String key;
    private final long contentLength;

    S3ObjectResource(S3Client s3Client, String bucket, String key, long contentLength) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
        this.contentLength = contentLength;
    }

    @Override
    public InputStream getInputStream() {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public boolean exists() {
        // The caller already read the object's metadata to build this resource.
        return true;
    }

    @Override
    public String getFilename() {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    @Override
    public String getDescription() {
        return "S3 object [s3://" + bucket + "/" + key + "]";
    }
}
