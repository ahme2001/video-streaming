package com.stream.video.exception;

import lombok.Getter;

/**
 * Raised when a video exists but has no playable HLS output yet, or never will.
 * Answered with 409 so it is not confused with an unknown id.
 */
@Getter
public class HlsNotReadyException extends RuntimeException {

    private final String status;

    public HlsNotReadyException(String message, String status) {
        super(message);
        this.status = status;
    }
}
