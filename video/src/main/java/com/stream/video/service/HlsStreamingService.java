package com.stream.video.service;

import com.stream.video.dto.ResourceResponseDTO;

public interface HlsStreamingService {

    ResourceResponseDTO getMasterPlaylist(String videoId);

    ResourceResponseDTO getMediaPlaylist(String videoId, String rendition);

    ResourceResponseDTO getSegment(String videoId, String rendition, String segment);
}
