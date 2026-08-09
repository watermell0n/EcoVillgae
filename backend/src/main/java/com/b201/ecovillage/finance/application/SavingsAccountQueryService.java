package com.b201.ecovillage.finance.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.finance.repository.SavingsAccountRepository;
import com.b201.ecovillage.finance.repository.SavingsInstallmentRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class SavingsAccountQueryService {
    private final ClassroomAuthorizationService authorizationService;
    private final SavingsAccountRepository accountRepository;
    private final FinancialProductVersionRepository versionRepository;
    private final FinancialProductRepository productRepository;
    private final SavingsInstallmentRepository installmentRepository;

    public SavingsAccountQueryService(ClassroomAuthorizationService authorizationService,
        SavingsAccountRepository accountRepository, FinancialProductVersionRepository versionRepository,
        FinancialProductRepository productRepository, SavingsInstallmentRepository installmentRepository) {
        this.authorizationService = authorizationService;
        this.accountRepository = accountRepository;
        this.versionRepository = versionRepository;
        this.productRepository = productRepository;
        this.installmentRepository = installmentRepository;
    }

    @Transactional(readOnly = true)
    public List<SavingsAccountView> findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(subject, classroomId, ClassroomPermission.READ_OWN_STUDENT_DATA, studentId);
        return accountRepository.findAllByClassroomIdAndClassroomStudentIdOrderByOpenedAtDesc(classroomId, studentId)
            .stream().map(account -> {
                FinancialProductVersion version = versionRepository.findById(account.getProductVersionId())
                    .orElseThrow(() -> missing("The savings product version is missing."));
                FinancialProduct product = productRepository.findById(version.getFinancialProductId())
                    .orElseThrow(() -> missing("The savings product is missing."));
                var nextInstallment = installmentRepository
                    .findAllByClassroomIdAndSavingsAccountIdOrderByDueAt(classroomId, account.getId())
                    .stream()
                    .filter(installment -> List.of("SCHEDULED", "MISSED").contains(installment.getStatus()))
                    .findFirst()
                    .orElse(null);
                return new SavingsAccountView(account.getId(), product.getId(), product.getName(), account.getStatus().name(),
                    account.getInstallmentCount(), account.getPaidInstallmentCount(), account.getInstallmentAmount(),
                    account.getPrincipalBalance(), account.getExpectedInterestAmount(), version.getInterestRateBps(),
                    account.getOpenedAt(), account.getMaturesAt(),
                    nextInstallment == null ? null : nextInstallment.getDueAt(),
                    nextInstallment == null ? null : nextInstallment.getStatus(),
                    account.getStateVersion());
            }).toList();
    }

    private static UUID studentId(String subject) {
        try { return UUID.fromString(subject); }
        catch (RuntimeException exception) { throw missing("A valid student session is required."); }
    }

    private static ClassroomOperationException missing(String message) {
        return new ClassroomOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
