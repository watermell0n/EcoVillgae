package com.b201.ecovillage.finance.api;

import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.application.DepositAccountQueryService;
import com.b201.ecovillage.finance.application.LoanApplicationService;
import com.b201.ecovillage.finance.application.DepositAccountResult;
import com.b201.ecovillage.finance.application.DepositSubscriptionCommand;
import com.b201.ecovillage.finance.application.DepositSubscriptionService;
import com.b201.ecovillage.finance.application.DepositTerminationCommand;
import com.b201.ecovillage.finance.application.DepositTerminationService;
import com.b201.ecovillage.finance.application.FinancialProductCommand;
import com.b201.ecovillage.finance.application.FinancialProductCommandService;
import com.b201.ecovillage.finance.application.FinancialResourceResult;
import com.b201.ecovillage.finance.application.FinancialProductQueryService;
import com.b201.ecovillage.finance.application.SavingsAccountQueryService;
import com.b201.ecovillage.finance.application.SavingsSubscriptionCommand;
import com.b201.ecovillage.finance.application.SavingsSubscriptionService;

@RestController
@RequestMapping("/classrooms/{classroomId}")
public class FinanceController {

    private final FinancialProductCommandService productService;
    private final DepositSubscriptionService depositService;
    private final DepositTerminationService terminationService;
    private final DepositAccountQueryService queryService;
    private final SavingsSubscriptionService savingsService;
    private final SavingsAccountQueryService savingsQueryService;
    private final FinancialProductQueryService productQueryService;
    private final LoanApplicationService loanApplicationService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public FinanceController(
        FinancialProductCommandService productService,
        DepositSubscriptionService depositService,
        DepositTerminationService terminationService,
        DepositAccountQueryService queryService,
        SavingsSubscriptionService savingsService,
        SavingsAccountQueryService savingsQueryService,
        FinancialProductQueryService productQueryService,
        LoanApplicationService loanApplicationService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.productService = productService;
        this.depositService = depositService;
        this.terminationService = terminationService;
        this.queryService = queryService;
        this.savingsService = savingsService;
        this.savingsQueryService = savingsQueryService;
        this.productQueryService = productQueryService;
        this.loanApplicationService = loanApplicationService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/financial-products")
    public ResponseEntity<ApiResponse<FinancialProductsResponse>> findProducts(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        List<FinancialProductViewResponse> products = productQueryService
            .findVisible(subject(principal), classroomId)
            .stream()
            .map(view -> new FinancialProductViewResponse(
                view.resourceId(),
                view.label(),
                view.status(),
                view.version(),
                view.stateVersion(),
                view.productType(),
                view.termUnits(),
                view.interestRateBps(),
                view.earlyTerminationRateBps(),
                view.installmentCount(),
                view.installmentAmount(),
                view.minimumAmount(),
                view.maximumAmount()
            ))
            .toList();
        return response(
            HttpStatus.OK,
            new FinancialProductsResponse(products),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/financial-product-commands")
    public ResponseEntity<ApiResponse<FinancialResourceResponse>> commandProduct(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody FinancialProductCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        FinancialResourceResult result = productService.execute(
            new FinancialProductCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.command(),
                request.productId(),
                request.productType(),
                request.name(),
                request.termUnits(),
                request.interestRateBps(),
                request.earlyTerminationRateBps(),
                request.installmentCount(),
                request.installmentAmount(),
                request.minimumAmount(),
                request.maximumAmount(),
                request.expectedProductVersion()
            )
        );
        return response(
            HttpStatus.OK,
            new FinancialResourceResponse(
                result.financialResourceId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/deposit-accounts")
    public ResponseEntity<ApiResponse<FinancialResourceResponse>> subscribeDeposit(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody DepositSubscriptionRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        DepositAccountResult result = depositService.subscribe(
            new DepositSubscriptionCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.productId(),
                request.principal(),
                request.expectedProductVersion()
            )
        );
        return response(
            HttpStatus.CREATED,
            new FinancialResourceResponse(
                result.accountId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/deposit-accounts/{accountId}/commands")
    public ResponseEntity<ApiResponse<FinancialResourceResponse>> commandDeposit(
        @PathVariable UUID classroomId,
        @PathVariable UUID accountId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody DepositAccountCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        DepositAccountResult result = terminationService.terminate(
            new DepositTerminationCommand(
                subject(principal),
                classroomId,
                accountId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.command()
            )
        );
        return response(
            HttpStatus.OK,
            new FinancialResourceResponse(
                result.accountId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/savings-accounts")
    public ResponseEntity<ApiResponse<FinancialResourceResponse>> subscribeSavings(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody SavingsSubscriptionRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null ? uuidGenerator.generate() : correlationId;
        FinancialResourceResult result = savingsService.subscribe(new SavingsSubscriptionCommand(
            subject(principal), classroomId, requestId, resolvedCorrelationId, request.expectedStateVersion(),
            request.productId(), request.installmentAmount(), request.expectedProductVersion()
        ));
        return response(HttpStatus.CREATED, new FinancialResourceResponse(
            result.financialResourceId(), result.status(), result.stateVersion()
        ), requestId, resolvedCorrelationId);
    }

    @GetMapping("/students/me/deposit-and-savings")
    public ResponseEntity<ApiResponse<DepositAndSavingsResponse>> findMyAccounts(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        List<DepositAccountViewResponse> deposits = queryService
            .findMine(subject(principal), classroomId)
            .stream()
            .map(view -> new DepositAccountViewResponse(
                view.resourceId(),
                view.label(),
                view.status(),
                view.version(),
                view.stateVersion(),
                view.principalAmount(),
                view.expectedInterestAmount(),
                view.maturityAmount(),
                view.openedAt(),
                view.maturesAt(),
                view.closedAt()
            ))
            .toList();
        List<SavingsAccountViewResponse> savings = savingsQueryService
            .findMine(subject(principal), classroomId)
            .stream()
            .map(view -> new SavingsAccountViewResponse(
                view.resourceId(), view.label(), view.status(), view.installmentCount(),
                view.paidInstallmentCount(), view.installmentAmount(), view.principalBalance(),
                view.expectedInterestAmount(), view.openedAt(), view.maturesAt(), view.stateVersion()
            ))
            .toList();
        return response(
            HttpStatus.OK,
            new DepositAndSavingsResponse(deposits, savings),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/loan-application-commands")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> applyLoan(
        @PathVariable UUID classroomId,
        @Valid @RequestBody LoanApplicationRequest request,
        Principal principal
    ) {
        LoanApplicationService.LoanApplicationView value;
        if ("APPLY".equalsIgnoreCase(request.command())) {
            if (request.amount() == null) {
                throw new ClassroomOperationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LOAN_COMMAND",
                    "신청 금액을 입력해 주세요."
                );
            }
            value = loanApplicationService.apply(
                subject(principal),
                classroomId,
                request.amount(),
                request.installments()
            );
        } else if ("CANCEL".equalsIgnoreCase(request.command())) {
            if (request.applicationId() == null) {
                throw new ClassroomOperationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LOAN_COMMAND",
                    "취소할 신청을 선택해 주세요."
                );
            }
            value = loanApplicationService.cancel(
                subject(principal),
                classroomId,
                request.applicationId()
            );
        } else {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_LOAN_COMMAND",
                "대출 명령은 APPLY 또는 CANCEL이어야 합니다."
            );
        }
        UUID requestId = uuidGenerator.generate();
        return response(HttpStatus.OK, loan(value), requestId, uuidGenerator.generate());
    }

    @GetMapping("/students/me/loans-and-credit")
    public ResponseEntity<ApiResponse<LoanOverviewResponse>> myLoans(
        @PathVariable UUID classroomId,
        Principal principal
    ) {
        var value = loanApplicationService.mine(subject(principal), classroomId);
        UUID requestId = uuidGenerator.generate();
        var loans = value.loans().stream()
            .map(loan -> new LoanOverviewResponse.LoanResponse(
                loan.loanId(),
                loan.principalAmount(),
                loan.outstandingPrincipal(),
                loan.outstandingInterest(),
                loan.status(),
                loan.maturesAt()
            ))
            .toList();
        return response(
            HttpStatus.OK,
            new LoanOverviewResponse(
                value.creditScore(),
                value.creditLevel(),
                value.availableLimit(),
                value.interestRateBps(),
                value.termUnits(),
                value.installmentCount(),
                value.canApply(),
                value.pendingApplicationId(),
                value.pendingApplicationStatus(),
                loans
            ),
            requestId,
            uuidGenerator.generate()
        );
    }

    @GetMapping("/loan-approval-queue")
    public ResponseEntity<ApiResponse<LoanApprovalQueueResponse>> loanQueue(@PathVariable UUID classroomId, Principal principal) {
        String subject = subject(principal);
        UUID requestId = uuidGenerator.generate();
        return response(
            HttpStatus.OK,
            new LoanApprovalQueueResponse(
                policy(loanApplicationService.policy(subject, classroomId)),
                loanApplicationService.queue(subject, classroomId).stream()
                    .map(FinanceController::loan)
                    .toList()
            ),
            requestId,
            uuidGenerator.generate()
        );
    }

    @PutMapping("/loan-policy")
    public ResponseEntity<ApiResponse<LoanPolicyResponse>> updateLoanPolicy(
        @PathVariable UUID classroomId,
        @Valid @RequestBody LoanPolicyRequest request,
        Principal principal
    ) {
        var value = loanApplicationService.updatePolicy(
            subject(principal),
            classroomId,
            request.maximumPrincipal(),
            request.interestRateBps(),
            request.termUnits(),
            request.installmentCount(),
            request.graceUnits(),
            request.onTimeCredit(),
            request.overdueCredit(),
            request.repaidCredit(),
            request.expectedPolicyVersion()
        );
        UUID requestId = uuidGenerator.generate();
        return response(HttpStatus.OK, policy(value), requestId, uuidGenerator.generate());
    }

    @PostMapping("/loan-applications/{applicationId}/teacher-decisions")
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> decideLoan(
        @PathVariable UUID classroomId,
        @PathVariable UUID applicationId,
        @Valid @RequestBody LoanDecisionRequest request,
        Principal principal
    ) {
        boolean approve = "APPROVE".equalsIgnoreCase(request.decision());
        if (!approve && !"REJECT".equalsIgnoreCase(request.decision())) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_LOAN_DECISION",
                "승인 또는 거절을 선택해 주세요."
            );
        }
        var value = loanApplicationService.decide(
            subject(principal),
            classroomId,
            applicationId,
            approve,
            request.approvedAmount(),
            request.reason()
        );
        UUID requestId = uuidGenerator.generate();
        return response(HttpStatus.OK, loan(value), requestId, uuidGenerator.generate());
    }

    private static LoanApplicationResponse loan(LoanApplicationService.LoanApplicationView value) {
        return new LoanApplicationResponse(
            value.applicationId(), value.studentId(), value.studentNickname(), value.requestedAmount(),
            value.approvedAmount(), value.creditScore(), value.creditLevel(), value.status(), value.createdAt()
        );
    }

    private static LoanPolicyResponse policy(LoanApplicationService.LoanPolicyView value) {
        return new LoanPolicyResponse(
            value.version(), value.maximumPrincipal(), value.interestRateBps(), value.termUnits(),
            value.installmentCount(), value.graceUnits(), value.onTimeCredit(), value.overdueCredit(),
            value.repaidCredit()
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> response(
        HttpStatus status,
        T data,
        UUID requestId,
        UUID correlationId
    ) {
        return ResponseEntity.status(status)
            .body(new ApiResponse<>(
                data,
                new ApiMeta(requestId, correlationId, clock.instant())
            ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
