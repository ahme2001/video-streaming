package com.stream.video.controller;

import com.stream.video.dto.VideoResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class health {

    @GetMapping
    public ResponseEntity<String> getAllVideos(){
        return ResponseEntity.
                status(HttpStatus.OK).
                body("hello world");
    }
}
