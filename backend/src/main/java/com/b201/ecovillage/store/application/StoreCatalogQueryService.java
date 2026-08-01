package com.b201.ecovillage.store.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.repository.CharacterCatalogRepository;
import com.b201.ecovillage.character.repository.ClassroomCharacterRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.store.domain.StoreProductStatus;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;

@Service
public class StoreCatalogQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final StoreProductRepository productRepository;
    private final StoreProductVersionRepository versionRepository;
    private final ClassroomCharacterRepository classroomCharacterRepository;
    private final CharacterCatalogRepository characterCatalogRepository;

    public StoreCatalogQueryService(
        ClassroomAuthorizationService authorizationService,
        StoreProductRepository productRepository,
        StoreProductVersionRepository versionRepository,
        ClassroomCharacterRepository classroomCharacterRepository,
        CharacterCatalogRepository characterCatalogRepository
    ) {
        this.authorizationService = authorizationService;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.classroomCharacterRepository = classroomCharacterRepository;
        this.characterCatalogRepository = characterCatalogRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreCatalogView> findPublished(String subject, UUID classroomId) {
        authorize(subject, classroomId);
        return productRepository
            .findAllByClassroomIdAndStatusOrderByName(
                classroomId,
                StoreProductStatus.PUBLISHED
            )
            .stream()
            .flatMap(product -> versionRepository
                .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
                    classroomId,
                    product.getId()
                )
                .stream()
                .filter(StoreProductVersion::isPurchasable)
                .map(version -> new StoreCatalogView(
                    product.getId(),
                    product.getName(),
                    product.getStatus().name(),
                    version.getVersion(),
                    product.getStateVersion(),
                    version.getUnitPrice(),
                    product.getProductKind().name(),
                    product.getProductKind() == StoreProductKind.CHARACTER
                        ? classroomCharacterRepository
                            .findByClassroomIdAndStoreProductId(
                                classroomId,
                                product.getId()
                            )
                            .flatMap(classroomCharacter -> characterCatalogRepository.findById(
                                classroomCharacter.getCharacterCatalogId()
                            ))
                            .map(character -> character.getCharacterCode())
                            .orElse(null)
                        : null
                )))
            .toList();
    }

    private void authorize(String subject, UUID classroomId) {
        if (subject == null || subject.isBlank()) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "인증이 필요합니다."
            );
        }
        try {
            UUID studentId = UUID.fromString(subject);
            authorizationService.authorize(
                subject,
                classroomId,
                ClassroomPermission.READ_OWN_STUDENT_DATA,
                studentId
            );
        } catch (IllegalArgumentException exception) {
            authorizationService.authorize(
                subject,
                classroomId,
                ClassroomPermission.READ_CONFIGURATION,
                null
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
