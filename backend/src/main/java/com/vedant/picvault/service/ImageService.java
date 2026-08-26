package com.vedant.picvault.service;

import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.vedant.picvault.dto.ImageDto;
import com.vedant.picvault.dto.ImageResourceDto;
import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.repository.ImageMetadataRepository;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.Http.Method;
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
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

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
            String storageKey = UUID.randomUUID().toString() + "_" + sanitizeFileName(file.getOriginalFilename());

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
    public void deleteAllImage() {
        imageMetadataRepository.deleteAll();
        deleteAllMinioItemsInSingleBucket(bucketName);
    }

    public Page<ImageDto> listAllImages(Pageable pageable) {
        return imageMetadataRepository.findAllByOrderByUploadedAtDesc(pageable)
                .map(metadata -> new ImageDto(
                    metadata.getOriginalFilename(),
                    buildImageUrl(metadata.getStorageKey()),
                    metadata.getSizeBytes()
                ));
    }

    public ImageResourceDto serveImageUrls(String filename) {
        try {
            // Fetching object metadata from minio to get etag (MD5 hash)
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(filename).build()
            );
            // Returning file contents in stream form, etag & contentType via DTO to controller;
            // To serve image URLs with appropriate caching headers
            try(InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(filename).build())) {
                byte[] media = stream.readAllBytes();
                return new ImageResourceDto(media, stat.etag(), stat.contentType());
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
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

    private void deleteAllMinioItemsInSingleBucket(String bucketName) {
        try {
            Iterable<Result<Item>> resultSet = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()
            );
            for(Result<Item> result : resultSet) {
                String objectName = result.get().objectName();
                minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete images from minio. " + e.getMessage());
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) return "unnamed";
        String name = Paths.get(originalFileName).getFileName().toString();
        name = UNSAFE_CHARS.matcher(name).replaceAll("");
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }

    private String buildImageUrl(String storageKey) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/picvault/images/{filename}")
            .buildAndExpand(storageKey)
            .toUriString();
    }
}
