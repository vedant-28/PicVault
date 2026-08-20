package com.vedant.picvault.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.service.ImageService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/picvault")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @GetMapping("/test")
    public ResponseEntity<String> testController() {
        System.out.println("success");
        return ResponseEntity.ok("success");
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageMetadata> imageFileUpload(@RequestParam("file") MultipartFile file) {
        ImageMetadata imageMetadata = imageService.uploadImage(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(imageMetadata);
    }

}
