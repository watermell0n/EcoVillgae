package com.b201.bibim.feature.settlement;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.deposit.DepositEntity;
import com.b201.bibim.domain.deposit.DepositRepository;
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
import com.b201.bibim.domain.settlement.FinancialSettlementAllocationEntity;
import com.b201.bibim.domain.settlement.FinancialSettlementAllocationRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementItemEntity;
import com.b201.bibim.domain.settlement.FinancialSettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunEntity;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.settlement.Day2FinanceCalculator.AllocationInput;
import com.b201.bibim.feature.settlement.Day2FinanceCalculator.AllocationResult;
import com.b201.bibim.feature.settlement.Day2FinanceCalculator.Calculation;
import com.b201.bibim.feature.settlement.Day2FinanceDto.AllocationResultResponse;
import com.b201.bibim.feature.settlement.Day2FinanceDto.SettlementResponse;
import com.b201.bibim.feature.settlement.Day2FinanceDto.StudentFinanceResponse;
import com.b201.bibim.feature.settlement.Day2FinanceDto.TeacherFinancePreviewResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Day2FinanceService {

    private static final String DAY2_FINANCE = "DAY2_FINANCE";
    private static final String COMPLETED = "COMPLETED";
    private static final Set<String> PREVIEW_STAGES = Set.of(
            "DAY2_LOGIN",
            "DAY2_SETTLEMENT_WAIT",
            "DAY2_FINANCE_SETTLEMENT",
            "DAY2_RESULT_GUIDED"
    );

    private final RoomRepository roomRepository;
    private final RoomRulesRepository rulesRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final DepositRepository depositRepository;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAllocationRepository investmentAllocationRepository;
    private final InvestmentAssetRepository assetRepository;
    private final InvestmentScenarioRepository scenarioRepository;
    private final InvestmentScenarioReturnRepository scenarioReturnRepository;
    private final SettlementRunRepository runRepository;
    private final FinancialSettlementItemRepository itemRepository;
    private final FinancialSettlementAllocationRepository resultAllocationRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;
    private final Day2FinanceCalculator calculator;

    public Day2FinanceService(
            RoomRepository roomRepository,
            RoomRulesRepository rulesRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            DepositRepository depositRepository,
            InvestmentOrderRepository orderRepository,
            InvestmentAllocationRepository investmentAllocationRepository,
            InvestmentAssetRepository assetRepository,
            InvestmentScenarioRepository scenarioRepository,
            InvestmentScenarioReturnRepository scenarioReturnRepository,
            SettlementRunRepository runRepository,
            FinancialSettlementItemRepository itemRepository,
            FinancialSettlementAllocationRepository resultAllocationRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository,
            Day2FinanceCalculator calculator
    ) {
        this.roomRepository = roomRepository;
        this.rulesRepository = rulesRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.depositRepository = depositRepository;
        this.orderRepository = orderRepository;
        this.investmentAllocationRepository = investmentAllocationRepository;
        this.assetRepository = assetRepository;
        this.scenarioRepository = scenarioRepository;
        this.scenarioReturnRepository = scenarioReturnRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.resultAllocationRepository = resultAllocationRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public TeacherFinancePreviewResponse preview(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensurePreviewStage(room);
        return runRepository.findByRoomIdAndSettlementType(roomId, DAY2_FINANCE)
                .filter(run -> COMPLETED.equals(run.getStatus()))
                .map(run -> completedPreview(room, run))
                .orElseGet(() -> currentPreview(room));
    }

    @Transactional(readOnly = true)
    public StudentFinanceResponse studentResult(UUID studentId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        if (room.getCurrentDay() != 2) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY2_FINANCE_NOT_OPEN",
                    "2일차가 시작된 뒤 금융 결과를 확인할 수 있습니다."
            );
        }
        var completedRun = runRepository.findByRoomIdAndSettlementType(room.getId(), DAY2_FINANCE)
                .filter(run -> COMPLETED.equals(run.getStatus()));
        if (completedRun.isPresent()) {
            SettlementRunEntity run = completedRun.get();
            FinancialSettlementItemEntity item = itemRepository
                    .findBySettlementRunIdAndStudentId(run.getId(), studentId)
                    .orElseThrow(this::resultNotReady);
            return storedStudent(room, run, student, item);
        }
        return pendingStudent(student);
    }

    @Transactional
    public SettlementResponse settle(
            UUID teacherId,
            UUID roomId,
            String idempotencyKey
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        RoomEntity room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        if (!"DAY2_FINANCE_SETTLEMENT".equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY2_FINANCE_SETTLEMENT_NOT_OPEN",
                    "2일차 금융 정산 단계에서만 실행할 수 있습니다."
            );
        }

        var existingRun = runRepository.findByRoomIdAndSettlementType(roomId, DAY2_FINANCE);
        if (existingRun.isPresent()) {
            SettlementRunEntity run = existingRun.get();
            if (COMPLETED.equals(run.getStatus())) {
                return new SettlementResponse(completedPreview(room, run), true);
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_IN_PROGRESS",
                    "금융 정산이 이미 진행 중입니다."
            );
        }
        runRepository.findByIdempotencyKey(normalizedKey).ifPresent(run -> {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "같은 Idempotency-Key를 다른 정산에 사용할 수 없습니다."
            );
        });

        DaySessionEntity dayTwoSession = sessionRepository
                .findByRoomIdAndDayNumber(roomId, (short) 2)
                .orElseThrow(this::dayTwoSessionNotFound);
        List<StudentEntity> students = studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE");
        if (students.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_VALIDATION_FAILED",
                    "정산할 활성 학생이 없습니다."
            );
        }

        MarketContext market = marketContext(roomId);
        List<CalculatedStudent> calculated = students.stream()
                .map(student -> calculate(room, student, market))
                .toList();
        SettlementRunEntity run = runRepository.saveAndFlush(
                SettlementRunEntity.processing(
                        roomId,
                        dayTwoSession.getId(),
                        teacherId,
                        normalizedKey,
                        calculated.size(),
                        DAY2_FINANCE
                )
        );
        AccountEntity classFund = roomAccount(roomId, "CLASS_FUND");
        AccountEntity treasury = roomAccount(roomId, "SYSTEM_TREASURY");

        long totalDepositPrincipal = 0;
        long totalDepositInterest = 0;
        long totalInvestmentPrincipal = 0;
        long totalInvestmentProfit = 0;
        long totalFinancialTax = 0;
        long totalWalletAfter = 0;

        for (CalculatedStudent value : calculated) {
            StudentEntity student = value.student();
            Calculation amount = value.calculation();
            AccountEntity wallet = studentAccount(roomId, student.getId(), "STUDENT_WALLET");
            AccountEntity depositAccount = studentAccount(roomId, student.getId(), "STUDENT_DEPOSIT");
            AccountEntity investmentAccount = studentAccount(
                    roomId, student.getId(), "STUDENT_INVESTMENT"
            );
            validateAccountBalances(amount, wallet, depositAccount, investmentAccount);

            FinancialSettlementItemEntity item = itemRepository.saveAndFlush(
                    FinancialSettlementItemEntity.create(
                            run.getId(), student.getId(), amount.walletBefore(),
                            amount.depositPrincipal(), amount.depositInterest(),
                            amount.investmentPrincipal(), amount.investmentProfit(),
                            amount.investmentValue(), amount.taxableProfit(),
                            amount.financialTax(), amount.walletAfter()
                    )
            );
            resultAllocationRepository.saveAll(amount.allocations().stream()
                    .map(allocation -> FinancialSettlementAllocationEntity.create(
                            item.getId(), allocation.assetId(), allocation.principal(),
                            allocation.returnBps(), allocation.profit(), allocation.finalValue()
                    ))
                    .toList());

            transfer(room, dayTwoSession, teacherId, run, student,
                    depositAccount, wallet, amount.depositPrincipal(),
                    "DEPOSIT_PRINCIPAL_RETURN", normalizedKey, "예금 원금 반환");
            transfer(room, dayTwoSession, teacherId, run, student,
                    treasury, wallet, amount.depositInterest(),
                    "DEPOSIT_INTEREST", normalizedKey, "예금 이자 지급");
            transfer(room, dayTwoSession, teacherId, run, student,
                    investmentAccount, wallet, amount.investmentPrincipal(),
                    "INVESTMENT_PRINCIPAL_RETURN", normalizedKey, "투자 원금 반환");
            if (amount.investmentProfit() > 0) {
                transfer(room, dayTwoSession, teacherId, run, student,
                        treasury, wallet, amount.investmentProfit(),
                        "INVESTMENT_PROFIT", normalizedKey, "투자 수익 지급");
            } else if (amount.investmentProfit() < 0) {
                transfer(room, dayTwoSession, teacherId, run, student,
                        wallet, treasury, -amount.investmentProfit(),
                        "INVESTMENT_LOSS", normalizedKey, "투자 손실 반영");
            }
            transfer(room, dayTwoSession, teacherId, run, student,
                    wallet, classFund, amount.financialTax(),
                    "FINANCIAL_TAX", normalizedKey, "금융세 납부");
            if (wallet.getBalance() != amount.walletAfter()
                    || depositAccount.getBalance() != 0
                    || investmentAccount.getBalance() != 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "SETTLEMENT_VALIDATION_FAILED",
                        "금융 정산 후 계정 잔액이 예상과 다릅니다."
                );
            }

            totalDepositPrincipal = Math.addExact(
                    totalDepositPrincipal, amount.depositPrincipal()
            );
            totalDepositInterest = Math.addExact(totalDepositInterest, amount.depositInterest());
            totalInvestmentPrincipal = Math.addExact(
                    totalInvestmentPrincipal, amount.investmentPrincipal()
            );
            totalInvestmentProfit = Math.addExact(
                    totalInvestmentProfit, amount.investmentProfit()
            );
            totalFinancialTax = Math.addExact(totalFinancialTax, amount.financialTax());
            totalWalletAfter = Math.addExact(totalWalletAfter, amount.walletAfter());
        }

        run.completeFinancial(
                totalDepositPrincipal,
                totalDepositInterest,
                totalInvestmentPrincipal,
                totalInvestmentProfit,
                totalFinancialTax,
                totalWalletAfter
        );
        room.markEconomicStateChanged();
        return new SettlementResponse(completedPreview(room, run), false);
    }

    private TeacherFinancePreviewResponse currentPreview(RoomEntity room) {
        MarketContext market = marketContext(room.getId());
        List<StudentFinanceResponse> students = studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(room.getId(), "ACTIVE")
                .stream()
                .map(student -> calculatedStudent(room, student, market, "NOT_STARTED", null))
                .toList();
        return teacherResponse(room, "NOT_STARTED", null, students);
    }

    private TeacherFinancePreviewResponse completedPreview(
            RoomEntity room,
            SettlementRunEntity run
    ) {
        Map<UUID, StudentEntity> studentsById = studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(room.getId(), "ACTIVE")
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        List<StudentFinanceResponse> students = itemRepository
                .findAllBySettlementRunId(run.getId())
                .stream()
                .map(item -> storedStudent(
                        room,
                        run,
                        studentsById.get(item.getStudentId()),
                        item
                ))
                .sorted(Comparator.comparingInt(StudentFinanceResponse::participantNumber))
                .toList();
        return teacherResponse(room, COMPLETED, run, students);
    }

    private TeacherFinancePreviewResponse teacherResponse(
            RoomEntity room,
            String status,
            SettlementRunEntity run,
            List<StudentFinanceResponse> students
    ) {
        RoomRulesEntity rules = rules(room.getId());
        return new TeacherFinancePreviewResponse(
                room.getId(), status, rules.getFinancialTaxBps(), students.size(),
                sum(students, StudentFinanceResponse::depositPrincipal),
                sum(students, StudentFinanceResponse::depositInterest),
                sum(students, StudentFinanceResponse::investmentPrincipal),
                sum(students, StudentFinanceResponse::investmentProfit),
                sum(students, StudentFinanceResponse::financialTax),
                sum(students, StudentFinanceResponse::walletAfter),
                balance(room.getId(), null, "CLASS_FUND"),
                run == null ? null : run.getCompletedAt(),
                students
        );
    }

    private StudentFinanceResponse calculatedStudent(
            RoomEntity room,
            StudentEntity student,
            MarketContext market,
            String status,
            java.time.Instant completedAt
    ) {
        CalculatedStudent value = calculate(room, student, market);
        return response(student, status, value.calculation(), market.assets(), completedAt);
    }

    private CalculatedStudent calculate(
            RoomEntity room,
            StudentEntity student,
            MarketContext market
    ) {
        DaySessionEntity dayOne = sessionRepository
                .findByRoomIdAndDayNumber(room.getId(), (short) 1)
                .orElseThrow(this::dayOneSessionNotFound);
        DepositEntity deposit = depositRepository
                .findBySessionIdAndStudentId(dayOne.getId(), student.getId())
                .filter(item -> "CONFIRMED".equals(item.getStatus()))
                .orElse(null);
        InvestmentOrderEntity order = orderRepository
                .findBySessionIdAndStudentId(dayOne.getId(), student.getId())
                .filter(item -> "CONFIRMED".equals(item.getStatus()))
                .orElse(null);
        List<AllocationInput> allocations = order == null
                ? List.of()
                : investmentAllocationRepository.findAllByOrderId(order.getId()).stream()
                        .map(item -> new AllocationInput(
                                item.getAssetId(),
                                item.getAmount(),
                                market.returnBps().getOrDefault(item.getAssetId(), 0)
                        ))
                        .toList();
        if (order != null && allocations.stream().anyMatch(
                item -> !market.returnBps().containsKey(item.assetId())
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_VALIDATION_FAILED",
                    "투자 수익률 시나리오를 찾을 수 없습니다."
            );
        }
        Calculation calculation;
        try {
            calculation = calculator.calculate(
                    balance(room.getId(), student.getId(), "STUDENT_WALLET"),
                    deposit == null ? 0 : deposit.getPrincipal(),
                    deposit == null ? 0 : deposit.getInterestRateBps(),
                    allocations,
                    rules(room.getId()).getFinancialTaxBps()
            );
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_VALIDATION_FAILED",
                    "금융 정산 금액을 계산할 수 없습니다."
            );
        }
        return new CalculatedStudent(student, calculation);
    }

    private StudentFinanceResponse storedStudent(
            RoomEntity room,
            SettlementRunEntity run,
            StudentEntity student,
            FinancialSettlementItemEntity item
    ) {
        if (student == null) throw resultNotReady();
        Map<UUID, InvestmentAssetEntity> assets = assets(room.getId());
        List<AllocationResultResponse> allocations = resultAllocationRepository
                .findAllBySettlementItemId(item.getId())
                .stream()
                .map(value -> allocationResponse(
                        assets,
                        value.getAssetId(),
                        value.getPrincipal(),
                        value.getReturnBps(),
                        value.getProfit(),
                        value.getFinalValue()
                ))
                .toList();
        return new StudentFinanceResponse(
                student.getId(), student.getParticipantNumber(), student.getNickname(),
                COMPLETED, true, item.getWalletBefore(), item.getDepositPrincipal(),
                item.getDepositInterest(), item.getInvestmentPrincipal(),
                item.getInvestmentProfit(), item.getInvestmentValue(),
                item.getTaxableProfit(), item.getFinancialTax(), item.getWalletAfter(),
                allocations, run.getCompletedAt()
        );
    }

    private StudentFinanceResponse response(
            StudentEntity student,
            String status,
            Calculation amount,
            Map<UUID, InvestmentAssetEntity> assets,
            java.time.Instant completedAt
    ) {
        List<AllocationResultResponse> allocations = amount.allocations().stream()
                .map(value -> allocationResponse(
                        assets,
                        value.assetId(),
                        value.principal(),
                        value.returnBps(),
                        value.profit(),
                        value.finalValue()
                ))
                .toList();
        return new StudentFinanceResponse(
                student.getId(), student.getParticipantNumber(), student.getNickname(),
                status, COMPLETED.equals(status), amount.walletBefore(), amount.depositPrincipal(),
                amount.depositInterest(), amount.investmentPrincipal(),
                amount.investmentProfit(), amount.investmentValue(),
                amount.taxableProfit(), amount.financialTax(), amount.walletAfter(),
                allocations, completedAt
        );
    }

    private StudentFinanceResponse pendingStudent(StudentEntity student) {
        return new StudentFinanceResponse(
                student.getId(), student.getParticipantNumber(), student.getNickname(),
                "NOT_STARTED", false,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null
        );
    }

    private AllocationResultResponse allocationResponse(
            Map<UUID, InvestmentAssetEntity> assets,
            UUID assetId,
            long principal,
            int returnBps,
            long profit,
            long finalValue
    ) {
        InvestmentAssetEntity asset = assets.get(assetId);
        return new AllocationResultResponse(
                assetId,
                asset == null ? "알 수 없는 투자처" : asset.getName(),
                asset == null ? "UNKNOWN" : asset.getRiskLevel(),
                principal,
                returnBps,
                profit,
                finalValue
        );
    }

    private MarketContext marketContext(UUID roomId) {
        Map<UUID, InvestmentAssetEntity> assets = assets(roomId);
        InvestmentScenarioEntity scenario = scenarioRepository.findByRoomId(roomId).orElse(null);
        if (scenario == null) return new MarketContext(assets, Map.of());
        Map<UUID, Integer> returnBps = scenarioReturnRepository
                .findAllByScenarioId(scenario.getId())
                .stream()
                .collect(Collectors.toMap(
                        InvestmentScenarioReturnEntity::getAssetId,
                        InvestmentScenarioReturnEntity::getReturnBps
                ));
        return new MarketContext(assets, returnBps);
    }

    private Map<UUID, InvestmentAssetEntity> assets(UUID roomId) {
        return assetRepository.findAllByRoomIdOrderByDisplayOrder(roomId).stream()
                .collect(Collectors.toMap(InvestmentAssetEntity::getId, Function.identity()));
    }

    private void validateAccountBalances(
            Calculation amount,
            AccountEntity wallet,
            AccountEntity deposit,
            AccountEntity investment
    ) {
        if (wallet.getBalance() != amount.walletBefore()
                || deposit.getBalance() != amount.depositPrincipal()
                || investment.getBalance() != amount.investmentPrincipal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_VALIDATION_FAILED",
                    "원장 잔액과 확정된 금융 활동 금액이 일치하지 않습니다."
            );
        }
    }

    private void transfer(
            RoomEntity room,
            DaySessionEntity session,
            UUID teacherId,
            SettlementRunEntity run,
            StudentEntity student,
            AccountEntity from,
            AccountEntity to,
            long amount,
            String transactionType,
            String baseKey,
            String description
    ) {
        if (amount == 0) return;
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.success(
                        room.getId(), session.getId(), student.getId(), teacherId,
                        run.getId(), transactionType,
                        baseKey + ":" + student.getId() + ":" + transactionType,
                        description,
                        Map.of("amount", amount, "dayNumber", 2)
                )
        );
        long fromBefore = from.getBalance();
        long toBefore = to.getBalance();
        from.changeBy(-amount);
        to.changeBy(amount);
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(transaction.getId(), from, -amount, fromBefore, 1),
                FinancialEntryEntity.create(transaction.getId(), to, amount, toBefore, 2)
        ));
    }

    private AccountEntity studentAccount(UUID roomId, UUID studentId, String type) {
        return accountRepository.findStudentAccountForUpdate(roomId, studentId, type)
                .orElseGet(() -> accountRepository.saveAndFlush(switch (type) {
                    case "STUDENT_WALLET" -> AccountEntity.studentWallet(roomId, studentId);
                    case "STUDENT_DEPOSIT" -> AccountEntity.studentDeposit(roomId, studentId);
                    case "STUDENT_INVESTMENT" -> AccountEntity.studentInvestment(roomId, studentId);
                    default -> throw new IllegalArgumentException("지원하지 않는 학생 계정입니다.");
                }));
    }

    private AccountEntity roomAccount(UUID roomId, String type) {
        return accountRepository.findRoomAccountForUpdate(roomId, type)
                .orElseGet(() -> accountRepository.saveAndFlush(
                        "CLASS_FUND".equals(type)
                                ? AccountEntity.classFund(roomId)
                                : AccountEntity.systemTreasury(roomId)
                ));
    }

    private long balance(UUID roomId, UUID studentId, String type) {
        if (studentId == null) {
            return accountRepository.findByRoomIdAndStudentIdIsNullAndAccountType(roomId, type)
                    .map(AccountEntity::getBalance)
                    .orElse(0L);
        }
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(roomId, studentId, type)
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private RoomRulesEntity rules(UUID roomId) {
        return rulesRepository.findByRoomId(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT,
                "SETTLEMENT_VALIDATION_FAILED",
                "수업 경제 규칙을 찾을 수 없습니다."
        ));
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        return room;
    }

    private void ensureOwner(RoomEntity room, UUID teacherId) {
        if (!room.getTeacherId().equals(teacherId)) throw roomNotFound();
    }

    private void ensurePreviewStage(RoomEntity room) {
        if (!PREVIEW_STAGES.contains(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY2_FINANCE_NOT_OPEN",
                    "2일차 금융 정산 단계에서 결과를 확인할 수 있습니다."
            );
        }
    }

    private StudentEntity activeStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
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

    private long sum(
            List<StudentFinanceResponse> students,
            java.util.function.ToLongFunction<StudentFinanceResponse> extractor
    ) {
        return students.stream().mapToLong(extractor).sum();
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private ApiException dayOneSessionNotFound() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_FOUND", "1일차 세션을 찾을 수 없습니다.");
    }

    private ApiException dayTwoSessionNotFound() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_FOUND", "2일차 세션을 찾을 수 없습니다.");
    }

    private ApiException resultNotReady() {
        return new ApiException(HttpStatus.CONFLICT, "FINANCE_RESULT_NOT_READY", "금융 정산 결과를 찾을 수 없습니다.");
    }

    private record MarketContext(
            Map<UUID, InvestmentAssetEntity> assets,
            Map<UUID, Integer> returnBps
    ) {
    }

    private record CalculatedStudent(StudentEntity student, Calculation calculation) {
    }
}
