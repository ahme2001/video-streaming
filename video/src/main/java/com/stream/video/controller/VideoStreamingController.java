package com.stream.video.controller;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.service.VideoStreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
public class VideoStreamingController {

    private final VideoStreamingService videoStreamingService;

    @GetMapping("/{id}")
    public ResponseEntity<Resource> getAllVideoInOneChunk(@PathVariable String id){
        ResourceResponseDTO response = videoStreamingService.getInOneChunk(id);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(response.contentType()))
                .body(response.resource());
    }
}
