package com.jobportal.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class FileStorageUtil {

    private final Path uploadLocation;
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".pdf", ".docx");

    public FileStorageUtil(@Value("${file.upload-dir:./uploads/resumes}") String uploadDir) {
        this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location for resume uploads", e);
        }
    }

    public String storeFile(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        
        // Security check: Path traversal prevention
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Cannot store file with relative path outside current directory " + originalFilename);
        }

        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = originalFilename.substring(dotIndex).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new IllegalArgumentException("Unsupported file format. Only PDF and DOCX files are permitted.");
        }

        String storedFileName = UUID.randomUUID().toString() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        Path destination = this.uploadLocation.resolve(storedFileName).normalize();

        // Ensure target is within upload directory
        if (!destination.startsWith(this.uploadLocation)) {
            throw new SecurityException("Cannot store file outside configured directory");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        return destination.toString();
    }

    public Path getFilePath(String fullFilePath) {
        Path path = Paths.get(fullFilePath).toAbsolutePath().normalize();
        if (!path.startsWith(this.uploadLocation)) {
            throw new SecurityException("Access to path outside upload directory is strictly prohibited");
        }
        return path;
    }

    public void deleteFile(String fullFilePath) {
        try {
            Path path = getFilePath(fullFilePath);
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
