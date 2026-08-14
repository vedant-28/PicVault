package com.vedant.picvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata row for one uploaded image. The actual bytes live in MinIO under
 * {@link #storageKey}; this row is what the list/gallery endpoints query.
 */
@Entity
@Table(name = "image_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Filename as the client sent it — for display only, never used as a path. */
    @Column(nullable = false)
    private String originalFilename;

    /** Generated, collision-proof object key used inside the MinIO bucket. */
    @Column(nullable = false, unique = true)
    private String storageKey;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private Instant uploadedAt;
}
