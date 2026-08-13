package com.stream.video.dto;

import org.springframework.core.io.Resource;

public record ResourceResponseDTO(Resource resource,String contentType) {
}
