package com.stream.video.service.impl;

import com.stream.video.exception.InvalidRangeException;
import lombok.NoArgsConstructor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor
final class ByteRangeParser {

    private static final String BYTES_PREFIX = "bytes=";
    private static final Pattern SPEC = Pattern.compile("(\\d*)-(\\d*)");

    static Optional<ByteRange> parse(String header, long totalLength) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String value = header.trim();
        if (!value.startsWith(BYTES_PREFIX)) {
            throw malformed(value, totalLength);
        }

        String firstSpec = value.substring(BYTES_PREFIX.length()).split(",", 2)[0].trim();
        Matcher matcher = SPEC.matcher(firstSpec);
        if (!matcher.matches()) {
            throw malformed(value, totalLength);
        }

        String firstBytePos = matcher.group(1);
        String lastBytePos = matcher.group(2);

        long start;
        long end;
        try {
            if (firstBytePos.isEmpty()) {
                // suffix form: "-N" means the last N bytes
                if (lastBytePos.isEmpty()) {
                    throw malformed(value, totalLength);
                }
                long suffixLength = Long.parseLong(lastBytePos);
                if (suffixLength <= 0) {
                    throw unsatisfiable(firstSpec, totalLength);
                }
                start = Math.max(0, totalLength - suffixLength);
                end = totalLength - 1;
            } else {
                start = Long.parseLong(firstBytePos);
                end = lastBytePos.isEmpty() ? totalLength - 1 : Long.parseLong(lastBytePos);
            }
        } catch (NumberFormatException e) {
            // positions too large to hold in a long
            throw malformed(value, totalLength);
        }

        end = Math.min(end, totalLength - 1);
        if (start > end || start >= totalLength) {
            throw unsatisfiable(firstSpec, totalLength);
        }
        return Optional.of(new ByteRange(start, end));
    }

    private static InvalidRangeException malformed(String header, long totalLength) {
        return new InvalidRangeException("Malformed Range header: " + header, totalLength);
    }

    private static InvalidRangeException unsatisfiable(String spec, long totalLength) {
        return new InvalidRangeException(
                "Range cannot be satisfied: bytes=" + spec + " against a " + totalLength + " byte resource",
                totalLength);
    }

    record ByteRange(long start, long end) {

        long length() {
            return end - start + 1;
        }

        ByteRange cappedAt(long maxLength) {
            return length() <= maxLength ? this : new ByteRange(start, start + maxLength - 1);
        }
    }
}
