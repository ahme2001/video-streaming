package com.stream.video.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String,String>> handleNotFoundException(NotFoundException e) {
        Map<String,String> errors = new HashMap<>();
        errors.put("status", HttpStatus.NOT_FOUND.toString());
        errors.put("message", e.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String,String>> handleInvalidFileException(InvalidFileException e) {
        Map<String,String> errors = new HashMap<>();
        errors.put("status", HttpStatus.NOT_ACCEPTABLE.toString());
        errors.put("message", e.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(InvalidRangeException.class)
    public ResponseEntity<Map<String,String>> handleInvalidRangeException(InvalidRangeException e) {
        Map<String,String> errors = new HashMap<>();
        errors.put("status", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.toString());
        errors.put("message", e.getMessage());
        // RFC 7233: a 416 tells the client the length it should have ranged against
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + e.getTotalLength())
                .body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleException(Exception e) {
        Map<String,String> errors = new HashMap<>();
        errors.put("status", HttpStatus.BAD_REQUEST.toString());
        errors.put("message", e.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }


}
