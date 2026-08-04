package com.b201.ecovillage.finance.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.identity.authorization.AuthorizationDecision;
import com.b201.ecovillage.identity.authorization.ClassroomActorRole;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class FinancialProductQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomModuleRepository moduleRepository;
    private final FinancialProductRepository productRepository;
    private final FinancialProductVersionRepository versionRepository;

    public FinancialProductQueryService(
        ClassroomAuthorizationService authorizationService,
        ClassroomModuleRepository moduleRepository,
        FinancialProductRepository productRepository,
        FinancialProductVersionRepository versionRepository
    ) {
        this.authorizationService = authorizationService;
        this.moduleRepository = moduleRepository;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductView> findVisible(String subject, UUID classroomId) {
        UUID targetStudentId = studentId(subject);
        AuthorizationDecision decision = authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_FINANCIAL_PRODUCTS,
            targetStudentId
        );
        boolean student = decision.actorType() == ClassroomActorRole.STU;
        return productRepository.findAllByClassroomIdOrderByName(classroomId)
            .stream()
            .filter(product -> !student || product.isPublished())
            .filter(product -> !student || moduleRepository
                .existsByClassroomIdAndModuleCodeAndStatus(
                    classroomId,
                    product.getProductType().name(),
                    "ACTIVE"
                ))
            .flatMap(product -> (student
                ? versionRepository
                    .findTopByClassroomIdAndFinancialProductIdAndStatusOrderByVersionDesc(
                        classroomId,
                        product.getId(),
                        "PUBLISHED"
                    )
                : versionRepository
                    .findTopByClassroomIdAndFinancialProductIdOrderByVersionDesc(
                        classroomId,
                        product.getId()
                    )
            ).stream().map(version -> view(product, version)))
            .toList();
    }

    private static FinancialProductView view(
        FinancialProduct product,
        com.b201.ecovillage.finance.domain.FinancialProductVersion version
    ) {
        return new FinancialProductView(
            product.getId(),
            product.getName(),
            product.getStatus(),
            version.getVersion(),
            product.getStateVersion(),
            product.getProductType().name(),
            version.getTermUnits(),
            version.getInterestRateBps(),
            version.getEarlyTerminationRateBps(),
            version.getInstallmentCount(),
            version.getInstallmentAmount(),
            version.getMinimumAmount(),
            version.getMaximumAmount()
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
