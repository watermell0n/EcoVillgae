package com.b201.bibim.feature.management;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.feature.management.TeacherClassManagementDto.ProductImageUploadResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

    static final long MAX_IMAGE_BYTES = 50L * 1024L * 1024L;

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/avif", "avif"),
            Map.entry("image/bmp", "bmp"),
            Map.entry("image/tiff", "tiff"),
            Map.entry("image/heic", "heic"),
            Map.entry("image/heif", "heif"),
            Map.entry("image/x-icon", "ico"),
            Map.entry("image/vnd.microsoft.icon", "ico")
    );
    private static final Map<String, String> MEDIA_TYPES_BY_EXTENSION = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("avif", "image/avif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("ico", "image/x-icon")
    );
    private static final Set<String> SAFE_EXTENSIONS = Set.copyOf(MEDIA_TYPES_BY_EXTENSION.keySet());

    private final RoomRepository roomRepository;
    private final Path storageDirectory;

    public ProductImageStorageService(
            RoomRepository roomRepository,
            @Value("${app.product-images.directory:./data/product-images}") String storageDirectory
    ) {
        this.roomRepository = roomRepository;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public ProductImageUploadResponse store(
            UUID teacherId,
            UUID roomId,
            MultipartFile file
    ) {
        roomRepository.findById(roomId)
                .filter(room -> room.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "수업을 찾을 수 없습니다."
                ));

        if (file == null || file.isEmpty()) {
            throw invalidImage("등록할 이미지 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw invalidImage("이미지 파일은 50MB 이하로 선택해 주세요.");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw invalidImage("JPG, PNG, GIF, WEBP 등 일반 이미지 파일을 선택해 주세요.");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path destination = storageDirectory.resolve(filename).normalize();
        if (!destination.startsWith(storageDirectory)) {
            throw invalidImage("올바른 이미지 파일명을 확인해 주세요.");
        }

        try {
            Files.createDirectories(storageDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "IMAGE_UPLOAD_FAILED",
                    "이미지 파일을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return new ProductImageUploadResponse("/api/v1/product-images/" + filename);
    }

    public StoredProductImage load(String filename) {
        if (filename == null || !filename.matches("[0-9a-fA-F-]+\\.[a-zA-Z0-9]+")) {
            throw imageNotFound();
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!SAFE_EXTENSIONS.contains(extension)) {
            throw imageNotFound();
        }
        Path file = storageDirectory.resolve(filename).normalize();
        if (!file.startsWith(storageDirectory) || !Files.isRegularFile(file)) {
            throw imageNotFound();
        }
        Resource resource = new FileSystemResource(file);
        return new StoredProductImage(resource, MediaType.parseMediaType(MEDIA_TYPES_BY_EXTENSION.get(extension)));
    }

    private ApiException invalidImage(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_IMAGE", message);
    }

    private ApiException imageNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "상품 이미지를 찾을 수 없습니다.");
    }

    public record StoredProductImage(Resource resource, MediaType mediaType) {
    }
}
