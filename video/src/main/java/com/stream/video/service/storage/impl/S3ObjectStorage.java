package com.stream.video.service.storage.impl;

import com.stream.video.config.AwsProperties;
import com.stream.video.exception.NotFoundException;
import com.stream.video.service.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final AwsProperties properties;

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        s3Client.putObject(putRequest(key, contentType),
                RequestBody.fromInputStream(content, contentLength));
        log.debug("Uploaded {} bytes to {}", contentLength, key);
    }

    @Override
    public void upload(String key, Path file, String contentType) {
        s3Client.putObject(putRequest(key, contentType), RequestBody.fromFile(file));
        log.debug("Uploaded {} to {}", file.getFileName(), key);
    }

    @Override
    public byte[] readAllBytes(String key) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket()).key(key).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw missing(key, e);
        } catch (S3Exception e) {
            throw translate(key, e);
        }
    }

    @Override
    public Resource openStream(String key) {
        // HEAD first: it both proves the object is there and gives the length the
        // response needs, without pulling the body into memory.
        return new S3ObjectResource(s3Client, bucket(), key, headObject(key).contentLength());
    }

    @Override
    public void downloadTo(String key, Path target) {
        try (ResponseInputStream<GetObjectResponse> body = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket()).key(key).build())) {

            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchKeyException e) {
            throw missing(key, e);
        } catch (S3Exception e) {
            throw translate(key, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + key + " to " + target, e);
        }
    }

    @Override
    public void deleteByPrefix(String keyPrefix) {
        String continuationToken = null;
        do {
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket())
                    .prefix(keyPrefix)
                    .continuationToken(continuationToken)
                    .maxKeys(DELETE_BATCH_SIZE)
                    .build());

            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            if (!keys.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket())
                        .delete(Delete.builder().objects(keys).build())
                        .build());
                log.debug("Deleted {} objects under {}", keys.size(), keyPrefix);
            }
            continuationToken = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    private HeadObjectResponse headObject(String key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (NoSuchKeyException e) {
            throw missing(key, e);
        } catch (S3Exception e) {
            throw translate(key, e);
        }
    }

    private PutObjectRequest putRequest(String key, String contentType) {
        return PutObjectRequest.builder()
                .bucket(bucket())
                .key(key)
                .contentType(contentType)
                .build();
    }

    private String bucket() {
        return properties.s3().bucket();
    }

    /**
     * HeadObject answers a missing key with a bare 404 and no error code, so the
     * status has to be inspected rather than relying on NoSuchKeyException alone.
     */
    private RuntimeException translate(String key, S3Exception e) {
        return e.statusCode() == 404 ? missing(key, e) : e;
    }

    private NotFoundException missing(String key, Exception cause) {
        log.debug("Object not found in S3: {}", key, cause);
        return new NotFoundException("object is missing from storage: " + key);
    }
}
