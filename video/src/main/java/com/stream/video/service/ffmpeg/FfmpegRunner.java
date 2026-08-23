package com.stream.video.service.ffmpeg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs an external command with a timeout and returns whatever it printed. Knows nothing
 * about video, so it can be tested against sh, sleep and false.
 */
@Service
@Slf4j
public class FfmpegRunner {


    private static final int MAX_CAPTURED_LINES = 50;
    private static final Duration DRAIN_GRACE = Duration.ofSeconds(5);

    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(5);

    private static final int NO_EXIT_CODE = -1;

    public record Result(int exitCode, boolean timedOut, String output) {
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
        public String describeFailure() {
            return timedOut ? "timed out" : "exited with " + exitCode;
        }
    }

    public Result run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        log.debug("Running: {}", String.join(" ", command));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        process.getOutputStream().close();
        CompletableFuture<String> output = drainAsync(process);

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Killing command after {}: {}", timeout, String.join(" ", command));
                terminate(process);
                return new Result(NO_EXIT_CODE, true, awaitOutput(output));
            }
            return new Result(process.exitValue(), false, awaitOutput(output));
        } finally {
            // An exception on the way out must not leave ffmpeg running unattended.
            if (process.isAlive()) {
                terminate(process);
            }
        }
    }

    private CompletableFuture<String> drainAsync(Process process) {
        CompletableFuture<String> output = new CompletableFuture<>();
        Thread.ofVirtual()
                .name("ffmpeg-drain-" + process.pid())
                .start(() -> output.complete(drain(process)));
        return output;
    }

    private String drain(Process process) {
        Deque<String> tail = new ArrayDeque<>(MAX_CAPTURED_LINES);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // A long encode prints far more than is worth keeping in memory.
                if (tail.size() == MAX_CAPTURED_LINES) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            }
        } catch (IOException e) {
            // The stream dies when the process is killed, which is normal on timeout.
            log.debug("Stopped reading process output", e);
        }
        return String.join(System.lineSeparator(), tail);
    }

    private String awaitOutput(CompletableFuture<String> output) throws InterruptedException {
        try {
            return output.get(DRAIN_GRACE.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            log.warn("Could not collect the process output", e);
            return "";
        }
    }

    private void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(TERMINATION_GRACE.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
