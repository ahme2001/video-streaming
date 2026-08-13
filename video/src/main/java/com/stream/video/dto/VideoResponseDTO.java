package com.stream.video.dto;

public record VideoResponseDTO(
        String videoId,
        String title,
        String description,
        String contentType,
        String path
) {}
