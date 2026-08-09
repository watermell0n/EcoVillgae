package com.b201.ecovillage.investment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.domain.InvestmentModeConfig;
import com.b201.ecovillage.investment.domain.InvestmentOrder;
import com.b201.ecovillage.investment.domain.InvestmentPosition;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentOrderRepository;
import com.b201.ecovillage.investment.repository.InvestmentPositionRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.investment.repository.InvestmentScenarioVersionRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

/**
 * 투자 시장을 다음 날로 넘기고, 정산할 시점이면 학생 보유량을 현금으로 되돌린다.
 *
 * <p>가격 단계는 시나리오를 만들 때 전부 미리 정해 두고 숨겨 둔다. 하루를 넘기는 일은
 * 숨은 단계를 하나 공개하는 것뿐이며, 가격을 새로 만들지 않는다.
 *
 * <p>정산 시점은 방식마다 다르다. 단기는 다음 날 반드시 정산하므로 하루를 넘기는 순간
 * 정산하고 시장을 닫는다. 장기는 마지막 날에 최종 정산한다.
 */
@Service
public class InvestmentDayService {

    private static final String ROUTE = "/classrooms/{classroom_id}/investment-market/commands";

    private final ClassroomAuthorizationService authorizationService;
    private final InvestmentMarketRepository marketRepository;
    private final InvestmentScenarioVersionRepository scenarioRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentPriceStepRepository priceStepRepository;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentOrderRepository orderRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final InvestmentRealtimePublisher realtimePublisher;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public InvestmentDayService(
        ClassroomAuthorizationService authorizationService,
        InvestmentMarketRepository marketRepository,
        InvestmentScenarioVersionRepository scenarioRepository,
        InvestmentAssetRepository assetRepository,
        InvestmentPriceStepRepository priceStepRepository,
        InvestmentPositionRepository positionRepository,
        InvestmentOrderRepository orderRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PolicySnapshotRepository policySnapshotRepository,
        AtomicLedgerTransactionService transactionService,
        InvestmentRealtimePublisher realtimePublisher,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.marketRepository = marketRepository;
        this.scenarioRepository = scenarioRepository;
        this.assetRepository = assetRepository;
        this.priceStepRepository = priceStepRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.transactionService = transactionService;
        this.realtimePublisher = realtimePublisher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public InvestmentDayResult advanceDay(
        String teacherSubject,
        UUID classroomId,
        String requestedMode,
        long expectedStateVersion
    ) {
        authorizationService.authorize(
            teacherSubject, classroomId, ClassroomPermission.CONTROL_INVESTMENT_MARKET, null
        );
        InvestmentMode mode = InvestmentModeConfig.parse(requestedMode)
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST, "INVALID_INVESTMENT_MODE", "투자 방식이 올바르지 않습니다."
            ));
        InvestmentMarket market = marketRepository.findForUpdate(classroomId, mode)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "투자 시장을 찾을 수 없습니다."
            ));
        if (market.getStateVersion() != expectedStateVersion) {
            throw operation(HttpStatus.CONFLICT, "STATE_VERSION_CONFLICT", "투자 시장 상태가 변경되었습니다.");
        }
        if (!InvestmentMarket.OPEN.equals(market.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "열려 있는 투자만 다음 날로 넘길 수 있습니다."
            );
        }
        InvestmentScenarioVersion scenario = scenarioRepository
            .findByClassroomIdAndId(classroomId, market.getActiveScenarioVersionId())
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "투자 시나리오가 없습니다."));
        List<InvestmentAsset> assets = assetRepository
            .findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
                classroomId, scenario.getId(), InvestmentAsset.ACTIVE
            );
        if (assets.isEmpty()) {
            throw operation(HttpStatus.CONFLICT, "INVALID_PRICE_STEP", "공개할 종목이 없습니다.");
        }

        int nextStep = market.getCurrentStep() + 1;
        // 모든 종목의 다음 단계가 함께 있어야 한다. 한 종목만 넘어가면 학생이 보는 시세가 어긋난다.
        List<InvestmentPriceStep> nextSteps = new ArrayList<>();
        for (InvestmentAsset asset : assets) {
            nextSteps.add(priceStepRepository
                .findByClassroomIdAndInvestmentAssetIdAndStepNumber(classroomId, asset.getId(), nextStep)
                .orElseThrow(() -> operation(
                    HttpStatus.CONFLICT,
                    "NO_REMAINING_PRICE_STEP",
                    "마지막 날까지 모두 공개했습니다. 새 시나리오를 만들어 주세요."
                )));
        }

        Instant now = clock.instant();
        for (InvestmentPriceStep step : nextSteps) {
            step.reveal(now);
            priceStepRepository.save(step);
        }
        market.advanceDay(now);

        // 단기는 다음 날 의무 정산이고, 장기는 마지막 날에 최종 정산한다.
        int totalSteps = priceStepRepository
            .findAllByClassroomIdAndInvestmentAssetIdOrderByStepNumber(classroomId, assets.get(0).getId())
            .size();
        boolean finalStep = nextStep >= totalSteps;
        boolean settleNow = mode == InvestmentMode.SHORT_TERM || finalStep;

        int settledPositions = 0;
        long settledAmount = 0L;
        if (settleNow) {
            for (int index = 0; index < assets.size(); index++) {
                InvestmentAsset asset = assets.get(index);
                long price = nextSteps.get(index).getPrice();
                for (InvestmentPosition position
                    : positionRepository.findAllHoldersForUpdate(classroomId, asset.getId())) {
                    long paid = settle(classroomId, market, position, asset, price, nextStep, now);
                    settledPositions += 1;
                    settledAmount = Math.addExact(settledAmount, paid);
                }
            }
            market.close(now);
        }
        marketRepository.save(market);
        marketRepository.flush();
        realtimePublisher.publish(
            classroomId, market, settleNow ? "MARKET_SETTLED" : "PRICE_ADVANCED"
        );

        return new InvestmentDayResult(
            market.getId(),
            market.getStatus(),
            mode.name(),
            market.getCurrentStep(),
            totalSteps,
            settleNow,
            settledPositions,
            settledAmount,
            market.getStateVersion()
        );
    }

    private long settle(
        UUID classroomId,
        InvestmentMarket market,
        InvestmentPosition position,
        InvestmentAsset asset,
        long price,
        int priceStep,
        Instant now
    ) {
        int quantity = position.getQuantity();
        long proceeds = Math.multiplyExact(price, (long) quantity);
        UUID studentId = position.getClassroomStudentId();
        LedgerAccount wallet = ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId, studentId, LedgerAccountType.STUDENT_WALLET, LedgerAccountStatus.ACTIVE
            ).orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.studentWallet(
                uuidGenerator.generate(), classroomId, studentId, now
            )));
        LedgerAccount clearing = ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId, "INVESTMENT_CLEARING", LedgerAccountType.INVESTMENT_CLEARING,
                LedgerAccountStatus.ACTIVE
            ).orElseThrow(() -> operation(
                HttpStatus.CONFLICT, "SETTLEMENT_FAILED", "투자 청산 계정을 찾을 수 없습니다."
            ));
        String snapshotHash = sha256(
            classroomId + "|settle|" + market.getId() + "|" + asset.getId() + "|" + priceStep + "|" + price
        );
        PolicySnapshot snapshot = policySnapshotRepository
            .findByClassroomIdAndSnapshotHash(classroomId, snapshotHash)
            .orElseGet(() -> policySnapshotRepository.save(PolicySnapshot.capture(
                uuidGenerator.generate(), classroomId, snapshotHash, "1",
                Map.of(
                    "scenario_version_id", market.getActiveScenarioVersionId().toString(),
                    "asset_id", asset.getId().toString(),
                    "price_step", priceStep,
                    "unit_price", price
                ),
                "INTEGER", now
            )));
        UUID transactionGroupId = uuidGenerator.generate();
        InvestmentOrder order = InvestmentOrder.executedSell(
            uuidGenerator.generate(), classroomId, studentId, asset.getId(),
            quantity, price, priceStep, transactionGroupId, now
        );
        String requestHash = sha256("settle|" + position.getId() + "|" + priceStep + "|" + quantity);
        LedgerDomainMutation mutation = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (position.getQuantity() != quantity) {
                    throw operation(HttpStatus.CONFLICT, "SETTLEMENT_FAILED", "정산 중 보유량이 변경되었습니다.");
                }
            }

            @Override
            public void apply() {
                position.settle(now);
                positionRepository.save(position);
                orderRepository.save(order);
            }
        };
        transactionService.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                // 교사가 하루를 넘겨 서버가 일으킨 정산이므로 주체는 SYSTEM이고,
                // 돈이 움직이는 대상은 그 학생이다.
                classroomId, "INVESTMENT_SETTLEMENT", LedgerActorType.SYSTEM, studentId,
                snapshot.getId(), ROUTE, transactionGroupId.toString(), requestHash,
                transactionGroupId.toString(), "가상 투자 정산 지급",
                List.of(
                    new LedgerTransactionPosting(clearing.getId(), -proceeds),
                    new LedgerTransactionPosting(wallet.getId(), proceeds)
                ),
                now
            ),
            List.of(mutation)
        );
        return proceeds;
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
