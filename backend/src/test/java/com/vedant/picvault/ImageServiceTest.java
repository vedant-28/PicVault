package com.vedant.picvault;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.vedant.picvault.dto.ImageDto;
import com.vedant.picvault.dto.ImageResourceDto;
import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.repository.ImageMetadataRepository;
import com.vedant.picvault.service.ImageService;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService(minioClient, imageMetadataRepository);
        ReflectionTestUtils.setField(imageService, "bucketName", "test-bucket");
    }

    // ---------- uploadImage ----------

    @Test
    void uploadImage_storesEachFileAndSavesMetadata() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "cat.jpg", "image/jpeg", "content-1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "dog.png", "image/png", "content-2".getBytes());
        when(imageMetadataRepository.save(any(ImageMetadata.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<ImageMetadata> result = imageService.uploadImage(new MultipartFile[]{file1, file2});

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOriginalFilename()).isEqualTo("cat.jpg");
        assertThat(result.get(0).getStorageKey()).endsWith("_cat.jpg");
        assertThat(result.get(1).getOriginalFilename()).isEqualTo("dog.png");
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
        verify(imageMetadataRepository, times(2)).save(any(ImageMetadata.class));
    }

    @Test
    void uploadImage_rejectsMoreThan20Files() {
        MultipartFile[] files = new MultipartFile[21];
        for (int i = 0; i < 21; i++) {
            files[i] = new MockMultipartFile("files", "img" + i + ".jpg", "image/jpeg", "x".getBytes());
        }

        assertThatThrownBy(() -> imageService.uploadImage(files))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("20");
        verifyNoInteractions(minioClient);
    }

    @Test
    void uploadImage_rejectsWhenAnySingleFileExceeds50MB() {
        MultipartFile tooLarge = oversizedStub("huge.jpg", 51L * 1024 * 1024);

        assertThatThrownBy(() -> imageService.uploadImage(new MultipartFile[]{tooLarge}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("50MB");
        verifyNoInteractions(minioClient);
    }

    @Test
    void uploadImage_rejectsUnsupportedContentType() {
        MockMultipartFile pdf = new MockMultipartFile("files", "doc.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> imageService.uploadImage(new MultipartFile[]{pdf}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported file type");
        verifyNoInteractions(minioClient);
    }

    @Test
    void uploadImage_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> imageService.uploadImage(new MultipartFile[]{empty}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Empty file");
    }

    @Test
    void uploadImage_sanitizesPathTraversalInFilename() throws Exception {
        MockMultipartFile malicious = new MockMultipartFile(
            "files", "../../etc/passwd", "image/jpeg", "content".getBytes());
        when(imageMetadataRepository.save(any(ImageMetadata.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<ImageMetadata> result = imageService.uploadImage(new MultipartFile[]{malicious});

        assertThat(result.get(0).getStorageKey()).doesNotContain("..").doesNotContain("/etc/");
    }

    @Test
    void uploadImage_wrapsMinioFailureAsRuntimeException() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "cat.jpg", "image/jpeg", "content".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> imageService.uploadImage(new MultipartFile[]{file}))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to store file in minio");
        verifyNoInteractions(imageMetadataRepository);
    }

    private MultipartFile oversizedStub(String filename, long size) {
        return new MockMultipartFile("files", filename, "image/jpeg", new byte[1]) {
            @Override
            public long getSize() {
                return size; // pretend it's this big without actually allocating it
            }
        };
    }

    // ---------- deleteImage ----------

    @Test
    void deleteImage_removesFromRepositoryAndMinio() throws Exception {
        UUID id = UUID.randomUUID();
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(id);
        metadata.setStorageKey("abc-cat.jpg");
        when(imageMetadataRepository.findById(id)).thenReturn(Optional.of(metadata));

        imageService.deleteImage(id);

        verify(imageMetadataRepository).delete(metadata);
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteImage_throwsWhenIdNotFound() {
        UUID id = UUID.randomUUID();
        when(imageMetadataRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.deleteImage(id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(id.toString());
        verifyNoInteractions(minioClient);
    }

    // ---------- deleteAllImage ----------

    @Test
    void deleteAllImage_clearsRepositoryAndMinioBucket() throws Exception {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("key-1.jpg");
        @SuppressWarnings("unchecked")
        Result<Item> result = mock(Result.class);
        when(result.get()).thenReturn(item);
        when(minioClient.listObjects(any())).thenReturn(List.of(result));

        imageService.deleteAllImage();

        verify(imageMetadataRepository).deleteAll();
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteAllImage_isIdempotent_noOpWhenAlreadyEmptyAndDoesNotThrow() {
        when(minioClient.listObjects(any())).thenReturn(List.of());

        // The bug this guards against: earlier logic errored on a second,
        // no-op call instead of succeeding — DELETE should be idempotent.
        imageService.deleteAllImage();

        verify(imageMetadataRepository).deleteAll();
        try {
            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- listAllImages ----------

    @Test
    void listAllImages_mapsEntitiesToDtosWithBuiltUrls() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ImageMetadata metadata = new ImageMetadata();
        metadata.setOriginalFilename("cat.jpg");
        metadata.setStorageKey("uuid_cat.jpg");
        metadata.setSizeBytes(12345L);
        metadata.setUploadedAt(Instant.now());
        Pageable pageable = PageRequest.of(0, 20);
        when(imageMetadataRepository.findAllByOrderByUploadedAtDesc(pageable))
            .thenReturn(new PageImpl<>(List.of(metadata)));

        Page<ImageDto> page = imageService.listAllImages(pageable);

        assertThat(page.getContent()).hasSize(1);
        ImageDto dto = page.getContent().get(0);
        assertThat(dto.filename()).isEqualTo("cat.jpg");
        assertThat(dto.url()).isEqualTo("http://localhost/picvault/images/uuid_cat.jpg");
        assertThat(dto.size()).isEqualTo(12345L);

        RequestContextHolder.resetRequestAttributes();
    }

    // ---------- serveImageUrls ----------

    @Test
    void serveImageUrls_returnsContentEtagAndContentType() throws Exception {
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.etag()).thenReturn("\"abc123\"");
        when(stat.contentType()).thenReturn("image/jpeg");
        when(minioClient.statObject(any())).thenReturn(stat);

        GetObjectResponse getResponse = mock(GetObjectResponse.class);
        when(getResponse.readAllBytes()).thenReturn("image-bytes".getBytes());
        when(minioClient.getObject(any())).thenReturn(getResponse);

        ImageResourceDto dto = imageService.serveImageUrls("uuid-cat.jpg");

        assertThat(dto.etag()).isEqualTo("\"abc123\"");
        assertThat(dto.contentType()).isEqualTo("image/jpeg");
        assertThat(new String(dto.content())).isEqualTo("image-bytes");
    }

    @Test
    void serveImageUrls_wrapsMinioFailureAsRuntimeException() throws Exception {
        when(minioClient.statObject(any())).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> imageService.serveImageUrls("missing.jpg"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }
}
