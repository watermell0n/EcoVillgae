package com.b201.ecovillage.store.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.domain.CharacterCatalog;
import com.b201.ecovillage.character.domain.ClassroomCharacter;
import com.b201.ecovillage.character.repository.CharacterCatalogRepository;
import com.b201.ecovillage.character.repository.ClassroomCharacterRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductStatus;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;

@Service
public class StoreCatalogQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final StoreProductRepository productRepository;
    private final StoreProductVersionRepository versionRepository;
    private final StoreStockRepository stockRepository;
    private final ClassroomCharacterRepository classroomCharacterRepository;
    private final CharacterCatalogRepository characterCatalogRepository;
    private final UserAccountRepository userAccountRepository;

    public StoreCatalogQueryService(
        ClassroomAuthorizationService authorizationService,
        StoreProductRepository productRepository,
        StoreProductVersionRepository versionRepository,
        StoreStockRepository stockRepository,
        ClassroomCharacterRepository classroomCharacterRepository,
        CharacterCatalogRepository characterCatalogRepository,
        UserAccountRepository userAccountRepository
    ) {
        this.authorizationService = authorizationService;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.stockRepository = stockRepository;
        this.classroomCharacterRepository = classroomCharacterRepository;
        this.characterCatalogRepository = characterCatalogRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreCatalogView> findVisible(String subject, UUID classroomId) {
        boolean teacher = authorize(subject, classroomId);
        var products = teacher
            ? productRepository.findAllByClassroomIdOrderByName(classroomId)
            : productRepository.findAllByClassroomIdAndStatusOrderByName(
                classroomId,
                StoreProductStatus.PUBLISHED
            );
        return products
            .stream()
            .flatMap(product -> versionRepository
                .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
                    classroomId,
                    product.getId()
                )
                .stream()
                .filter(version -> teacher || version.isPurchasable())
                .map(version -> {
                    // 캐릭터 상품은 교실 캐릭터 행을 한 번만 읽는다. characterCode와
                    // sortOrder가 같은 행에서 나오므로 조회를 두 번 하지 않는다.
                    ClassroomCharacter classroomCharacter =
                        product.getProductKind() == StoreProductKind.CHARACTER
                            ? classroomCharacterRepository
                                .findByClassroomIdAndStoreProductId(classroomId, product.getId())
                                .orElse(null)
                            : null;
                    return new StoreCatalogView(
                        product.getId(),
                        product.getName(),
                        product.getStatus().name(),
                        version.getVersion(),
                        product.getStateVersion(),
                        version.getUnitPrice(),
                        version.getVatRateBps(),
                        stockRepository
                            .findByClassroomIdAndStoreProductId(classroomId, product.getId())
                            .map(stock -> stock.getAvailableQuantity())
                            .orElse(0),
                        product.getProductKind().name(),
                        classroomCharacter == null
                            ? null
                            : characterCatalogRepository
                                .findById(classroomCharacter.getCharacterCatalogId())
                                .map(CharacterCatalog::getCharacterCode)
                                .orElse(null),
                        product.getDescription(),
                        classroomCharacter == null ? null : classroomCharacter.getSortOrder(),
                        product.getImageUrl()
                    );
                }))
            .toList();
    }

    private boolean authorize(String subject, UUID classroomId) {
        if (subject == null || subject.isBlank()) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "인증이 필요합니다."
            );
        }
        if (userAccountRepository.findByExternalSubject(subject).isPresent()) {
            authorizationService.authorize(
                subject,
                classroomId,
                ClassroomPermission.READ_CONFIGURATION,
                null
            );
            return true;
        }
        try {
            UUID studentId = UUID.fromString(subject);
            authorizationService.authorize(
                subject,
                classroomId,
                ClassroomPermission.READ_OWN_STUDENT_DATA,
                studentId
            );
            return false;
        } catch (IllegalArgumentException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "유효한 인증 주체를 확인할 수 없습니다."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
