package com.vedant.picvault.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${picvault.minio.url}")
    private String url;

    @Value("${picvault.minio.access-key}")
    private String accessKey;

    @Value("${picvault.minio.secret-key}")
    private String secretKey;

    @Value("${picvault.minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Runs once on startup. Local/dev convenience only — in the docker-compose
     * setup the minio-init service already creates the bucket, so this is a
     * safety net (and what makes `./gradlew bootRun` work without compose too).
     */
    @Bean
    ApplicationRunner ensureBucketExists(MinioClient minioClient) {
        return args -> {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        };
    }
}
