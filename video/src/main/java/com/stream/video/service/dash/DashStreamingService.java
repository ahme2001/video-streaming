package com.stream.video.service.dash;

import com.stream.video.dto.ResourceResponseDTO;

public interface DashStreamingService {

    ResourceResponseDTO getManifest(String videoId);

    /** An init segment or a media segment; DASH keeps them all in one flat folder. */
    ResourceResponseDTO getSegment(String videoId, String segment);
}
