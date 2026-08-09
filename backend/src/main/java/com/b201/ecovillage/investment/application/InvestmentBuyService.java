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

import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentMode;
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
    private final EconomicEventDraftRepository eventRepository;
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
        EconomicEventDraftRepository eventRepository,
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
        this.eventRepository = eventRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionService = transactionService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public InvestmentBuyResult buy(InvestmentBuyCommand command) {
        return execute(command, "BUY");
    }

    /** 장기 투자 보유분을 현재 공개 가격으로 일부 또는 전부 매도한다. */
    @Transactional
    public InvestmentBuyResult sell(InvestmentBuyCommand command) {
        return execute(command, "SELL");
    }

    private InvestmentBuyResult execute(InvestmentBuyCommand command, String side) {
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(), command.classroomId(),
            ClassroomPermission.PLACE_INVESTMENT_ORDER, studentId
        );
        if (command.quantity() < 1 || command.quantity() > MAX_QUANTITY_PER_ORDER) {
            throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "LIMIT_EXCEEDED", "한 번에 1~100주만 주문할 수 있습니다.");
        }
        studentRepository.findById(studentId)
            .filter(student -> student.getClassroomId().equals(command.classroomId()))
            .filter(student -> student.getStatus() == ClassroomStudentStatus.ACTIVE)
            .orElseThrow(() -> operation(HttpStatus.FORBIDDEN, "RESOURCE_NOT_OWNED", "현재 학급의 학생만 투자할 수 있습니다."));
        moduleRepository.findByClassroomIdAndModuleCode(command.classroomId(), "INVESTMENT")
            .filter(module -> "ACTIVE".equals(module.getStatus()))
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "MODULE_NOT_ACTIVE", "투자 모듈이 활성화되어 있지 않습니다."));
        // 기존 BUY 요청의 재시도 호환성을 유지하고 SELL만 별도 해시 공간을 쓴다.
        String requestHash = sha256(
            ("BUY".equals(side) ? "" : side + "|")
                + command.assetId() + "|" + command.quantity() + "|" + command.expectedPriceVersion()
        );
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

        // 학급이 단기와 장기를 함께 운영하므로 종목이 어느 시장의 것인지부터 정한다.
        InvestmentAsset asset = assetRepository.findByClassroomIdAndId(command.classroomId(), command.assetId())
            .filter(InvestmentAsset::isActive)
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "현재 시장의 투자 종목이 아닙니다."));
        InvestmentMarket market = marketRepository
            .findForUpdateByActiveScenario(command.classroomId(), asset.getScenarioVersionId())
            .filter(candidate -> InvestmentMarket.OPEN.equals(candidate.getStatus()))
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "MARKET_NOT_OPEN", "현재 투자 시장이 열려 있지 않습니다."));
        if ("SELL".equals(side) && market.getInvestmentMode() != InvestmentMode.LONG_TERM) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SELL_NOT_ALLOWED",
                "매도는 장기 투자에서만 할 수 있습니다. 단기 투자는 다음 날 자동 정산됩니다."
            );
        }
        InvestmentPriceStep price = priceStepRepository
            .findByClassroomIdAndInvestmentAssetIdAndStepNumber(
                command.classroomId(), asset.getId(), market.getCurrentStep()
            )
            .filter(InvestmentPriceStep::isRevealed)
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "PRICE_CHANGED", "현재 공개 가격을 확인할 수 없습니다."));
        if (price.getStepNumber() != command.expectedPriceVersion()) {
            throw operation(HttpStatus.CONFLICT, "PRICE_CHANGED", "가격 단계가 변경되었습니다. 최신 가격을 확인해 주세요.");
        }
        if (!eventRepository.existsByClassroomIdAndInvestmentPriceStepIdAndStatusIn(
            command.classroomId(), price.getId(), List.of("APPROVED", "APPLIED")
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "NEWS_NOT_PUBLISHED",
                "교사가 오늘의 투자 이야기를 공개한 뒤 주문할 수 있습니다."
            );
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
                + "|" + side + "|" + command.quantity()
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
                    "total_amount", total,
                    "side", side
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
        ).orElse(null);
        if ("SELL".equals(side) && (position == null || position.getQuantity() < command.quantity())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INSUFFICIENT_POSITION",
                "보유한 주식보다 많이 팔 수 없습니다."
            );
        }
        if (position == null) {
            position = InvestmentPosition.empty(
                uuidGenerator.generate(), command.classroomId(), studentId, asset.getId(), now
            );
        }
        InvestmentPosition resolvedPosition = position;
        UUID transactionGroupId = uuidGenerator.generate();
        InvestmentOrder order = "BUY".equals(side)
            ? InvestmentOrder.executedBuy(
                uuidGenerator.generate(), command.classroomId(), studentId, asset.getId(),
                command.quantity(), price.getPrice(), price.getStepNumber(), transactionGroupId, now
            )
            : InvestmentOrder.executedSell(
                uuidGenerator.generate(), command.classroomId(), studentId, asset.getId(),
                command.quantity(), price.getPrice(), price.getStepNumber(), transactionGroupId, now
            );
        LedgerDomainMutation mutation = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (!InvestmentMarket.OPEN.equals(market.getStatus())
                    || market.getCurrentStep() != price.getStepNumber()) {
                    throw operation(HttpStatus.CONFLICT, "PRICE_CHANGED", "주문 처리 중 가격 단계가 변경되었습니다.");
                }
                if ("SELL".equals(side) && resolvedPosition.getQuantity() < command.quantity()) {
                    throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_POSITION", "보유한 주식보다 많이 팔 수 없습니다.");
                }
            }

            @Override
            public void apply() {
                if ("BUY".equals(side)) {
                    resolvedPosition.buy(command.quantity(), price.getPrice(), now);
                } else {
                    resolvedPosition.sell(command.quantity(), now);
                }
                positionRepository.save(resolvedPosition);
                orderRepository.save(order);
            }
        };
        List<LedgerTransactionPosting> postings = "BUY".equals(side)
            ? List.of(
                new LedgerTransactionPosting(wallet.getId(), -total),
                new LedgerTransactionPosting(clearing.getId(), total)
            )
            : List.of(
                new LedgerTransactionPosting(clearing.getId(), -total),
                new LedgerTransactionPosting(wallet.getId(), total)
            );
        transactionService.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                command.classroomId(), "INVESTMENT_" + side, LedgerActorType.STUDENT, studentId,
                snapshot.getId(), ROUTE, command.requestId().toString(), requestHash,
                command.correlationId().toString(), "가상 투자 종목 " + ("BUY".equals(side) ? "매수" : "매도"),
                postings,
                now
            ),
            List.of(mutation)
        );
        return result(order, resolvedPosition);
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
