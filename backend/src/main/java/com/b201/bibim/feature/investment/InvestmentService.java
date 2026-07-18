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
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.investment.InvestmentDto.AllocationRequest;
import com.b201.bibim.feature.investment.InvestmentDto.AllocationResponse;
import com.b201.bibim.feature.investment.InvestmentDto.AssetResponse;
import com.b201.bibim.feature.investment.InvestmentDto.InvestmentResponse;
import com.b201.bibim.feature.investment.InvestmentDto.SaveDraftRequest;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.flow.StudentFlowService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentService {

    private static final String OPEN_STAGE = "DAY1_FREE_ACTIVITY";
    private static final long AMOUNT_UNIT = 10;

    private static final List<AssetDefinition> DEFAULT_ASSETS = List.of(
            new AssetDefinition(
                    "SAFE_BOND", "튼튼 채권", "변동이 작아 비교적 안정적인 투자예요.",
                    "LOW", -200, 400, 200
            ),
            new AssetDefinition(
                    "LOCAL_STORE", "우리 동네 가게", "수익과 손실 가능성이 모두 있는 중간 위험 투자예요.",
                    "MEDIUM", -800, 1_200, 700
            ),
            new AssetDefinition(
                    "FUTURE_TECH", "미래 기술", "큰 수익을 기대할 수 있지만 손실 위험도 커요.",
                    "HIGH", -2_000, 2_500, -600
            )
    );

    private final InvestmentAssetRepository assetRepository;
    private final InvestmentScenarioRepository scenarioRepository;
    private final InvestmentScenarioReturnRepository scenarioReturnRepository;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAllocationRepository allocationRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
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

    @Transactional
    public InvestmentResponse saveDraft(UUID studentId, SaveDraftRequest request) {
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);
        Market market = ensureMarket(room.getId());
        ValidatedAllocations validated = validateAllocations(request.allocations(), market.assets());
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
                .findAllByRoomIdOrderByDisplayOrder(roomId);
        if (assets.isEmpty()) {
            List<InvestmentAssetEntity> created = new ArrayList<>();
            for (int index = 0; index < DEFAULT_ASSETS.size(); index += 1) {
                AssetDefinition definition = DEFAULT_ASSETS.get(index);
                created.add(InvestmentAssetEntity.create(
                        roomId,
                        definition.code(),
                        definition.name(),
                        definition.description(),
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

    private ValidatedAllocations validateAllocations(
            List<AllocationRequest> requests,
            List<InvestmentAssetEntity> assets
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
                    || request.amount() <= 0 || request.amount() % AMOUNT_UNIT != 0) {
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
            return new InvestmentResponse(
                    null, "NONE", 0, walletBalance,
                    "LOCKED".equals(market.scenario().getStatus()),
                    assetResponses, List.of(), null, null, replayed
            );
        }
        Map<UUID, InvestmentAssetEntity> assetsById = market.assets().stream()
                .collect(Collectors.toMap(InvestmentAssetEntity::getId, Function.identity()));
        List<AllocationResponse> allocations = allocationRepository.findAllByOrderId(order.getId())
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
        return new InvestmentResponse(
                order.getId(), order.getStatus(), order.getTotalAmount(), walletBalance,
                "LOCKED".equals(market.scenario().getStatus()),
                assetResponses, allocations, order.getUpdatedAt(), order.getConfirmedAt(), replayed
        );
    }

    private AssetResponse toAssetResponse(InvestmentAssetEntity asset) {
        return new AssetResponse(
                asset.getId(), asset.getCode(), asset.getName(), asset.getDescription(),
                asset.getRiskLevel(), asset.getMinReturnBps(), asset.getMaxReturnBps(),
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
