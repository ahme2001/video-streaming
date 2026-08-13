package com.stream.video.service;

import com.stream.video.dto.ResourceResponseDTO;


public interface VideoStreamingService {

    ResourceResponseDTO getInOneChunk(String id);

    ResourceResponseDTO getNextChunkOfVideo(String id, String range);
}
