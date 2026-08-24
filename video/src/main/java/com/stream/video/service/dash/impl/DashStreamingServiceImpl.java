package com.stream.video.service.dash.impl;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.exception.PackagingNotReadyException;
import com.stream.video.exception.NotFoundException;
import com.stream.video.model.PackagingStatus;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.dash.DashProcessingService;
import com.stream.video.service.dash.DashStreamingService;
import com.stream.video.service.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashStreamingServiceImpl implements DashStreamingService {

    private final DashProcessingService dashProcessingService;
    private final VideoRepository videoRepository;
    private final DashLayout layout;
    private final ObjectStorage objectStorage;

    @Override
    public ResourceResponseDTO getManifest(String videoId) {
        requireReady(videoId);
        return fetch(layout.manifestKey(videoId), DashPackager.MANIFEST_CONTENT_TYPE);
    }

    @Override
    public ResourceResponseDTO getSegment(String videoId, String segment) {
        requireReady(videoId);
        // Matched whole, so a path variable can never reach beyond this video's own files.
        if (!DashPackager.SEGMENT_NAME.matcher(segment).matches()) {
            throw new NotFoundException("no such segment: " + segment);
        }
        return fetch(layout.segmentKey(videoId, segment), DashPackager.SEGMENT_CONTENT_TYPE);
    }

    /** Manifests are small and a segment is a few seconds of video, so both are read whole. */
    private ResourceResponseDTO fetch(String key, String contentType) {
        return new ResourceResponseDTO(new ByteArrayResource(objectStorage.readAllBytes(key)), contentType);
    }

    private void requireReady(String videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new NotFoundException("video with following id not found: " + videoId));

        if (video.getDashStatus() != PackagingStatus.READY) {
            String status = video.getDashStatus() == null ? "NONE" : video.getDashStatus().name();
            dashProcessingService.submit(videoId);
            throw new PackagingNotReadyException(
                    "DASH output for " + videoId + " is not available, status is " + status, status);
        }
    }
}
