package com.stream.video.repository;

import com.stream.video.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, String> {
    Optional<Video> findByTitle(String title);


    @Modifying
    @Query("""
            update Video v
               set v.status = com.stream.video.model.HlsStatus.PENDING
             where v.status is null
                or v.status = com.stream.video.model.HlsStatus.PROCESSING
            """)
    int requeueUnfinished();
}
