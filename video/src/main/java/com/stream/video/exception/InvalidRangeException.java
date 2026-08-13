package com.stream.video.exception;

import lombok.Getter;

/**
 * Raised when a Range header is syntactically valid but cannot be satisfied,
 * e.g. it starts past the end of the file. Answered with 416.
 */
@Getter
public class InvalidRangeException extends RuntimeException {

    private final long totalLength;

    public InvalidRangeException(String message, long totalLength) {
        super(message);
        this.totalLength = totalLength;
    }
}
