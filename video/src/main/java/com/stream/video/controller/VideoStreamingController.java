package com.stream.video.controller;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.dto.VideoStreamResponseDTO;
import com.stream.video.service.VideoStreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> getVideoChunkByChunk(@PathVariable String id,
                                                        @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
        VideoStreamResponseDTO response = videoStreamingService.getNextChunkOfVideo(id,range);

        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(response.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(MediaType.parseMediaType(response.contentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(response.contentLength());

        if (response.partial()) {
            builder.header(HttpHeaders.CONTENT_RANGE,
                    "bytes %d-%d/%d".formatted(response.start(), response.end(), response.totalLength()));
        }
        return builder.body(response.resource());
    }


}
