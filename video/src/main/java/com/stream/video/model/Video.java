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

    /** HLS packaging state. Kept as "status" so existing rows and clients still read it. */
    @Enumerated(value = EnumType.STRING)
    private PackagingStatus status;

    @Column(length = 2000)
    private String hlsError;

    /**
     * DASH packaging state, tracked separately: the two are packaged by independent jobs,
     * so one protocol being unavailable says nothing about the other.
     */
    @Enumerated(value = EnumType.STRING)
    private PackagingStatus dashStatus;

    @Column(length = 2000)
    private String dashError;
}
