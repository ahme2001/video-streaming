package com.stream.video.controller;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.service.HlsStreamingService;
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
@RequestMapping("/api/v1/video/hls")
@RequiredArgsConstructor
public class HlsStreamingController {

    private final HlsStreamingService hlsStreamingService;

    @GetMapping("/{id}/master.m3u8")
    public ResponseEntity<Resource> getMasterPlaylist(@PathVariable String id) {
        return playlist(hlsStreamingService.getMasterPlaylist(id));
    }

    @GetMapping("/{id}/{rendition}/index.m3u8")
    public ResponseEntity<Resource> getMediaPlaylist(@PathVariable String id,
                                                    @PathVariable String rendition) {
        return playlist(hlsStreamingService.getMediaPlaylist(id, rendition));
    }

    @GetMapping("/{id}/{rendition}/{segment}")
    public ResponseEntity<Resource> getSegment(@PathVariable String id,
                                               @PathVariable String rendition,
                                               @PathVariable String segment) {
        ResourceResponseDTO response = hlsStreamingService.getSegment(id, rendition, segment);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(response.contentType()))
                // A segment's bytes never change once written, so it can be held for a long time.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(response.resource());
    }

    private ResponseEntity<Resource> playlist(ResourceResponseDTO response) {
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(response.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(response.resource());
    }
}
