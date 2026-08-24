package com.stream.video.service.video.impl;

import com.stream.video.config.AwsProperties;
import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.exception.NotFoundException;
import com.stream.video.mapper.VideoMapper;
import com.stream.video.model.PackagingStatus;
import com.stream.video.model.Video;
import com.stream.video.repository.VideoRepository;
import com.stream.video.service.dash.DashProcessingService;
import com.stream.video.service.hls.HlsProcessingService;
import com.stream.video.service.storage.impl.FileValidationService;
import com.stream.video.service.storage.ObjectStorage;
import com.stream.video.service.video.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final FileValidationService fileValidationService;
    private final ObjectStorage objectStorage;
    private final AwsProperties awsProperties;

    private final VideoRepository videoRepository;
    private final VideoMapper videoMapper;
    private final HlsProcessingService hlsProcessingService;
    private final DashProcessingService dashProcessingService;

    @Override
    public VideoResponseDTO uploadVideo(String description, MultipartFile file) {
        String contentType = fileValidationService.validateAndDetect(file);
        String extension = fileValidationService.extensionFor(contentType);
        String storedName = UUID.randomUUID() + "." + extension;
        String key = awsProperties.s3().videoPrefix() + storedName;

        try (InputStream content = file.getInputStream()) {
            objectStorage.upload(key, content, file.getSize(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file in S3: " + file.getOriginalFilename(), e);
        }

        log.info("Stored upload {} as {}", file.getOriginalFilename(), key);

        Video video = new Video();
        video.setContentType(contentType);
        video.setDescription(description);
        video.setTitle(storedName);
        video.setFilePath(storedName);
        video.setStatus(PackagingStatus.PENDING);
        video.setDashStatus(PackagingStatus.PENDING);
        video = videoRepository.save(video);
        // Both packagings are queued on the same single-threaded executor, so they run one
        // after the other rather than fighting over the CPU.
        hlsProcessingService.submit(video.getId());
        dashProcessingService.submit(video.getId());
        return videoMapper.videoToResponse(video);
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
