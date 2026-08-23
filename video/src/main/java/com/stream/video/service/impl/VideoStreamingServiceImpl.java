package com.stream.video.service.impl;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.service.storage.ObjectStorage;
import com.stream.video.service.video.VideoService;
import com.stream.video.service.VideoStreamingService;
import com.stream.video.service.storage.impl.VideoObjectLocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final VideoService videoService;
    private final VideoObjectLocator objectLocator;
    private final ObjectStorage objectStorage;

    @Override
    public ResourceResponseDTO getInOneChunk(String id) {
        VideoResponseDTO video = videoService.getVideoById(id);
        return new ResourceResponseDTO(objectStorage.openStream(keyOf(video)), contentTypeOf(video));
    }


    private String keyOf(VideoResponseDTO video) {
        return objectLocator.key(video.videoId(), video.path());
    }

    private String contentTypeOf(VideoResponseDTO video) {
        return video.contentType() != null ? video.contentType() : DEFAULT_CONTENT_TYPE;
    }
}
