package com.stream.video.repository;

import com.stream.video.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, String> {
    Optional<Video> findByTitle(String title);


    @Modifying
    @Transactional
    @Query("""
            update Video v
               set v.status = com.stream.video.model.HlsStatus.PROCESSING
             where v.id = :id
            """)
    int markProcessing(@Param("id") String id);

    @Modifying
    @Transactional
    @Query("""
            update Video v
               set v.status = com.stream.video.model.HlsStatus.READY,
                   v.hlsError = null
             where v.id = :id
            """)
    int markReady(@Param("id") String id);

    @Modifying
    @Transactional
    @Query("""
            update Video v
               set v.status = com.stream.video.model.HlsStatus.FAILED,
                   v.hlsError = :error
             where v.id = :id
            """)
    int markFailed(@Param("id") String id, @Param("error") String error);
}
