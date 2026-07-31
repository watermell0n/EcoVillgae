package com.b201.ecovillage.store.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:store-catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class StoreCatalogPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Autowired
    private StoreProductRepository productRepository;

    @Autowired
    private StoreProductVersionRepository versionRepository;

    @Autowired
    private StoreStockRepository stockRepository;

    @Test
    void savesAndFindsCurrentVersionAndStock() {
        UUID classroomId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StoreProduct product = StoreProduct.draft(
            productId,
            classroomId,
            "연필",
            "교실에서 받는 실제 상품",
            StoreProductKind.PHYSICAL,
            UUID.randomUUID(),
            NOW
        );
        product.publish(NOW);
        productRepository.save(product);
        versionRepository.save(
            StoreProductVersion.publish(
                UUID.randomUUID(),
                classroomId,
                productId,
                1,
                StoreProductKind.PHYSICAL,
                300,
                1_000,
                true,
                Map.of("sku", "PENCIL"),
                NOW
            )
        );
        stockRepository.save(StoreStock.initialize(productId, classroomId, 20, NOW));

        StoreProductVersion current = versionRepository
            .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
                classroomId,
                productId
            )
            .orElseThrow();

        assertEquals(1, current.getVersion());
        assertEquals(300, current.getUnitPrice());
        assertTrue(
            stockRepository.findByClassroomIdAndStoreProductId(classroomId, productId)
                .isPresent()
        );
    }
}
