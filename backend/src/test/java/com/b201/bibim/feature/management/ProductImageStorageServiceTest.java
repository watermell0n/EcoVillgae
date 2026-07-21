package com.b201.bibim.feature.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ProductImageStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesImageAsPublicUrlAndLoadsItAgain() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomEntity room = mock(RoomEntity.class);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        ProductImageStorageService service = new ProductImageStorageService(
                roomRepository, tempDirectory.toString()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.png", "image/png", new byte[]{1, 2, 3, 4}
        );

        var uploaded = service.store(teacherId, roomId, file);
        String filename = uploaded.imageUrl().substring(uploaded.imageUrl().lastIndexOf('/') + 1);
        var loaded = service.load(filename);

        assertTrue(uploaded.imageUrl().matches("/api/v1/product-images/[0-9a-f-]+\\.png"));
        assertEquals("image/png", loaded.mediaType().toString());
        assertEquals(4, loaded.resource().contentLength());
    }

    @Test
    void rejectsNonImageFilesWithFriendlyError() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomEntity room = mock(RoomEntity.class);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        ProductImageStorageService service = new ProductImageStorageService(
                roomRepository, tempDirectory.toString()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "not an image".getBytes()
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.store(teacherId, roomId, file)
        );

        assertEquals("INVALID_PRODUCT_IMAGE", exception.getCode());
        assertEquals("JPG, PNG, GIF, WEBP 등 일반 이미지 파일을 선택해 주세요.", exception.getMessage());
    }
}
