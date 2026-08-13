package com.stream.video.service.impl;

import com.stream.video.dto.ResourceResponseDTO;
import com.stream.video.dto.VideoResponseDTO;
import com.stream.video.dto.VideoStreamResponseDTO;
import com.stream.video.exception.NotFoundException;
import com.stream.video.service.VideoService;
import com.stream.video.service.VideoStreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoStreamingServiceImpl implements VideoStreamingService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final VideoService videoService;

    @Value("${video.folder}")
    private String DIR;

    @Value("${video.chunk-size-bytes}")
    private long chunkSizeBytes;

    @Override
    public ResourceResponseDTO getInOneChunk(String id) {
        VideoResponseDTO video = videoService.getVideoById(id);
        Path file = resolveFile(video);
        return new ResourceResponseDTO(new FileSystemResource(file), contentTypeOf(video));
    }

    @Override
    public VideoStreamResponseDTO getNextChunkOfVideo(String id, String range) throws IOException {
        VideoResponseDTO video = videoService.getVideoById(id);
        Path file = resolveFile(video);
        long totalLength = sizeOf(file);

        Optional<ByteRangeParser.ByteRange> requested = ByteRangeParser.parse(range, totalLength);
        if (requested.isEmpty()) {
            // No Range header at all: serve the whole file, and advertise that ranges work.
            return new VideoStreamResponseDTO(new FileSystemResource(file), contentTypeOf(video),
                    0, Math.max(0, totalLength - 1), totalLength, false);
        }

        ByteRangeParser.ByteRange chunk = requested.get().cappedAt(chunkSizeBytes);
        byte[] data = read(file, chunk);
        // The file could have been truncated between the size check and the read,
        // so the advertised range follows what was actually read.
        long end = chunk.start() + data.length - 1;

        log.trace("Serving bytes {}-{}/{} of video {}", chunk.start(), end, totalLength, id);
        return new VideoStreamResponseDTO(new ByteArrayResource(data), contentTypeOf(video),
                chunk.start(), end, totalLength, true);
    }

    /**
     * Resolves the stored file against the configured folder. Only the file name of
     * the stored value is used, so a path that somehow made it into the database
     * cannot walk out of the video folder.
     */
    private Path resolveFile(VideoResponseDTO video) {
        Path baseDir = Path.of(DIR).toAbsolutePath().normalize();
        Path fileName = Path.of(video.path()).getFileName();
        if (fileName == null) {
            throw new NotFoundException("video file is missing for id: " + video.videoId());
        }

        Path file = baseDir.resolve(fileName).normalize();
        if (!file.startsWith(baseDir) || !Files.isReadable(file)) {
            throw new NotFoundException("video file is missing for id: " + video.videoId());
        }
        return file;
    }

    private String contentTypeOf(VideoResponseDTO video) {
        return video.contentType() != null ? video.contentType() : DEFAULT_CONTENT_TYPE;
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the size of " + file, e);
        }
    }

    private byte[] read(Path file, ByteRangeParser.ByteRange chunk) throws IOException {
        byte[] buffer = new byte[(int) chunk.length()];

        try (InputStream inputStream = Files.newInputStream(file)) {
            // skipNBytes/readNBytes rather than skip/read: the plain versions are allowed
            // to move fewer bytes than asked for, which would silently shift every chunk
            // off its offset or pad the tail of the buffer with zeros.
            inputStream.skipNBytes(chunk.start());
            int read = inputStream.readNBytes(buffer, 0, buffer.length);
            log.trace("read(number of bytes) : {}", read);
            return read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            log.error("Failed to read chunk of {}", file, e);
            throw new IOException("Failed to read chunk of " + file, e);
        }
    }
}
