package com.stream.video.service.impl;

import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.exception.NotFoundException;
import com.stream.video.mapper.VideoMapper;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    @Value("${video.folder}")
    private String DIR;
    private final FileValidationService fileValidationService;

    private final VideoRepository videoRepository;
    private final VideoMapper videoMapper;

    @Override
    public VideoResponseDTO uploadVideo(String description, MultipartFile file) {
        String contentType = fileValidationService.validateAndDetect(file);
        String extension = fileValidationService.extensionFor(contentType);
        String title = buildTitle(file.getOriginalFilename(),extension);

        Path target = Path.of(DIR).resolve(title).toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file locally: " + file.getOriginalFilename(), e);
        }

        log.trace("Stored file locally with title {}", title);

        Video video = new Video();
        video.setContentType(contentType);
        video.setDescription(description);
        video.setTitle(title);
        video.setFilePath(Path.of(DIR).resolve(title).toString());
        video = videoRepository.save(video);
        return videoMapper.videoToResponse(video);
    }

    private String buildTitle(String originalName, String extension) {
        return originalName != null ? originalName: UUID.randomUUID() + "/" + extension;
    }

    @Override
    public VideoResponseDTO getVideoById(String id) {
        Video video = videoRepository.findById(id).orElseThrow(
                () -> new NotFoundException("video with following id not found: " + id)
        );
        return videoMapper.videoToResponse(video);
    }

    @Override
    public VideoResponseDTO getVideoByTitle(String title) {
        Video video = videoRepository.findByTitle(title).orElseThrow(
                () -> new NotFoundException("video with following title not found: " + title)
        );
        return videoMapper.videoToResponse(video);
    }

    @Override
    public List<VideoResponseDTO> getAllVideos() {
        return videoMapper.videosToResponse(videoRepository.findAll());
    }
}
