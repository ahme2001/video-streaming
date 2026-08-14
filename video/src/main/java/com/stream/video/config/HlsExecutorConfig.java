package com.stream.video.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class HlsExecutorConfig {

    public static final String HLS_EXECUTOR = "hlsExecutor";

    /**
     * One packaging job at a time. ffmpeg already spreads a single encode across every core,
     * so running several at once does not finish them sooner, it just makes them contend.
     */
    @Bean(name = HLS_EXECUTOR)
    public ThreadPoolTaskExecutor hlsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("hls-");

        // On shutdown the worker is interrupted and FfmpegRunner kills its child process.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
