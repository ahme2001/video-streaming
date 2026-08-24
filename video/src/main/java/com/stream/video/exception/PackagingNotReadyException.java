package com.stream.video.exception;

import lombok.Getter;

/**
 * Raised when a video exists but has no playable output for the requested protocol yet,
 * Answered with 409 so it is not confused with an unknown id.
 */
@Getter
public class PackagingNotReadyException extends RuntimeException {

    private final String status;

    public PackagingNotReadyException(String message, String status) {
        super(message);
        this.status = status;
    }
}
