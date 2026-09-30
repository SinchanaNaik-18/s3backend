package com.eventsphere.controller;

import com.eventsphere.dto.UploadResponse;
import com.eventsphere.service.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final S3StorageService s3StorageService;

    public FileController(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    /**
     * Health and status check endpoint.
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "EventSphere S3 REST API",
                "endpoints", Map.of(
                        "upload", "POST /upload",
                        "download", "GET /download/{imageId}"
                )
        ));
    }

    /**
     * POST /upload
     * Accepts multipart/form-data with parameters:
     * - imageId: identifier for the file (stored as eventsphere/{imageId})
     * - file: the binary file payload
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("imageId") String imageId,
            @RequestParam("file") MultipartFile file) throws IOException {

        logger.info("Received upload request for imageId: '{}', file: '{}'", imageId, file.getOriginalFilename());
        UploadResponse response = s3StorageService.uploadFile(imageId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /download/{imageId}
     * Retrieves the object from AWS S3 stored at eventsphere/{imageId}.
     * Returns the file bytes with the appropriate Content-Type.
     * Returns HTTP 404 if the object does not exist.
     */
    @GetMapping("/download/{imageId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable("imageId") String imageId) {
        logger.info("Received download request for imageId: '{}'", imageId);

        S3StorageService.S3FileResult result = s3StorageService.downloadFile(imageId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(result.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String encodedFilename = URLEncoder.encode(result.originalFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "inline; filename=\"" + result.originalFilename() + "\"; filename*=UTF-8''" + encodedFilename;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(result.contentLength());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        headers.set("X-Original-Filename", result.originalFilename());

        return new ResponseEntity<>(result.data(), headers, HttpStatus.OK);
    }
}
