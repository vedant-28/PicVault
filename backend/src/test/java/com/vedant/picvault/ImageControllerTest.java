package com.vedant.picvault;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.vedant.picvault.controller.ImageController;
import com.vedant.picvault.dto.ImageDto;
import com.vedant.picvault.dto.ImageResourceDto;
import com.vedant.picvault.entity.ImageMetadata;
import com.vedant.picvault.service.ImageService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    @Test
    void uploadImages_returns201WithSavedMetadata() throws Exception {
        ImageMetadata saved = new ImageMetadata();
        saved.setId(UUID.randomUUID());
        saved.setOriginalFilename("cat.jpg");
        when(imageService.uploadImage(any())).thenReturn(List.of(saved));
        MockMultipartFile file = new MockMultipartFile("files", "cat.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart("/picvault/images").file(file))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].originalFilename").value("cat.jpg"));
    }

    @Test
    void uploadImages_returns400OnValidationFailure() throws Exception {
        when(imageService.uploadImage(any()))
            .thenThrow(new IllegalArgumentException("Unsupported file type; only image files are allowed."));
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/picvault/images").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Unsupported file type; only image files are allowed."));
    }

    @Test
    void listAllImagesAndUrls_returnsPagedDtos() throws Exception {
        ImageDto dto = new ImageDto(UUID.randomUUID(), "cat.jpg", "/picvault/images/uuid-cat.jpg", 12345L);
        when(imageService.listAllImages(any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/picvault/images"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].filename").value("cat.jpg"))
            .andExpect(jsonPath("$.content[0].url").value("/picvault/images/uuid-cat.jpg"));
    }

    @Test
    void serveImage_returnsBytesWithCacheHeadersAndEtag() throws Exception {
        ImageResourceDto image = new ImageResourceDto("bytes".getBytes(), "\"abc123\"", "image/jpeg");
        when(imageService.serveImageUrls("uuid-cat.jpg")).thenReturn(image);

        mockMvc.perform(get("/picvault/images/uuid-cat.jpg"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"abc123\""))
            .andExpect(header().string("Cache-Control", containsString("max-age=86400, public")))
            .andExpect(content().bytes("bytes".getBytes()));
    }

    @Test
    void serveImage_returns304WhenIfNoneMatchMatchesEtag() throws Exception {
        ImageResourceDto image = new ImageResourceDto("bytes".getBytes(), "\"abc123\"", "image/jpeg");
        when(imageService.serveImageUrls("uuid-cat.jpg")).thenReturn(image);

        mockMvc.perform(get("/picvault/images/uuid-cat.jpg").header("If-None-Match", "\"abc123\""))
            .andExpect(status().isNotModified());
    }

    @Test
    void deleteImage_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/picvault/images/{id}", id))
            .andExpect(status().isNoContent());

        verify(imageService).deleteImage(eq(id));
    }

    @Test
    void deleteImage_returns400WhenIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Specified image with id: " + id + " not found"))
            .when(imageService).deleteImage(id);

        mockMvc.perform(delete("/picvault/images/{id}", id))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAllImages_returns204_andIsCallableTwiceInARow() throws Exception {
        mockMvc.perform(delete("/picvault/images")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/picvault/images")).andExpect(status().isNoContent());

        verify(imageService, org.mockito.Mockito.times(2)).deleteAllImage();
    }
}
