package com.b201.bibim.feature.investment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryEntity;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationEntity;
import com.b201.bibim.domain.investment.InvestmentAllocationRepository;
import com.b201.bibim.domain.investment.InvestmentAssetEntity;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.domain.investment.InvestmentOrderEntity;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioEntity;
import com.b201.bibim.domain.investment.InvestmentScenarioRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioReturnEntity;
import com.b201.bibim.domain.investment.InvestmentScenarioReturnRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.investment.InvestmentDto.AllocationRequest;
import com.b201.bibim.feature.investment.InvestmentDto.AllocationResponse;
import com.b201.bibim.feature.investment.InvestmentDto.AssetResponse;
import com.b201.bibim.feature.investment.InvestmentDto.InvestmentResponse;
import com.b201.bibim.feature.investment.InvestmentDto.InvestmentHistoryItemResponse;
import com.b201.bibim.feature.investment.InvestmentDto.InvestmentHistoryResponse;
import com.b201.bibim.feature.investment.InvestmentDto.SaveDraftRequest;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.flow.StudentFlowService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentService {

    private static final String OPEN_STAGE = "DAY1_FREE_ACTIVITY";
    private static final Set<String> SUPPORTED_ASSET_CODES = Set.of(
            "SAFE_BOND", "LOCAL_STORE", "FUTURE_TECH"
    );

    private static final List<AssetDefinition> DEFAULT_ASSETS = List.of(
            new AssetDefinition(
                    "SAFE_BOND",
                    "거북이 상사",
                    "생활용품을 만드는 오래된 회사예요. 큰 변화는 적지만 매달 꾸준히 물건을 팔고 있어요.",
                    """
                    거북이상사는 생활용품을 만드는 오래된 회사예요.
                    새롭고 화려한 상품은 적지만, 사람들이 매일 쓰는 물건을 꾸준히 팔고 있어요.

                    최근 한 달 동안 거북이상사의 판매량은 지난달보다 120건 늘었어요.
                    반품은 3건에서 1건으로 줄었고, 창고에 남아 있던 재고도 줄었어요.

                    또 학교와 사무실에서 사용하는 기본 문구 세트를 새로 납품하게 되었어요.
                    다만 새 상품을 기다리는 사람은 많지 않고, 뉴스에 크게 소개된 적도 없어요.

                    이번 달 거북이상사의 회사 가치는 어떻게 변할까요?
                    자료를 보고 직접 예상해보세요.
                    """.strip(),
                    "LOW", -1_500, 2_500, 200
            ),
            new AssetDefinition(
                    "LOCAL_STORE",
                    "독수리 테크",
                    "새로운 학습 기기를 만드는 기술 회사예요. 성공하면 크게 성장할 수 있지만, 실패하면 손실도 클 수 있어요.",
                    """
                    독수리테크는 새로운 AI 학습 기기를 만드는 회사예요.
                    최근 발표한 제품은 디자인이 멋지고, 체험 행사에서도 많은 관심을 받았어요.
                    예약 신청도 첫날 300건이나 들어왔어요.

                    하지만 제품을 만드는 데 필요한 부품 가격이 지난달보다 50원 올랐어요.
                    완성된 제품 100개 중 18개에서는 작동 오류가 발견되었어요.

                    원래 이번 주에 배송을 시작하려고 했지만,
                    추가 점검 때문에 배송이 일주일 늦어졌어요.
                    그래도 회사는 “더 좋은 제품을 보내기 위한 준비”라고 설명했어요.

                    이번 달 독수리테크의 회사 가치는 어떻게 변할까요?
                    좋은 소식과 걱정되는 소식을 함께 보고 예상해보세요.
                    """.strip(),
                    "MEDIUM", -3_000, 3_000, 700
            ),
            new AssetDefinition(
                    "FUTURE_TECH",
                    "호랑이 운송",
                    "배송 성공 793건, 장거리 배송 150건, 대형 배송 101건! 하지만 경쟁 회사도 주문 처리 481건으로 만만치 않아요.",
                    """
                    호랑이운송은 최근 86일 동안 793번 배달에 성공했어요.
                    경쟁 회사도 792번 성공해서 거의 비슷해요.

                    긴 거리 배달은 호랑이운송이 150번, 경쟁 회사가 134번 성공했어요.
                    큰 물건 배달도 호랑이운송이 101번, 경쟁 회사가 95번 성공했어요.
                    이 부분은 호랑이운송이 조금 앞서요.

                    하지만 주문 수는 경쟁 회사가 더 많아요.
                    호랑이운송은 450건, 경쟁 회사는 481건의 주문을 받았어요.
                    돈을 번 횟수도 호랑이운송은 431번, 경쟁 회사는 451번이에요.

                    실수 점수는 호랑이운송이 4.28점, 경쟁 회사가 4.50점이에요.
                    낮을수록 좋은 점수라서, 실수는 호랑이운송이 조금 적어요.

                    두 회사의 직접 대결은 호랑이운송이 5번 이기고 4번 졌어요.
                    조금 앞서지만 차이가 크지 않아요.
                    """.strip(),
                    "HIGH", -3_000, 3_000, -600
            )
    );

    private final InvestmentAssetRepository assetRepository;
    private final InvestmentScenarioRepository scenarioRepository;
    private final InvestmentScenarioReturnRepository scenarioReturnRepository;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAllocationRepository allocationRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final RoomRulesRepository rulesRepository;
    private final DaySessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;
    private final StudentFlowService studentFlowService;

    public InvestmentService(
            InvestmentAssetRepository assetRepository,
            InvestmentScenarioRepository scenarioRepository,
            InvestmentScenarioReturnRepository scenarioReturnRepository,
            InvestmentOrderRepository orderRepository,
            InvestmentAllocationRepository allocationRepository,
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            RoomRulesRepository rulesRepository,
            DaySessionRepository sessionRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository,
            StudentFlowService studentFlowService
    ) {
        this.assetRepository = assetRepository;
        this.scenarioRepository = scenarioRepository;
        this.scenarioReturnRepository = scenarioReturnRepository;
        this.orderRepository = orderRepository;
        this.allocationRepository = allocationRepository;
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.rulesRepository = rulesRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.studentFlowService = studentFlowService;
    }

    @Transactional
    public InvestmentResponse current(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = lockRoom(student.getRoomId());
        ensureOpen(room, studentId);
        Market market = ensureMarket(room.getId());
        DaySessionEntity session = dayOneSession(room.getId());
        InvestmentOrderEntity order = currentOrder(session.getId(), studentId).orElse(null);
        return toResponse(
                order,
                market,
                walletBalance(room.getId(), studentId),
                false
        );
    }

    @Transactional(readOnly = true)
    public InvestmentHistoryResponse history(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        ensureOpen(room, studentId);
        DaySessionEntity session = dayOneSession(room.getId());
        Map<UUID, InvestmentAssetEntity> assetsById = assetRepository
                .findAllByRoomIdOrderByDisplayOrder(room.getId())
                .stream()
                .collect(Collectors.toMap(InvestmentAssetEntity::getId, Function.identity()));
        List<InvestmentHistoryItemResponse> investments = orderRepository
                .findAllBySessionIdAndStudentIdAndStatusOrderByConfirmedAtDesc(
                        session.getId(), studentId, "CONFIRMED"
                )
                .stream()
                .map(order -> new InvestmentHistoryItemResponse(
                        order.getId(),
                        order.getTotalAmount(),
                        allocationResponses(order.getId(), assetsById),
                        order.getConfirmedAt()
                ))
                .toList();
        return new InvestmentHistoryResponse(investments);
    }

    @Transactional
    public InvestmentResponse saveDraft(UUID studentId, SaveDraftRequest request) {
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);
        Market market = ensureMarket(room.getId());
        ValidatedAllocations validated = validateAllocations(
                request.allocations(), market.assets(), rules(room.getId())
        );
        AccountEntity wallet = walletForUpdate(room.getId(), studentId);
        if (wallet.getBalance() < validated.totalAmount()) {
            throw insufficientBalance();
        }
        DaySessionEntity session = dayOneSession(room.getId());
        InvestmentOrderEntity order = orderRepository.findDraftForUpdate(session.getId(), studentId)
                .map(existing -> {
                    existing.changeDraft(validated.totalAmount());
                    return existing;
                })
                .orElseGet(() -> orderRepository.saveAndFlush(InvestmentOrderEntity.draft(
                        room.getId(), session.getId(), studentId, validated.totalAmount()
                )));
        orderRepository.save(order);
        allocationRepository.deleteAllByOrderId(order.getId());
        allocationRepository.flush();
        List<InvestmentAllocationEntity> allocations = validated.requests().stream()
                .map(item -> InvestmentAllocationEntity.create(
                        order.getId(), item.assetId(), item.amount()
                ))
                .toList();
        allocationRepository.saveAll(allocations);
        room.markEconomicStateChanged();
        return toResponse(order, market, wallet.getBalance(), false);
    }

    @Transactional
    public InvestmentResponse cancelDraft(UUID studentId) {
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);
        Market market = ensureMarket(room.getId());
        DaySessionEntity session = dayOneSession(room.getId());
        InvestmentOrderEntity order = orderRepository.findDraftForUpdate(session.getId(), studentId)
                .orElseThrow(this::draftNotFound);
        order.cancelDraft();
        room.markEconomicStateChanged();
        return toResponse(order, market, walletBalance(room.getId(), studentId), false);
    }

    @Transactional
    public InvestmentResponse confirm(UUID studentId, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);
        Market market = ensureMarket(room.getId());

        var existingTransaction = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existingTransaction.isPresent()) {
            FinancialTransactionEntity transaction = existingTransaction.get();
            if (!"INVESTMENT_CONFIRM".equals(transaction.getTransactionType())
                    || !studentId.equals(transaction.getStudentId())) {
                throw idempotencyKeyReused();
            }
            InvestmentOrderEntity order = orderRepository.findByTransactionId(transaction.getId())
                    .orElseThrow(this::idempotencyKeyReused);
            return toResponse(order, market, walletBalance(room.getId(), studentId), true);
        }

        DaySessionEntity session = dayOneSession(room.getId());
        InvestmentOrderEntity order = orderRepository.findDraftForUpdate(session.getId(), studentId)
                .orElseThrow(this::draftNotFound);
        List<InvestmentAllocationEntity> allocations = allocationRepository
                .findAllByOrderId(order.getId());
        if (allocations.isEmpty()) {
            throw draftNotFound();
        }
        AccountEntity wallet = walletForUpdate(room.getId(), studentId);
        if (wallet.getBalance() < order.getTotalAmount()) {
            throw insufficientBalance();
        }
        AccountEntity investmentAccount = accountRepository.findStudentAccountForUpdate(
                        room.getId(), studentId, "STUDENT_INVESTMENT"
                )
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.studentInvestment(room.getId(), studentId)
                ));
        InvestmentScenarioEntity scenario = scenarioRepository.findByRoomIdForUpdate(room.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "INVESTMENT_SCENARIO_NOT_FOUND", "투자 시나리오를 찾을 수 없습니다."
                ));
        scenario.lock();
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.studentTransfer(
                        room.getId(),
                        session.getId(),
                        studentId,
                        "INVESTMENT_CONFIRM",
                        normalizedKey,
                        "1일차 투자 확정",
                        Map.of(
                                "totalAmount", order.getTotalAmount(),
                                "allocationCount", allocations.size(),
                                "scenarioId", scenario.getId().toString()
                        )
                )
        );
        long walletBefore = wallet.getBalance();
        long investmentBefore = investmentAccount.getBalance();
        wallet.changeBy(-order.getTotalAmount());
        investmentAccount.changeBy(order.getTotalAmount());
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(
                        transaction.getId(), wallet, -order.getTotalAmount(), walletBefore, 1
                ),
                FinancialEntryEntity.create(
                        transaction.getId(), investmentAccount,
                        order.getTotalAmount(), investmentBefore, 2
                )
        ));
        order.confirm(scenario.getId(), transaction.getId());
        room.markEconomicStateChanged();
        return toResponse(order, new Market(market.assets(), scenario), wallet.getBalance(), false);
    }

    private Market ensureMarket(UUID roomId) {
        List<InvestmentAssetEntity> assets = assetRepository
                .findAllByRoomIdOrderByDisplayOrder(roomId).stream()
                .filter(asset -> isSupportedAssetCode(asset.getCode()))
                .toList();
        if (assets.isEmpty()) {
            List<InvestmentAssetEntity> created = new ArrayList<>();
            for (int index = 0; index < DEFAULT_ASSETS.size(); index += 1) {
                AssetDefinition definition = DEFAULT_ASSETS.get(index);
                created.add(InvestmentAssetEntity.create(
                        roomId,
                        definition.code(),
                        definition.name(),
                        definition.description(),
                        definition.companyInfo(),
                        definition.riskLevel(),
                        definition.minReturnBps(),
                        definition.maxReturnBps(),
                        index + 1
                ));
            }
            assets = assetRepository.saveAll(created);
        }
        List<InvestmentAssetEntity> marketAssets = List.copyOf(assets);
        InvestmentScenarioEntity scenario = scenarioRepository.findByRoomId(roomId)
                .orElseGet(() -> {
                    InvestmentScenarioEntity created = scenarioRepository.saveAndFlush(
                            InvestmentScenarioEntity.ready(roomId)
                    );
                    Map<String, InvestmentAssetEntity> byCode = marketAssets.stream()
                            .collect(Collectors.toMap(InvestmentAssetEntity::getCode, Function.identity()));
                    scenarioReturnRepository.saveAll(DEFAULT_ASSETS.stream()
                            .map(definition -> InvestmentScenarioReturnEntity.create(
                                    created.getId(),
                                    byCode.get(definition.code()).getId(),
                                    definition.actualReturnBps()
                            ))
                            .toList());
                    return created;
                });
        return new Market(marketAssets, scenario);
    }

    public static boolean isSupportedAssetCode(String code) {
        return SUPPORTED_ASSET_CODES.contains(code);
    }

    @Transactional
    public void ensureDefaultAssets(UUID roomId) {
        ensureMarket(roomId);
    }

    private ValidatedAllocations validateAllocations(
            List<AllocationRequest> requests,
            List<InvestmentAssetEntity> assets,
            RoomRulesEntity rules
    ) {
        if (requests == null || requests.isEmpty() || requests.size() > 3) {
            throw invalidAllocation();
        }
        Map<UUID, InvestmentAssetEntity> assetsById = assets.stream()
                .collect(Collectors.toMap(InvestmentAssetEntity::getId, Function.identity()));
        Set<UUID> selected = new HashSet<>();
        long total = 0;
        for (AllocationRequest request : requests) {
            if (request.assetId() == null || !assetsById.containsKey(request.assetId())
                    || !selected.add(request.assetId())
                    || request.amount() < rules.getInvestmentMinAmount()
                    || request.amount() % rules.getInvestmentUnit() != 0) {
                throw invalidAllocation();
            }
            try {
                total = Math.addExact(total, request.amount());
            } catch (ArithmeticException exception) {
                throw invalidAllocation();
            }
        }
        return new ValidatedAllocations(List.copyOf(requests), total);
    }

    private InvestmentResponse toResponse(
            InvestmentOrderEntity order,
            Market market,
            long walletBalance,
            boolean replayed
    ) {
        List<AssetResponse> assetResponses = market.assets().stream()
                .map(this::toAssetResponse)
                .toList();
        if (order == null) {
            RoomRulesEntity rules = rules(market.assets().get(0).getRoomId());
            return new InvestmentResponse(
                    null, "NONE", 0, walletBalance,
                    rules.getInvestmentMinAmount(), rules.getInvestmentUnit(),
                    "LOCKED".equals(market.scenario().getStatus()),
                    assetResponses, List.of(), null, null, replayed
            );
        }
        Map<UUID, InvestmentAssetEntity> assetsById = market.assets().stream()
                .collect(Collectors.toMap(InvestmentAssetEntity::getId, Function.identity()));
        List<AllocationResponse> allocations = allocationResponses(order.getId(), assetsById);
        RoomRulesEntity rules = rules(order.getRoomId());
        return new InvestmentResponse(
                order.getId(), order.getStatus(), order.getTotalAmount(), walletBalance,
                rules.getInvestmentMinAmount(), rules.getInvestmentUnit(),
                "LOCKED".equals(market.scenario().getStatus()),
                assetResponses, allocations, order.getUpdatedAt(), order.getConfirmedAt(), replayed
        );
    }

    private RoomRulesEntity rules(UUID roomId) {
        return rulesRepository.findByRoomId(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "ROOM_RULES_NOT_FOUND", "수업 경제 설정을 찾을 수 없습니다."
        ));
    }

    private List<AllocationResponse> allocationResponses(
            UUID orderId,
            Map<UUID, InvestmentAssetEntity> assetsById
    ) {
        return allocationRepository.findAllByOrderId(orderId)
                .stream()
                .map(item -> {
                    InvestmentAssetEntity asset = assetsById.get(item.getAssetId());
                    return new AllocationResponse(
                            item.getAssetId(),
                            asset == null ? "알 수 없는 투자처" : asset.getName(),
                            asset == null ? "UNKNOWN" : asset.getRiskLevel(),
                            item.getAmount()
                    );
                })
                .toList();
    }

    private AssetResponse toAssetResponse(InvestmentAssetEntity asset) {
        return new AssetResponse(
                asset.getId(), asset.getCode(), asset.getName(), asset.getDescription(),
                asset.getCompanyInfo(), asset.getRiskLevel(), asset.getMinReturnBps(), asset.getMaxReturnBps(),
                asset.getDisplayOrder()
        );
    }

    private java.util.Optional<InvestmentOrderEntity> currentOrder(
            UUID sessionId,
            UUID studentId
    ) {
        return orderRepository
                .findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
                        sessionId, studentId, "DRAFT"
                )
                .or(() -> orderRepository
                        .findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
                                sessionId, studentId, "CONFIRMED"
                        ));
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private StudentEntity lockActiveStudent(UUID studentId) {
        return studentRepository.findByIdForUpdate(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private RoomEntity lockRoom(UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
    }

    private RoomEntity findRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
    }

    private DaySessionEntity dayOneSession(UUID roomId) {
        return sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "SESSION_NOT_FOUND", "1일차 수업을 찾을 수 없습니다."
                ));
    }

    private AccountEntity walletForUpdate(UUID roomId, UUID studentId) {
        return accountRepository.findStudentAccountForUpdate(
                roomId, studentId, "STUDENT_WALLET"
        ).orElseThrow(this::insufficientBalance);
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_WALLET"
        ).map(AccountEntity::getBalance).orElse(0L);
    }

    private void ensureOpen(RoomEntity room, UUID studentId) {
        if (!OPEN_STAGE.equals(room.getCurrentStage())
                && !studentFlowService.canUseStage(studentId, OPEN_STAGE)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVESTMENT_CLOSED", "현재는 투자를 저장하거나 확정할 수 없습니다."
            );
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "100자 이하의 Idempotency-Key 헤더가 필요합니다."
            );
        }
        return key.trim();
    }

    private ApiException invalidAllocation() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_INVESTMENT_ALLOCATION",
                "투자금은 서로 다른 1~3개 투자처에 10비빔 단위로 입력해 주세요."
        );
    }

    private ApiException insufficientBalance() {
        return new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "지갑 잔액이 부족합니다.");
    }

    private ApiException draftNotFound() {
        return new ApiException(HttpStatus.CONFLICT, "INVESTMENT_DRAFT_NOT_FOUND", "먼저 투자 초안을 저장해 주세요.");
    }

    private ApiException idempotencyKeyReused() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
    }

    private ApiException authenticationRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private record AssetDefinition(
            String code,
            String name,
            String description,
            String companyInfo,
            String riskLevel,
            int minReturnBps,
            int maxReturnBps,
            int actualReturnBps
    ) {
    }

    private record Market(
            List<InvestmentAssetEntity> assets,
            InvestmentScenarioEntity scenario
    ) {
    }

    private record ValidatedAllocations(
            List<AllocationRequest> requests,
            long totalAmount
    ) {
    }
}
