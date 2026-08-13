package com.stream.video.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "video")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String title;

    private String description;

    private String contentType;

    private String filePath;

    @Enumerated(value = EnumType.STRING)
    private HlsStatus status;

    // To save error happen while processing video.
    // ffmpeg failures come back as several lines of stderr, which do not fit the default
    // varchar(255): the insert would then fail while recording the failure.
    @Column(length = 2000)
    private String hlsError;

    // ffprobe reports fractional seconds (130.240726 for the sample), and playlist
    // durations are fractional too, so a whole-second type would round the video short.
    private Double durationSeconds;
}
