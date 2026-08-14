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
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HlsStreamingServiceImpl implements HlsStreamingService {

    public static final String PLAYLIST_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    public static final String SEGMENT_CONTENT_TYPE = "video/mp2t";
    private static final Pattern SEGMENT_NAME = Pattern.compile("segment_\\d{5}\\.ts");

    private final HlsProcessingService hlsProcessingService;
    private final VideoRepository videoRepository;
    private final HlsProperties properties;
    private final HlsLayout layout;

    @Override
    public ResourceResponseDTO getMasterPlaylist(String videoId) {
        Path file = readyOutputDir(videoId).resolve(HlsPackager.MASTER_PLAYLIST);
        return playlist(videoId, file);
    }

    @Override
    public ResourceResponseDTO getMediaPlaylist(String videoId, String rendition) {
        Path file = renditionDir(videoId, rendition).resolve(HlsPackager.PLAYLIST_NAME);
        return playlist(videoId, file);
    }

    @Override
    public ResourceResponseDTO getSegment(String videoId, String rendition, String segment) {
        if (!SEGMENT_NAME.matcher(segment).matches()) {
            throw new NotFoundException("no such segment: " + segment);
        }

        Path file = renditionDir(videoId, rendition).resolve(segment);
        return new ResourceResponseDTO(readable(videoId, file), SEGMENT_CONTENT_TYPE);
    }

    private ResourceResponseDTO playlist(String videoId, Path file) {
        return new ResourceResponseDTO(readable(videoId, file), PLAYLIST_CONTENT_TYPE);
    }

    private Path renditionDir(String videoId, String rendition) {
        if (!properties.hasRendition(rendition)) {
            throw new NotFoundException("no such rendition: " + rendition);
        }
        return readyOutputDir(videoId).resolve(rendition);
    }

    private Path readyOutputDir(String videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new NotFoundException("video with following id not found: " + videoId));

        if (video.getStatus() != HlsStatus.READY) {
            String status = video.getStatus() == null ? "NONE" : video.getStatus().name();
            hlsProcessingService.submit(videoId);
            throw new HlsNotReadyException(
                    "HLS output for " + videoId + " is not available, status is " + status, status);
        }
        return layout.outputDir(videoId);
    }

    /**
     * Second layer: whatever the path variables were, the file actually served has to sit
     * inside the output root. This is what still holds if the name check above is loosened.
     */
    private FileSystemResource readable(String videoId, Path file) {
        Path resolved = file.normalize();
        if (!resolved.startsWith(properties.outputRoot()) || !Files.isReadable(resolved)) {
            throw new NotFoundException("HLS file is missing for id: " + videoId);
        }
        return new FileSystemResource(resolved);
    }
}
