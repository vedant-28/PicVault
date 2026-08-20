package com.vedant.picvault.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.repository.ImageMetadataRepository;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageService {
    private final MinioClient minioClient;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${picvault.minio.bucket}")
    private String bucketName;

    public ImageMetadata uploadImage(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Empty file uploaded");

        // Unique storage key generatrion
        String fileName = file.getOriginalFilename();
        String storageKey = UUID.randomUUID().toString() + fileName;

        // Save image by streaming in minio
        try(InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                .bucket(bucketName)
                .object(storageKey)
                .stream(inputStream, file.getSize(), (long) -1)
                .contentType(file.getContentType())
                .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file in minio: " + e.getMessage());
        }

        // Store image metadata in MySQL
        ImageMetadata imageMetadata = new ImageMetadata();
        imageMetadata.setOriginalFilename(file.getOriginalFilename());
        imageMetadata.setStorageKey(storageKey);
        imageMetadata.setContentType(file.getContentType());
        imageMetadata.setSizeBytes(file.getSize());
        imageMetadata.setUploadedAt(Instant.now());

        return imageMetadataRepository.save(imageMetadata);
    }

}
