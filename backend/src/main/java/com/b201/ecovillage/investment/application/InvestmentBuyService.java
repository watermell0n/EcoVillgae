package com.b201.ecovillage.investment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentOrder;
import com.b201.ecovillage.investment.domain.InvestmentPosition;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentOrderRepository;
import com.b201.ecovillage.investment.repository.InvestmentPositionRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

@Service
public class InvestmentBuyService {

    private static final String ROUTE = "/classrooms/{classroom_id}/investment-orders";
    private static final int MAX_QUANTITY_PER_ORDER = 100;

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomStudentRepository studentRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final InvestmentMarketRepository marketRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentPriceStepRepository priceStepRepository;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentPositionRepository positionRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public InvestmentBuyService(
        ClassroomAuthorizationService authorizationService,
        ClassroomStudentRepository studentRepository,
        ClassroomModuleRepository moduleRepository,
        InvestmentMarketRepository marketRepository,
        InvestmentAssetRepository assetRepository,
        InvestmentPriceStepRepository priceStepRepository,
        InvestmentOrderRepository orderRepository,
        InvestmentPositionRepository positionRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PolicySnapshotRepository policySnapshotRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AtomicLedgerTransactionService transactionService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.studentRepository = studentRepository;
        this.moduleRepository = moduleRepository;
        this.marketRepository = marketRepository;
        this.assetRepository = assetRepository;
        this.priceStepRepository = priceStepRepository;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionService = transactionService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public InvestmentBuyResult buy(InvestmentBuyCommand command) {
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(), command.classroomId(),
            ClassroomPermission.PLACE_INVESTMENT_ORDER, studentId
        );
        if (command.quantity() < 1 || command.quantity() > MAX_QUANTITY_PER_ORDER) {
            throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "LIMIT_EXCEEDED", "한 번에 1~100주만 매수할 수 있습니다.");
        }
        studentRepository.findById(studentId)
            .filter(student -> student.getClassroomId().equals(command.classroomId()))
            .filter(student -> student.getStatus() == ClassroomStudentStatus.ACTIVE)
            .orElseThrow(() -> operation(HttpStatus.FORBIDDEN, "RESOURCE_NOT_OWNED", "현재 학급의 학생만 투자할 수 있습니다."));
        moduleRepository.findByClassroomIdAndModuleCode(command.classroomId(), "INVESTMENT")
            .filter(module -> "ACTIVE".equals(module.getStatus()))
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "MODULE_NOT_ACTIVE", "투자 모듈이 활성화되어 있지 않습니다."));
        String requestHash = sha256(command.assetId() + "|" + command.quantity() + "|" + command.expectedPriceVersion());
        IdempotencyRecord replay = idempotencyRepository.findForReplay(
            command.classroomId(), studentId, ROUTE, command.requestId().toString()
        ).orElse(null);
        if (replay != null) {
            if (!replay.hasRequestHash(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            InvestmentOrder order = orderRepository.findByClassroomIdAndTransactionGroupId(
                command.classroomId(), replay.getTransactionGroupId()
            ).orElseThrow(() -> operation(HttpStatus.CONFLICT, "IDEMPOTENCY_RESULT_MISSING", "기존 투자 결과가 없습니다."));
            InvestmentPosition position = positionRepository.findForUpdate(
                command.classroomId(), studentId, command.assetId()
            ).orElseThrow(() -> operation(HttpStatus.CONFLICT, "IDEMPOTENCY_RESULT_MISSING", "기존 보유 내역이 없습니다."));
            return result(order, position);
        }

        InvestmentMarket market = marketRepository.findForUpdate(command.classroomId())
            .filter(candidate -> InvestmentMarket.OPEN.equals(candidate.getStatus()))
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "MARKET_NOT_OPEN", "현재 투자 시장이 열려 있지 않습니다."));
        InvestmentAsset asset = assetRepository.findByClassroomIdAndId(command.classroomId(), command.assetId())
            .filter(InvestmentAsset::isActive)
            .filter(candidate -> candidate.getScenarioVersionId().equals(market.getActiveScenarioVersionId()))
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "현재 시장의 투자 종목이 아닙니다."));
        InvestmentPriceStep price = priceStepRepository
            .findByClassroomIdAndInvestmentAssetIdAndStepNumber(
                command.classroomId(), asset.getId(), market.getCurrentStep()
            )
            .filter(InvestmentPriceStep::isRevealed)
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "PRICE_CHANGED", "현재 공개 가격을 확인할 수 없습니다."));
        if (price.getStepNumber() != command.expectedPriceVersion()) {
            throw operation(HttpStatus.CONFLICT, "PRICE_CHANGED", "가격 단계가 변경되었습니다. 최신 가격을 확인해 주세요.");
        }
        long total;
        try {
            total = Math.multiplyExact(price.getPrice(), command.quantity());
        } catch (ArithmeticException exception) {
            throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_OVERFLOW", "투자 금액이 허용 범위를 초과했습니다.");
        }
        Instant now = clock.instant();
        String snapshotHash = sha256(
            command.classroomId() + "|" + market.getActiveScenarioVersionId() + "|"
                + asset.getId() + "|" + price.getStepNumber() + "|" + price.getPrice()
        );
        PolicySnapshot snapshot = policySnapshotRepository
            .findByClassroomIdAndSnapshotHash(command.classroomId(), snapshotHash)
            .orElseGet(() -> policySnapshotRepository.save(PolicySnapshot.capture(
                uuidGenerator.generate(), command.classroomId(), snapshotHash, "1",
                Map.of(
                    "scenario_version_id", market.getActiveScenarioVersionId().toString(),
                    "asset_id", asset.getId().toString(),
                    "price_step", price.getStepNumber(),
                    "unit_price", price.getPrice(),
                    "quantity", command.quantity(),
                    "total_amount", total
                ),
                "INTEGER", now
            )));
        LedgerAccount wallet = ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                command.classroomId(), studentId, LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            ).orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.studentWallet(
                uuidGenerator.generate(), command.classroomId(), studentId, now
            )));
        LedgerAccount clearing = ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                command.classroomId(), "INVESTMENT_CLEARING", LedgerAccountType.INVESTMENT_CLEARING,
                LedgerAccountStatus.ACTIVE
            ).orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.systemAccount(
                uuidGenerator.generate(), command.classroomId(), "INVESTMENT_CLEARING",
                LedgerAccountType.INVESTMENT_CLEARING, now
            )));
        InvestmentPosition position = positionRepository.findForUpdate(
            command.classroomId(), studentId, asset.getId()
        ).orElseGet(() -> InvestmentPosition.empty(
            uuidGenerator.generate(), command.classroomId(), studentId, asset.getId(), now
        ));
        UUID transactionGroupId = uuidGenerator.generate();
        InvestmentOrder order = InvestmentOrder.executedBuy(
            uuidGenerator.generate(), command.classroomId(), studentId, asset.getId(),
            command.quantity(), price.getPrice(), price.getStepNumber(), transactionGroupId, now
        );
        LedgerDomainMutation mutation = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (!InvestmentMarket.OPEN.equals(market.getStatus())
                    || market.getCurrentStep() != price.getStepNumber()) {
                    throw operation(HttpStatus.CONFLICT, "PRICE_CHANGED", "매수 처리 중 가격 단계가 변경되었습니다.");
                }
            }

            @Override
            public void apply() {
                position.buy(command.quantity(), price.getPrice(), now);
                positionRepository.save(position);
                orderRepository.save(order);
            }
        };
        transactionService.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                command.classroomId(), "INVESTMENT_BUY", LedgerActorType.STUDENT, studentId,
                snapshot.getId(), ROUTE, command.requestId().toString(), requestHash,
                command.correlationId().toString(), "가상 투자 종목 매수",
                List.of(
                    new LedgerTransactionPosting(wallet.getId(), -total),
                    new LedgerTransactionPosting(clearing.getId(), total)
                ),
                now
            ),
            List.of(mutation)
        );
        return result(order, position);
    }

    private static InvestmentBuyResult result(InvestmentOrder order, InvestmentPosition position) {
        long total = Math.multiplyExact(order.getExecutedPrice(), order.getQuantity());
        return new InvestmentBuyResult(
            order.getId(), order.getStatus(), order.getQuantity(), order.getExecutedPrice(),
            order.getExecutedPriceStep(), total, position.getQuantity(), position.getAveragePrice(),
            order.getStateVersion()
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "학생 인증이 필요합니다.");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ClassroomOperationException operation(HttpStatus status, String code, String message) {
        return new ClassroomOperationException(status, code, message);
    }
}
