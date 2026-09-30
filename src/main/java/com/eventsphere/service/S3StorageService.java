package com.eventsphere.service;

import com.eventsphere.dto.UploadResponse;
import com.eventsphere.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;

    @Value("${app.s3.bucket-name:s3-test-01-navaneeth}")
    private String bucketName;

    @Value("${app.s3.folder:eventsphere}")
    private String folderPrefix;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Constructs a safe S3 object key strictly under the configured folder prefix (eventsphere/).
     * Guarantees that every object is stored at eventsphere/{imageId}.
     */
    public String buildSafeKey(String imageId) {
        if (imageId == null || imageId.trim().isEmpty()) {
            throw new IllegalArgumentException("imageId cannot be empty or null.");
        }

        String cleanId = imageId.trim().replaceAll("^/+", "");
        if (cleanId.contains("..") || cleanId.isEmpty()) {
            throw new IllegalArgumentException("Invalid imageId: path traversal sequences are not allowed.");
        }

        String cleanFolder = (folderPrefix != null ? folderPrefix : "eventsphere")
                .replaceAll("^/+|/+$", "");

        return cleanFolder + "/" + cleanId;
    }

    /**
     * Uploads a file to AWS S3 under eventsphere/{imageId}.
     */
    public UploadResponse uploadFile(String imageId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be empty or missing.");
        }

        String key = buildSafeKey(imageId);
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        logger.info("Uploading file to S3. Bucket: '{}', Key: '{}', ContentType: '{}', Size: {} bytes",
                bucketName, key, contentType, file.getSize());

        Map<String, String> metadata = new HashMap<>();
        if (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()) {
            metadata.put("original-filename", file.getOriginalFilename());
        }
        metadata.put("content-type", contentType);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .metadata(metadata)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        logger.info("Successfully uploaded object to S3 at key: '{}'", key);

        return new UploadResponse(
                imageId,
                key,
                file.getOriginalFilename(),
                contentType,
                file.getSize(),
                "File successfully uploaded to AWS S3 under key '" + key + "'"
        );
    }

    /**
     * Downloads an object from AWS S3 under eventsphere/{imageId}.
     * Throws ResourceNotFoundException (404) if the object does not exist.
     */
    public S3FileResult downloadFile(String imageId) {
        String key = buildSafeKey(imageId);

        logger.info("Fetching object from S3. Bucket: '{}', Key: '{}'", bucketName, key);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest)) {
            GetObjectResponse response = s3Stream.response();
            byte[] bytes = s3Stream.readAllBytes();

            String contentType = response.contentType();
            if (contentType == null || contentType.isBlank() || "binary/octet-stream".equalsIgnoreCase(contentType)) {
                if (response.metadata() != null && response.metadata().containsKey("content-type")) {
                    contentType = response.metadata().get("content-type");
                } else {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }
            }

            String originalFilename = null;
            if (response.metadata() != null && response.metadata().containsKey("original-filename")) {
                originalFilename = response.metadata().get("original-filename");
            }
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = imageId;
            }

            logger.info("Successfully fetched S3 object: key='{}', contentType='{}', length={} bytes",
                    key, contentType, bytes.length);

            return new S3FileResult(bytes, contentType, originalFilename, response.contentLength() != null ? response.contentLength() : bytes.length);

        } catch (NoSuchKeyException e) {
            logger.warn("Object not found in S3 for key: '{}'", key);
            throw new ResourceNotFoundException("File with ID '" + imageId + "' not found at key '" + key + "' in S3 bucket '" + bucketName + "'.");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                logger.warn("Object returned 404 from S3 for key: '{}'", key);
                throw new ResourceNotFoundException("File with ID '" + imageId + "' not found at key '" + key + "' in S3 bucket '" + bucketName + "'.");
            }
            logger.error("AWS S3 error while reading key '{}': {}", key, e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("IO error reading S3 stream for key '{}': {}", key, e.getMessage());
            throw new RuntimeException("Failed to read S3 object data: " + e.getMessage(), e);
        }
    }

    public record S3FileResult(byte[] data, String contentType, String originalFilename, long contentLength) {}
}
