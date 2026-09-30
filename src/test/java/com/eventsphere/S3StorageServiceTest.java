package com.eventsphere;

import com.eventsphere.service.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3StorageService = new S3StorageService(s3Client);
        ReflectionTestUtils.setField(s3StorageService, "bucketName", "s3-test-01-navaneeth");
        ReflectionTestUtils.setField(s3StorageService, "folderPrefix", "eventsphere");
    }

    @Test
    void testBuildSafeKey_Standard() {
        String key1 = s3StorageService.buildSafeKey("1");
        assertEquals("eventsphere/1", key1);

        String key2 = s3StorageService.buildSafeKey("photo1");
        assertEquals("eventsphere/photo1", key2);

        String key3 = s3StorageService.buildSafeKey("document1");
        assertEquals("eventsphere/document1", key3);
    }

    @Test
    void testBuildSafeKey_LeadingSlashNormalized() {
        String key = s3StorageService.buildSafeKey("/photo1");
        assertEquals("eventsphere/photo1", key);
    }

    @Test
    void testBuildSafeKey_PathTraversalRejected() {
        assertThrows(IllegalArgumentException.class, () -> s3StorageService.buildSafeKey("../rootfile"));
        assertThrows(IllegalArgumentException.class, () -> s3StorageService.buildSafeKey(""));
        assertThrows(IllegalArgumentException.class, () -> s3StorageService.buildSafeKey("   "));
        assertThrows(IllegalArgumentException.class, () -> s3StorageService.buildSafeKey(null));
    }
}
