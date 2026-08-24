package com.stream.video.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class health {

    @GetMapping("/health")
    public ResponseEntity<String> getAllVideos(){
        return ResponseEntity.
                status(HttpStatus.OK).
                body("hello world");
    }
}
