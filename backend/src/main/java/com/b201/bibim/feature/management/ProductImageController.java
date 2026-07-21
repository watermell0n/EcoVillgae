package com.b201.bibim.feature.management;

import java.time.Duration;

import com.b201.bibim.feature.management.ProductImageStorageService.StoredProductImage;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-images")
public class ProductImageController {

    private final ProductImageStorageService imageStorageService;

    public ProductImageController(ProductImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @GetMapping("/{filename:.+}")
    ResponseEntity<?> image(@PathVariable String filename) {
        StoredProductImage image = imageStorageService.load(filename);
        return ResponseEntity.ok()
                .contentType(image.mediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.resource());
    }
}
