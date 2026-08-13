package com.stream.video.service.impl;

import com.stream.video.exception.InvalidFileException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
public class FileValidationService {

    // Thread-safe; detection reads only the leading bytes (magic numbers),
    // the filename is deliberately not passed so the extension can't influence the result
    private static final Tika TIKA = new Tika();

    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "video/mp4", "mp4",
            "application/mp4", "mp4",
            "video/quicktime", "mov",
            "video/webm", "webm",
            "video/x-matroska", "mkv"
    );

    public String validateAndDetect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file is missing or empty");
        }

        return detect(file);
    }


    public String extensionFor(String mimeType) {
        String extension = EXTENSION_BY_MIME.get(mimeType);
        if (extension == null) {
            throw new InvalidFileException("Unsupported content type: " + mimeType);
        }
        return extension;
    }

    private String detect(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return TIKA.detect(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file for type detection: " + file.getOriginalFilename(), e);
        }
    }
}
