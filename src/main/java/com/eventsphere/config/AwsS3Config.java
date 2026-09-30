package com.eventsphere.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsS3Config {

    private static final Logger logger = LoggerFactory.getLogger(AwsS3Config.class);

    @Value("${app.s3.region:us-east-1}")
    private String configuredRegion;

    /**
     * Initializes and configures the AWS S3Client bean.
     * Uses the AWS Default Credentials Provider Chain to read AWS_ACCESS_KEY_ID
     * and AWS_SECRET_ACCESS_KEY from the environment or system properties.
     * Credentials are NOT hardcoded.
     */
    @Bean
    public S3Client s3Client() {
        String regionStr = System.getenv("AWS_REGION");
        if (regionStr == null || regionStr.isBlank()) {
            regionStr = configuredRegion;
        }
        if (regionStr == null || regionStr.isBlank()) {
            regionStr = "us-east-1";
        }

        Region region = Region.of(regionStr.trim());
        logger.info("Initializing AWS S3Client with Region: {}", region);

        return S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
