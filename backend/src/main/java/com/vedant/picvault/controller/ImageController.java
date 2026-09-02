package com.vedant.picvault.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.web.WebProperties.Resources.Cache.Cachecontrol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import com.vedant.picvault.dto.ImageDto;
import com.vedant.picvault.dto.ImageResourceDto;
import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.service.ImageService;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/picvault")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @GetMapping("/images")
    public ResponseEntity<Page<ImageDto>> listAllImagesAndUrls(
        @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(imageService.listAllImages(pageable));
    }

    @GetMapping("/images/{filename}")
    public ResponseEntity<byte[]> serveImageUrlsWithCachingHeaders(@PathVariable("filename") String filename, WebRequest request) {
        try {
            // Get image data & metadata
            ImageResourceDto imageResourceDto = imageService.serveImageUrls(filename);
            
            // Checking if browser has already cached version with etag, Spring sends 304 no modified automatically
            if(request.checkNotModified(imageResourceDto.etag())) return null;

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                    .eTag(imageResourceDto.etag())
                    .header("Content-Type", imageResourceDto.contentType())
                    .body(imageResourceDto.content());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageMetadata>> imageFileUpload(@RequestParam("files") MultipartFile[] files) {
        if(files == null || files.length == 0) return ResponseEntity.badRequest().build();

        List<ImageMetadata> imageMetadataList = imageService.uploadImage(files);
        return ResponseEntity.status(HttpStatus.CREATED).body(imageMetadataList);
    }
    
    @DeleteMapping("/images/{id}")
    public ResponseEntity<String> deleteImageFile(@PathVariable("id") UUID id) {
        imageService.deleteImage(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/images")
    public ResponseEntity<String> deleteAllImage() {
        imageService.deleteAllImage();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
