package com.b201.ecovillage.finance.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class DepositAccountQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final DepositAccountRepository accountRepository;
    private final FinancialProductVersionRepository versionRepository;
    private final FinancialProductRepository productRepository;
    private final DepositMaturityService maturityService;

    public DepositAccountQueryService(
        ClassroomAuthorizationService authorizationService,
        DepositAccountRepository accountRepository,
        FinancialProductVersionRepository versionRepository,
        FinancialProductRepository productRepository,
        DepositMaturityService maturityService
    ) {
        this.authorizationService = authorizationService;
        this.accountRepository = accountRepository;
        this.versionRepository = versionRepository;
        this.productRepository = productRepository;
        this.maturityService = maturityService;
    }

    public List<DepositAccountView> findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
        maturityService.matureDueAccounts(classroomId);
        return accountRepository
            .findAllByClassroomIdAndClassroomStudentIdOrderByOpenedAtDesc(
                classroomId,
                studentId
            )
            .stream()
            .map(account -> {
                FinancialProductVersion version = versionRepository
                    .findById(account.getProductVersionId())
                    .orElseThrow(() -> missing("The deposit product version is missing."));
                FinancialProduct product = productRepository
                    .findById(version.getFinancialProductId())
                    .orElseThrow(() -> missing("The deposit product is missing."));
                if (!product.getClassroomId().equals(classroomId)) {
                    throw missing("The deposit product classroom is inconsistent.");
                }
                return new DepositAccountView(
                    account.getId(),
                    product.getName(),
                    account.getStatus().name(),
                    version.getVersion(),
                    account.getStateVersion(),
                    account.getPrincipalAmount(),
                    account.getExpectedInterestAmount(),
                    account.maturityAmount(),
                    account.getOpenedAt(),
                    account.getMaturesAt(),
                    account.getClosedAt()
                );
            })
            .toList();
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }

    private static ClassroomOperationException missing(String message) {
        return new ClassroomOperationException(
            HttpStatus.CONFLICT,
            "FINANCIAL_DATA_INCONSISTENT",
            message
        );
    }
}
