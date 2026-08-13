package com.stream.video.service.impl;

import com.stream.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class HlsStateRecovery {

    private final VideoRepository videoRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void requeueUnfinishedVideos() {
        int requeued = videoRepository.requeueUnfinished();
        if (requeued > 0) {
            log.info("Requeued {} video(s) whose HLS packaging never finished", requeued);
        }
    }
}
