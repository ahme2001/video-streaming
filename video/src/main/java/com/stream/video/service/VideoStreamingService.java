package com.stream.video.service;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.dto.VideoStreamResponseDTO;

import java.io.IOException;


public interface VideoStreamingService {

    ResourceResponseDTO getInOneChunk(String id);

    VideoStreamResponseDTO getNextChunkOfVideo(String id, String range) throws IOException;
}
