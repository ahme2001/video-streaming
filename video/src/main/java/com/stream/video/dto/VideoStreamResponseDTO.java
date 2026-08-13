package com.stream.video.dto;

import org.springframework.core.io.Resource;

/**
 * A slice of a video ready to be written to the response.
 * When {@code partial} is false the slice covers the whole file and the caller
 * should answer with 200 instead of 206.
 */
public record VideoStreamResponseDTO(
        Resource resource,
        String contentType,
        long start,
        long end,
        long totalLength,
        boolean partial
) {
    public long contentLength() {
        return end - start + 1;
    }
}
