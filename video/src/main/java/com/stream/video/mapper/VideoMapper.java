package com.stream.video.mapper;

import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.model.Video;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface VideoMapper {

    @Mapping(source = "id", target = "videoId")
    @Mapping(source = "filePath" , target = "path")
    VideoResponseDTO videoToResponse(Video video);

    List<VideoResponseDTO> videosToResponse(List<Video> videoList);
}
