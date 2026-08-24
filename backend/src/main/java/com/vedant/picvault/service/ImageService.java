package com.vedant.picvault.service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.repository.ImageMetadataRepository;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageService {
    private final MinioClient minioClient;
    private final ImageMetadataRepository imageMetadataRepository;

    @Value("${picvault.minio.bucket}")
    private String bucketName;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpg",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif"
    );

    @Transactional
    public List<ImageMetadata> uploadImage(MultipartFile[] files) {

        long totalArraySizeBytes = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        long maxAllowedMegaBytes = 50 * 1024 * 1024; // Total array size 
        if(files.length > 20 || totalArraySizeBytes > maxAllowedMegaBytes) {
            throw new IllegalArgumentException("File upload limit reached. Please upload 20 or less files of total size 50MB");
        }
        
        List<ImageMetadata> savedImageMetadatas = new ArrayList<>();

        for(MultipartFile file : files) {
            // file validation for content type
            validateImage(file);

            // Unique storage key generatrion
            //String contentType = file.getContentType();
            String storageKey = UUID.randomUUID().toString();

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

            savedImageMetadatas.add(imageMetadataRepository.save(imageMetadata));
        }

        return savedImageMetadatas;
    }

    @Transactional
    public void deleteImage(UUID id) {
        // Delete metadata from DB
        ImageMetadata metadata = imageMetadataRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Specified image with id: " + id + " not found"));
        imageMetadataRepository.delete(metadata);

        // Delete Image from MinIO
        String storageKey = metadata.getStorageKey();
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(storageKey)
                .build()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Image metadata removed from DB, failed to remove image from MinIO." + e.getMessage());
        }
    }

    @Transactional
    public void deleteAllImage() throws Exception {
        boolean isMetadataEmpty = imageMetadataRepository.count() == 0;
        boolean isMinioBucketEmpty = isMinioBucketEmpty(bucketName);
        if (!isMetadataEmpty && !isMinioBucketEmpty) {
            imageMetadataRepository.deleteAll();
            try {
                deleteAllMinioItemsInSingleBucket(bucketName);
            } catch (Exception e) {
                throw new Exception("Something went wrong while deleting image files from minio. Please try again.");
            }
        }
        else {
            throw new Exception("Something went wrong while deleting image files. Please try again.");
        }
    }

    // --------------------------------------Helper methods-----------------------------------------
    // Custom reuable validation for incoming file
    private void validateImage(MultipartFile file) {
        if(file.isEmpty()) throw new IllegalArgumentException("Empty file uploaded.");

        String contentType = file.getContentType();
        if(contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file type; only image files are allowed.");
        }
    }

    // Custom reusable validation for Minio bucket (non-empty)
    private boolean isMinioBucketEmpty(String bucketName) throws MinioException {
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            return false;

        } catch (ErrorResponseException e) {
            String errorCode = e.errorResponse().code();
            if ("BucketNotEmpty".equals(errorCode)) { // "BucketNotEmpty" means bucket exists and has items
                return false;
            }
            if ("NoSuchBucket".equals(errorCode)) { // "NoSuchBucket" means the bucket does not exist
                return true;
            }
            throw new RuntimeException("MinIO error occurred: " + errorCode, e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected MinIO connection failure", e);
        }
    }

    private void deleteAllMinioItemsInSingleBucket(String bucketName) throws Exception {
        Iterable<Result<Item>> resultSet = minioClient.listObjects(
            ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()
        );
        for(Result<Item> result : resultSet) {
            String objectName = result.get().objectName();
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build()
            );
        }
    }
}
