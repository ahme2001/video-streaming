package com.stream.video.service.impl;

import com.stream.video.config.HlsProperties;
import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.exception.HlsNotReadyException;
import com.stream.video.exception.NotFoundException;
import com.stream.video.model.HlsStatus;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.HlsProcessingService;
import com.stream.video.service.HlsStreamingService;
import com.stream.video.service.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HlsStreamingServiceImpl implements HlsStreamingService {

    private final HlsProcessingService hlsProcessingService;
    private final VideoRepository videoRepository;
    private final HlsProperties properties;
    private final HlsLayout layout;
    private final ObjectStorage objectStorage;

    @Override
    public ResourceResponseDTO getMasterPlaylist(String videoId) {
        requireReady(videoId);
        return fetch(layout.masterPlaylistKey(videoId), HlsPackager.PLAYLIST_CONTENT_TYPE);
    }

    @Override
    public ResourceResponseDTO getMediaPlaylist(String videoId, String rendition) {
        requireReady(videoId);
        requireRendition(rendition);
        return fetch(layout.mediaPlaylistKey(videoId, rendition), HlsPackager.PLAYLIST_CONTENT_TYPE);
    }

    @Override
    public ResourceResponseDTO getSegment(String videoId, String rendition, String segment) {
        requireReady(videoId);
        requireRendition(rendition);
        // The name is matched whole, so a path variable can never widen the key beyond
        // one segment of this rendition.
        if (!HlsPackager.SEGMENT_NAME.matcher(segment).matches()) {
            throw new NotFoundException("no such segment: " + segment);
        }
        return fetch(layout.segmentKey(videoId, rendition, segment), HlsPackager.SEGMENT_CONTENT_TYPE);
    }

    /**
     * Playlists are tiny and a segment is a few seconds of video, so both are read whole
     * rather than held open as a stream for the length of the response.
     */
    private ResourceResponseDTO fetch(String key, String contentType) {
        return new ResourceResponseDTO(new ByteArrayResource(objectStorage.readAllBytes(key)), contentType);
    }

    private void requireRendition(String rendition) {
        if (!properties.hasRendition(rendition)) {
            throw new NotFoundException("no such rendition: " + rendition);
        }
    }

    private void requireReady(String videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new NotFoundException("video with following id not found: " + videoId));

        if (video.getStatus() != HlsStatus.READY) {
            String status = video.getStatus() == null ? "NONE" : video.getStatus().name();
            hlsProcessingService.submit(videoId);
            throw new HlsNotReadyException(
                    "HLS output for " + videoId + " is not available, status is " + status, status);
        }
    }
}
