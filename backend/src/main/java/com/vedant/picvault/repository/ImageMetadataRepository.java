package com.vedant.picvault.repository;

import com.vedant.picvault.entity.ImageMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImageMetadataRepository extends JpaRepository<ImageMetadata, UUID> {

    Page<ImageMetadata> findAllByOrderByUploadedAtDesc(Pageable pageable);
}
