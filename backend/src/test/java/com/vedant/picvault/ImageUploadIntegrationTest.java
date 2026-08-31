package com.vedant.picvault;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.vedant.picvault.dto.ImageDto;
import com.vedant.picvault.repository.ImageMetadataRepository;

import io.minio.MinioClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real upload -> store -> retrieve flow against real Postgres
 * and real MinIO containers —> MinIO integration works end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class ImageUploadIntegrationTest {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:latest")
    .withDatabaseName("picvault")
    .withUsername("root")
    .withPassword("root");

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:latest");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("picvault.minio.url", minio::getS3URL);
        registry.add("picvault.minio.access-key", minio::getUserName);
        registry.add("picvault.minio.secret-key", minio::getPassword);
        registry.add("picvault.minio.bucket", () -> "picvault-images-it");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ImageMetadataRepository imageMetadataRepository;

    @AfterEach
    void cleanUp() {
        restTemplate.delete("/picvault/images");
    }

    @Test
    void uploadThenListThenServe_roundTripsTheActualBytes() {
        byte[] originalBytes = "fake-jpeg-bytes".getBytes();
        ResponseEntity<List> uploadResponse = uploadOneFile("test-photo.jpg", originalBytes);

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(uploadResponse.getBody()).hasSize(1);
        assertThat(imageMetadataRepository.count()).isEqualTo(1);

        String storageKey = imageMetadataRepository.findAll().get(0).getStorageKey();
        assertThat(storageKey).endsWith("_test-photo.jpg");

        ResponseEntity<Map> listResponse = restTemplate.getForEntity("/picvault/images", Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) listResponse.getBody().get("content");
        assertThat(content).hasSize(1);

        // The real proof the MinIO integration works: bytes served back out
        // match exactly what was uploaded, not just that a DB row exists.
        ResponseEntity<byte[]> serveResponse = restTemplate.getForEntity("/picvault/images/" + storageKey, byte[].class);
        assertThat(serveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(serveResponse.getBody()).isEqualTo(originalBytes);
        assertThat(serveResponse.getHeaders().getCacheControl()).contains("max-age=86400, public");
        assertThat(serveResponse.getHeaders().getETag()).isNotBlank();
    }

    @Test
    void deleteById_removesFromBothMysqlAndMinio() {
        ResponseEntity<List> uploadResponse = uploadOneFile("to-delete.jpg", "bytes".getBytes());
        Map<?, ?> uploaded = (Map<?, ?>) uploadResponse.getBody().get(0);
        String id = (String) uploaded.get("id");
        String storageKey = imageMetadataRepository.findAll().get(0).getStorageKey();

        restTemplate.delete("/picvault/images/" + id);

        assertThat(imageMetadataRepository.count()).isEqualTo(0);
        // Confirm the object is actually gone from MinIO too, not just Postgres.
        ResponseEntity<byte[]> afterDelete = restTemplate.getForEntity("/picvault/images/" + storageKey, byte[].class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void deleteAll_isIdempotent_secondCallOnAnEmptyVaultAlsoSucceeds() {
        restTemplate.delete("/picvault/images");

        ResponseEntity<Void> second = restTemplate.exchange("/picvault/images", HttpMethod.DELETE, null, Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void rejectsUploadWithUnsupportedContentType() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", namedResource("not-an-image.txt", "hello".getBytes()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/picvault/images", new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<List> uploadOneFile(String filename, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", namedResource(filename, bytes));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForEntity("/picvault/images", new HttpEntity<>(body, headers), List.class);
    }

    private ByteArrayResource namedResource(String filename, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
