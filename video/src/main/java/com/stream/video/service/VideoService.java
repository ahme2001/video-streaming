package com.stream.video.service;

import com.stream.video.dto.VideoResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VideoService {

    VideoResponseDTO uploadVideo(String description, MultipartFile file);

    // get video by id
    VideoResponseDTO getVideoById(String id);

    // get video by title
    VideoResponseDTO getVideoByTitle(String title);

    // get all video
    List<VideoResponseDTO> getAllVideos();
}
