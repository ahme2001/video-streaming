package com.stream.video.service.impl;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.model.Video;
import com.stream.video.service.VideoService;
import com.stream.video.service.VideoStreamingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private final VideoService videoService;

    @Override
    public ResourceResponseDTO getInOneChunk(String id) {
        VideoResponseDTO video = videoService.getVideoById(id);
        String contentType = video.contentType();
        String filePath = video.path();
        Resource resource = new FileSystemResource(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        return new ResourceResponseDTO(resource,contentType);
    }


    @Override
    public ResourceResponseDTO getNextChunkOfVideo(String id, String range) {
        VideoResponseDTO video = videoService.getVideoById(id);
        String contentType = video.contentType();
        String filePath = video.path();

        return new ResourceResponseDTO(null,contentType);
    }
}
