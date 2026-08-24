package com.stream.video.controller;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.service.dash.DashStreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/video/dash")
@RequiredArgsConstructor
public class DashStreamingController {

    private final DashStreamingService dashStreamingService;

    @GetMapping("/{id}/manifest.mpd")
    public ResponseEntity<Resource> getManifest(@PathVariable String id) {
        ResourceResponseDTO response = dashStreamingService.getManifest(id);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(response.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(response.resource());
    }

    @GetMapping("/{id}/{segment}")
    public ResponseEntity<Resource> getSegment(@PathVariable String id,
                                               @PathVariable String segment) {
        ResourceResponseDTO response = dashStreamingService.getSegment(id, segment);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(response.contentType()))
                // A segment's bytes never change once written, so it can be held for a long time.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(response.resource());
    }
}
