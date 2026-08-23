package com.stream.video.controller;

import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.service.video.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping
    public ResponseEntity<?> uploadVideo(@RequestParam(required = false) String description,
                                         @RequestParam(name = "video") MultipartFile video){
        VideoResponseDTO response = videoService.uploadVideo(description,video);
        return ResponseEntity.
                status(HttpStatus.CREATED).
                body(response);
    }

    @GetMapping
    public ResponseEntity<List<VideoResponseDTO>> getAllVideos(){
        List<VideoResponseDTO> response = videoService.getAllVideos();
        return ResponseEntity.
                status(HttpStatus.OK).
                body(response);
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<VideoResponseDTO> getVideoById(@PathVariable String id){
        VideoResponseDTO response = videoService.getVideoById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/title/{title}")
    public ResponseEntity<VideoResponseDTO> getVideoByTitle(@PathVariable String title){
        VideoResponseDTO response = videoService.getVideoByTitle(title);
        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }
}
