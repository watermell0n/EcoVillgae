package com.b201.bibim.feature.fund;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.b201.bibim.domain.fund.FundCostOptionEntity;
import com.b201.bibim.domain.fund.FundCostOptionRepository;
import com.b201.bibim.domain.fund.FundExpenseEntity;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.fund.VoteEligibleStudentEntity;
import com.b201.bibim.domain.fund.VoteEligibleStudentRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunEntity;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.vote.VoteEntity;
import com.b201.bibim.domain.vote.VoteOptionEntity;
import com.b201.bibim.domain.vote.VoteOptionRepository;
import com.b201.bibim.domain.vote.VoteRepository;
import com.b201.bibim.domain.vote.VoteSessionEntity;
import com.b201.bibim.domain.vote.VoteSessionRepository;
import com.b201.bibim.feature.fund.FundVoteDto.ApplyExpenseResponse;
import com.b201.bibim.feature.fund.FundVoteDto.AddFundRequest;
import com.b201.bibim.feature.fund.FundVoteDto.CastVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.CostOptionResponse;
import com.b201.bibim.feature.fund.FundVoteDto.CreateVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.DecideVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.FundExpenseResponse;
import com.b201.bibim.feature.fund.FundVoteDto.FundOverviewResponse;
import com.b201.bibim.feature.fund.FundVoteDto.VoteOptionResponse;
import com.b201.bibim.feature.fund.FundVoteDto.VoteParticipationResponse;
import com.b201.bibim.feature.fund.FundVoteDto.VoteSessionResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundVoteService {

    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final DaySessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;
    private final FundCostOptionRepository costOptionRepository;
    private final FundExpenseRepository expenseRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final VoteRepository voteRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final FinancialSettlementItemRepository financialItemRepository;
    private final VoteEligibleStudentRepository voteEligibleStudentRepository;

    public FundVoteService(
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            DaySessionRepository sessionRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository,
            FundCostOptionRepository costOptionRepository,
            FundExpenseRepository expenseRepository,
            VoteSessionRepository voteSessionRepository,
            VoteOptionRepository voteOptionRepository,
            VoteRepository voteRepository,
            SettlementRunRepository settlementRunRepository,
            SettlementItemRepository settlementItemRepository,
            FinancialSettlementItemRepository financialItemRepository,
            VoteEligibleStudentRepository voteEligibleStudentRepository
    ) {
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.costOptionRepository = costOptionRepository;
        this.expenseRepository = expenseRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.voteOptionRepository = voteOptionRepository;
        this.voteRepository = voteRepository;
        this.settlementRunRepository = settlementRunRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.financialItemRepository = financialItemRepository;
        this.voteEligibleStudentRepository = voteEligibleStudentRepository;
    }

    @Transactional(readOnly = true)
    public FundOverviewResponse teacherFund(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        return fundOverview(room);
    }

    @Transactional(readOnly = true)
    public FundOverviewResponse studentFund(UUID studentId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureFundVisible(room);
        return fundOverview(room);
    }

    @Transactional
    public List<CostOptionResponse> costOptions(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        return costOptionsForRoom(room);
    }

    @Transactional
    public List<CostOptionResponse> studentCostOptions(UUID studentId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureFundVisible(room);
        return costOptionsForRoom(room);
    }

    private List<CostOptionResponse> costOptionsForRoom(RoomEntity room) {
        List<FundCostOptionEntity> options = ensureCostOptions(room.getId());
        FundOverviewResponse fund = fundOverview(room);
        UUID appliedId = fund.appliedExpense() == null ? null : fund.appliedExpense().costOptionId();
        return options.stream()
                .map(option -> new CostOptionResponse(
                        option.getId(), option.getName(), option.getAmount(), option.isEnabled(),
                        option.getAmount() <= fund.balance(), option.getId().equals(appliedId)
                ))
                .toList();
    }

    @Transactional
    public FundOverviewResponse addFund(
            UUID teacherId,
            UUID roomId,
            String idempotencyKey,
            AddFundRequest request
    ) {
        String key = normalizeKey(idempotencyKey);
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureRoomWritable(room);
        if (!"DAY2_FUND_COST".equals(room.getCurrentStage())) {
            throw conflict("CLASS_FUND_ADDITION_NOT_OPEN", "공동기금 비용 확인 단계에서만 기금을 추가할 수 있습니다.");
        }
        var previous = transactionRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            FinancialTransactionEntity transaction = previous.get();
            if (!roomId.equals(transaction.getRoomId()) || !"CLASS_FUND_ADDITION".equals(transaction.getTransactionType())) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "같은 요청 키를 다른 요청에 사용할 수 없습니다.");
            }
            return fundOverview(room);
        }
        if (voteSessionRepository.findByRoomId(roomId).isPresent()) {
            throw conflict("FUND_VOTE_ALREADY_PREPARED", "투표를 준비한 뒤에는 공동기금을 추가할 수 없습니다.");
        }

        AccountEntity fund = accountRepository.findRoomAccountForUpdate(roomId, "CLASS_FUND")
                .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.classFund(roomId)));
        AccountEntity treasury = accountRepository.findRoomAccountForUpdate(roomId, "SYSTEM_TREASURY")
                .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.systemTreasury(roomId)));
        DaySessionEntity session = dayTwoSession(roomId);
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.success(
                        roomId, session.getId(), null, teacherId, null, "CLASS_FUND_ADDITION", key,
                        request.reason().trim(), Map.of("amount", request.amount(), "reason", request.reason().trim())
                )
        );
        long treasuryBefore = treasury.getBalance();
        long fundBefore = fund.getBalance();
        treasury.changeBy(-request.amount());
        fund.changeBy(request.amount());
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(transaction.getId(), treasury, -request.amount(), treasuryBefore, 1),
                FinancialEntryEntity.create(transaction.getId(), fund, request.amount(), fundBefore, 2)
        ));
        room.markEconomicStateChanged();
        return fundOverview(room);
    }

    @Transactional
    public ApplyExpenseResponse applyExpense(
            UUID teacherId,
            UUID roomId,
            String idempotencyKey,
            UUID costOptionId
    ) {
        String key = normalizeKey(idempotencyKey);
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureRoomWritable(room);
        if (!"DAY2_FUND_COST".equals(room.getCurrentStage())) {
            throw conflict("FUND_EXPENSE_NOT_OPEN", "공동기금 비용 확인 단계에서만 비용을 적용할 수 있습니다.");
        }

        var previous = transactionRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            FinancialTransactionEntity transaction = previous.get();
            if (!roomId.equals(transaction.getRoomId())
                    || !"CLASS_FUND_EXPENSE".equals(transaction.getTransactionType())
                    || expenseRepository.findByTransactionId(transaction.getId()).isEmpty()) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "같은 요청 키를 다른 요청에 사용할 수 없습니다.");
            }
            return new ApplyExpenseResponse(fundOverview(room), true);
        }
        if (voteSessionRepository.findByRoomId(roomId).isPresent()) {
            throw conflict(
                    "FUND_VOTE_ALREADY_PREPARED",
                    "투표 선택지를 준비한 뒤에는 공동비용을 적용할 수 없습니다."
            );
        }
        if (expenseRepository.existsByRoomId(roomId)) {
            throw conflict("FUND_EXPENSE_ALREADY_APPLIED", "공동비용은 한 번만 적용할 수 있습니다.");
        }

        FundCostOptionEntity option = costOptionRepository.findById(costOptionId)
                .filter(item -> item.getRoomId().equals(roomId) && item.isEnabled())
                .orElseThrow(() -> conflict("FUND_COST_OPTION_NOT_FOUND", "선택한 공동비용을 찾을 수 없습니다."));
        AccountEntity fund = accountRepository.findRoomAccountForUpdate(roomId, "CLASS_FUND")
                .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.classFund(roomId)));
        if (fund.getBalance() < option.getAmount()) {
            throw conflict("CLASS_FUND_INSUFFICIENT", "공동기금 잔액보다 큰 비용은 적용할 수 없습니다.");
        }
        AccountEntity expenseAccount = accountRepository.findRoomAccountForUpdate(roomId, "CLASS_EXPENSE")
                .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.classExpense(roomId)));
        DaySessionEntity session = dayTwoSession(roomId);
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.success(
                        roomId, session.getId(), null, teacherId, null, "CLASS_FUND_EXPENSE", key,
                        option.getName() + " 공동비용 적용",
                        Map.of("costOptionId", option.getId().toString(), "amount", option.getAmount())
                )
        );
        long fundBefore = fund.getBalance();
        long expenseBefore = expenseAccount.getBalance();
        fund.changeBy(-option.getAmount());
        expenseAccount.changeBy(option.getAmount());
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(transaction.getId(), fund, -option.getAmount(), fundBefore, 1),
                FinancialEntryEntity.create(transaction.getId(), expenseAccount, option.getAmount(), expenseBefore, 2)
        ));
        expenseRepository.save(FundExpenseEntity.applied(roomId, option, transaction.getId(), teacherId));
        room.markEconomicStateChanged();
        return new ApplyExpenseResponse(fundOverview(room), false);
    }

    @Transactional
    public VoteSessionResponse createVote(UUID teacherId, UUID roomId, CreateVoteRequest request) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureRoomWritable(room);
        if (!"DAY2_FUND_COST".equals(room.getCurrentStage())) {
            throw conflict("VOTE_CREATE_NOT_OPEN", "공동기금 비용 확인 단계에서 투표를 준비해 주세요.");
        }
        var existing = voteSessionRepository.findByRoomId(roomId);
        if (existing.isPresent()) return toVoteResponse(existing.get(), null, true);

        List<String> normalizedTitles = request.options().stream()
                .map(option -> option.title().trim().toLowerCase(Locale.ROOT))
                .toList();
        if (normalizedTitles.stream().distinct().count() != normalizedTitles.size()) {
            throw conflict("DUPLICATE_VOTE_OPTION", "같은 이름의 투표 선택지를 두 번 사용할 수 없습니다.");
        }
        long fundBalance = currentFundBalance(roomId);
        VoteSessionEntity session = voteSessionRepository.saveAndFlush(
                VoteSessionEntity.ready(roomId, request.question().trim(), fundBalance)
        );
        for (int index = 0; index < request.options().size(); index++) {
            var option = request.options().get(index);
            voteOptionRepository.save(VoteOptionEntity.create(
                    session.getId(), option.title().trim(), option.description().trim(),
                    option.requiredAmount(), index + 1
            ));
        }
        room.markControlStateChanged();
        return toVoteResponse(session, null, true);
    }

    @Transactional(readOnly = true)
    public VoteSessionResponse teacherVote(UUID teacherId, UUID roomId) {
        findOwnedRoom(teacherId, roomId);
        return currentVote(roomId, null, true);
    }

    @Transactional(readOnly = true)
    public VoteSessionResponse studentVote(UUID studentId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureVoteVisible(room);
        return currentVote(room.getId(), studentId, isVoteResultVisible(room));
    }

    @Transactional
    public VoteSessionResponse openVote(UUID teacherId, UUID roomId, UUID voteId) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureRoomWritable(room);
        if (!"DAY2_FUND_VOTE".equals(room.getCurrentStage())) {
            throw conflict("VOTE_NOT_OPEN", "공동기금 투표 단계에서 투표를 시작해 주세요.");
        }
        VoteSessionEntity session = lockedVote(roomId, voteId);
        if (voteEligibleStudentRepository.countByVoteSessionId(voteId) == 0) {
            voteEligibleStudentRepository.saveAll(
                    activeStudents(roomId).stream()
                            .map(student -> VoteEligibleStudentEntity.create(
                                    voteId, student.getId()
                            ))
                            .toList()
            );
        }
        try {
            session.open();
        } catch (IllegalStateException exception) {
            throw conflict("VOTE_STATE_CONFLICT", exception.getMessage());
        }
        room.markControlStateChanged();
        return toVoteResponse(session, null, true);
    }

    @Transactional
    public VoteSessionResponse closeVote(
            UUID teacherId,
            UUID roomId,
            UUID voteId,
            boolean force
    ) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureRoomWritable(room);
        if (!"DAY2_FUND_VOTE".equals(room.getCurrentStage())) {
            throw conflict("VOTE_NOT_OPEN", "공동기금 투표 단계에서 투표를 마감해 주세요.");
        }
        VoteSessionEntity session = lockedVote(roomId, voteId);
        if (!force && hasPendingVoters(session)) {
            throw conflict("VOTE_INCOMPLETE", "아직 공동기금 투표에 참여하지 않은 학생이 있습니다.");
        }
        try {
            session.close();
        } catch (IllegalStateException exception) {
            throw conflict("VOTE_STATE_CONFLICT", exception.getMessage());
        }
        List<UUID> leaders = leadingOptionIds(session.getId(), roomId);
        if (leaders.size() == 1) session.decide(leaders.get(0), null);
        room.markControlStateChanged();
        return toVoteResponse(session, null, true);
    }

    @Transactional
    public VoteSessionResponse decideVote(
            UUID teacherId,
            UUID roomId,
            UUID voteId,
            DecideVoteRequest request
    ) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureRoomWritable(room);
        VoteSessionEntity session = lockedVote(roomId, voteId);
        if (!leadingOptionIds(session.getId(), roomId).contains(request.optionId())) {
            throw conflict("VOTE_DECISION_INVALID", "동률 1위 선택지 중에서 최종 결과를 골라 주세요.");
        }
        try {
            session.decide(request.optionId(), teacherId);
        } catch (IllegalStateException exception) {
            throw conflict("VOTE_STATE_CONFLICT", exception.getMessage());
        }
        room.markControlStateChanged();
        return toVoteResponse(session, null, true);
    }

    @Transactional
    public VoteSessionResponse castVote(UUID studentId, UUID voteId, CastVoteRequest request) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findByIdForUpdate(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureRoomWritable(room);
        if (!"DAY2_FUND_VOTE".equals(room.getCurrentStage())) {
            throw conflict("VOTE_NOT_OPEN", "지금은 투표할 수 없습니다.");
        }
        VoteSessionEntity session = lockedVote(room.getId(), voteId);
        if (!isEligible(session, studentId)) {
            throw conflict(
                    "VOTE_NOT_ELIGIBLE",
                    "투표가 시작된 뒤 참여한 학생은 이번 투표를 선택할 수 없습니다."
            );
        }
        if (!"OPEN".equals(session.getStatus())) {
            throw conflict("VOTE_NOT_OPEN", "투표가 진행 중일 때만 참여할 수 있습니다.");
        }
        VoteOptionEntity option = voteOptionRepository.findById(request.optionId())
                .filter(item -> item.getVoteSessionId().equals(voteId))
                .orElseThrow(() -> conflict("VOTE_OPTION_NOT_FOUND", "선택한 투표 항목을 찾을 수 없습니다."));
        if (option.getRequiredAmount() > session.getFundBalanceSnapshot()) {
            throw conflict("CLASS_FUND_INSUFFICIENT", "공동기금으로 선택할 수 없는 항목입니다.");
        }
        if (voteRepository.existsByVoteSessionIdAndStudentId(voteId, studentId)) {
            throw conflict("VOTE_ALREADY_SUBMITTED", "투표는 한 번만 제출할 수 있습니다.");
        }
        voteRepository.saveAndFlush(VoteEntity.cast(voteId, studentId, option.getId()));
        room.markControlStateChanged();
        return toVoteResponse(session, studentId, false);
    }

    @Transactional(readOnly = true)
    public VoteSessionResponse voteResult(UUID studentId, UUID voteId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        if (!isVoteResultVisible(room)) {
            throw conflict("VOTE_RESULT_NOT_OPEN", "선생님이 투표 결과 확인 단계로 이동한 뒤 결과를 확인할 수 있습니다.");
        }
        VoteSessionEntity session = voteSessionRepository.findById(voteId)
                .filter(item -> item.getRoomId().equals(student.getRoomId()))
                .orElseThrow(() -> conflict("VOTE_NOT_FOUND", "투표를 찾을 수 없습니다."));
        if (!List.of("CLOSED", "DECIDED").contains(session.getStatus())) {
            throw conflict("VOTE_RESULT_NOT_OPEN", "투표를 마감한 뒤 결과를 확인할 수 있습니다.");
        }
        return toVoteResponse(session, studentId, true);
    }

    private FundOverviewResponse fundOverview(RoomEntity room) {
        long balance = currentFundBalance(room.getId());
        FundExpenseResponse expense = expenseRepository.findByRoomId(room.getId())
                .map(item -> new FundExpenseResponse(
                        item.getId(), item.getCostOptionId(), item.getNameSnapshot(),
                        item.getAmount(), item.getAppliedAt()
                ))
                .orElse(null);
        long incomeTax = settlementTotal(room.getId(), "DAY1_INCOME", false);
        long financialTax = settlementTotal(room.getId(), "DAY2_FINANCE", true);
        return new FundOverviewResponse(
                room.getId(), balance, incomeTax, financialTax,
                expense == null ? 0 : expense.amount(), expense
        );
    }

    private long settlementTotal(UUID roomId, String type, boolean financial) {
        return settlementRunRepository.findByRoomIdAndSettlementType(roomId, type)
                .filter(run -> "COMPLETED".equals(run.getStatus()))
                .map(run -> financial
                        ? financialItemRepository.findAllBySettlementRunId(run.getId()).stream()
                                .mapToLong(item -> item.getFinancialTax()).sum()
                        : settlementItemRepository.findAllBySettlementRunId(run.getId()).stream()
                                .mapToLong(item -> item.getIncomeTax()).sum())
                .orElse(0L);
    }

    private VoteSessionResponse currentVote(UUID roomId, UUID studentId, boolean allowResultReveal) {
        VoteSessionEntity session = voteSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> conflict("VOTE_NOT_FOUND", "준비된 공동기금 투표가 없습니다."));
        return toVoteResponse(session, studentId, allowResultReveal);
    }

    private VoteSessionResponse toVoteResponse(
            VoteSessionEntity session,
            UUID studentId,
            boolean allowResultReveal
    ) {
        List<VoteOptionEntity> options = voteOptionRepository
                .findAllByVoteSessionIdOrderByDisplayOrder(session.getId());
        List<StudentEntity> eligibleStudents = eligibleStudents(session);
        Set<UUID> eligibleStudentIds = eligibleStudents.stream()
                .map(StudentEntity::getId)
                .collect(Collectors.toSet());
        List<VoteEntity> votes = voteRepository.findAllByVoteSessionId(session.getId()).stream()
                .filter(vote -> eligibleStudentIds.contains(vote.getStudentId()))
                .toList();
        Map<UUID, Long> counts = votes.stream().collect(Collectors.groupingBy(
                VoteEntity::getOptionId, LinkedHashMap::new, Collectors.counting()
        ));
        long maxVotes = options.stream().mapToLong(option -> counts.getOrDefault(option.getId(), 0L)).max().orElse(0L);
        boolean reveal = allowResultReveal && List.of("CLOSED", "DECIDED").contains(session.getStatus());
        UUID selected = studentId == null ? null : votes.stream()
                .filter(vote -> vote.getStudentId().equals(studentId))
                .map(VoteEntity::getOptionId)
                .findFirst()
                .orElse(null);
        List<VoteOptionResponse> responseOptions = options.stream()
                .map(option -> {
                    long count = counts.getOrDefault(option.getId(), 0L);
                    return new VoteOptionResponse(
                            option.getId(), option.getTitle(), option.getDescription(), option.getRequiredAmount(),
                            option.getRequiredAmount() <= session.getFundBalanceSnapshot(),
                            reveal ? count : null,
                            reveal && count == maxVotes,
                            reveal && option.getId().equals(session.getDecidedOptionId())
                    );
                })
                .toList();
        Set<UUID> submittedStudentIds = votes.stream()
                .map(VoteEntity::getStudentId)
                .collect(Collectors.toSet());
        List<VoteParticipationResponse> pendingStudents = studentId == null
                ? eligibleStudents.stream()
                        .filter(student -> !submittedStudentIds.contains(student.getId()))
                        .map(student -> new VoteParticipationResponse(
                                student.getId(),
                                student.getParticipantNumber(),
                                student.getNickname()
                        ))
                        .toList()
                : List.of();
        return new VoteSessionResponse(
                session.getId(), session.getRoomId(), session.getQuestion(), session.getFundBalanceSnapshot(),
                session.getStatus(), eligibleStudents.size(), votes.size(),
                studentId == null || eligibleStudentIds.contains(studentId), selected != null,
                selected, reveal ? session.getDecidedOptionId() : null,
                session.getOpenedAt(), session.getClosedAt(),
                pendingStudents, responseOptions
        );
    }

    private boolean isVoteResultVisible(RoomEntity room) {
        return List.of(
                "DAY2_FUND_RESULT", "DAY2_FINAL_RESULT", "DAY2_SURVEY", "DAY2_COMPLETED"
        ).contains(room.getCurrentStage());
    }

    private boolean hasPendingVoters(VoteSessionEntity session) {
        Set<UUID> submittedStudentIds = voteRepository.findAllByVoteSessionId(session.getId())
                .stream()
                .map(VoteEntity::getStudentId)
                .collect(Collectors.toSet());
        return eligibleStudents(session).stream()
                .anyMatch(student -> !submittedStudentIds.contains(student.getId()));
    }

    private List<UUID> leadingOptionIds(UUID sessionId, UUID roomId) {
        List<VoteOptionEntity> options = voteOptionRepository.findAllByVoteSessionIdOrderByDisplayOrder(sessionId);
        VoteSessionEntity session = voteSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> conflict("VOTE_NOT_FOUND", "투표를 찾을 수 없습니다."));
        Set<UUID> eligibleStudentIds = eligibleStudents(session).stream()
                .map(StudentEntity::getId)
                .collect(Collectors.toSet());
        Map<UUID, Long> counts = voteRepository.findAllByVoteSessionId(sessionId).stream()
                .filter(vote -> eligibleStudentIds.contains(vote.getStudentId()))
                .collect(Collectors.groupingBy(VoteEntity::getOptionId, Collectors.counting()));
        long max = options.stream().mapToLong(option -> counts.getOrDefault(option.getId(), 0L)).max().orElse(0L);
        return options.stream()
                .filter(option -> counts.getOrDefault(option.getId(), 0L) == max)
                .map(VoteOptionEntity::getId)
                .toList();
    }

    private List<FundCostOptionEntity> ensureCostOptions(UUID roomId) {
        List<FundCostOptionEntity> options = costOptionRepository.findAllByRoomIdOrderByDisplayOrder(roomId);
        if (!options.isEmpty()) return options;
        return costOptionRepository.saveAll(List.of(
                FundCostOptionEntity.create(roomId, "에어컨 사용료", 50, 1),
                FundCostOptionEntity.create(roomId, "보드마카 구입", 30, 2),
                FundCostOptionEntity.create(roomId, "학급 공동활동", 100, 3)
        ));
    }

    private VoteSessionEntity lockedVote(UUID roomId, UUID voteId) {
        return voteSessionRepository.findByIdForUpdate(voteId)
                .filter(item -> item.getRoomId().equals(roomId))
                .orElseThrow(() -> conflict("VOTE_NOT_FOUND", "투표를 찾을 수 없습니다."));
    }

    private List<StudentEntity> activeStudents(UUID roomId) {
        return studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE");
    }

    private List<StudentEntity> eligibleStudents(VoteSessionEntity session) {
        List<UUID> ids = voteEligibleStudentRepository
                .findAllByVoteSessionId(session.getId()).stream()
                .map(VoteEligibleStudentEntity::getStudentId)
                .toList();
        if (ids.isEmpty()) return activeStudents(session.getRoomId());
        return studentRepository.findAllById(ids).stream()
                .sorted(Comparator.comparingInt(StudentEntity::getParticipantNumber))
                .toList();
    }

    private boolean isEligible(VoteSessionEntity session, UUID studentId) {
        if (voteEligibleStudentRepository.countByVoteSessionId(session.getId()) == 0) {
            return activeStudents(session.getRoomId()).stream()
                    .anyMatch(student -> student.getId().equals(studentId));
        }
        return voteEligibleStudentRepository
                .existsByVoteSessionIdAndStudentId(session.getId(), studentId);
    }

    private StudentEntity activeStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."
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

    private void ensureFundVisible(RoomEntity room) {
        if (room.getCurrentDay() != 2 || List.of(
                "DAY2_LOGIN", "DAY2_SETTLEMENT_WAIT", "DAY2_FINANCE_SETTLEMENT",
                "DAY2_RESULT_GUIDED", "DAY2_QUIZ", "DAY2_QUIZ_SETTLEMENT",
                "DAY2_TAX_INFLATION_GUIDED", "DAY2_INFLATION_APPLY", "DAY2_FINAL_SHOP"
        ).contains(room.getCurrentStage())) {
            throw conflict("CLASS_FUND_NOT_OPEN", "공동기금 단계부터 잔액을 확인할 수 있습니다.");
        }
    }

    private void ensureVoteVisible(RoomEntity room) {
        if (!List.of(
                "DAY2_FUND_VOTE", "DAY2_FUND_RESULT", "DAY2_FINAL_RESULT",
                "DAY2_SURVEY", "DAY2_COMPLETED"
        ).contains(room.getCurrentStage())) {
            throw conflict("VOTE_NOT_OPEN", "공동기금 투표 단계부터 투표를 확인할 수 있습니다.");
        }
    }

    private void ensureRoomWritable(RoomEntity room) {
        if ("COMPLETED".equals(room.getStatus()) || "DAY2_COMPLETED".equals(room.getCurrentStage())) {
            throw conflict("ROOM_COMPLETED", "종료된 수업에서는 상태를 변경할 수 없습니다.");
        }
    }

    private DaySessionEntity dayTwoSession(UUID roomId) {
        return sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2)
                .orElseThrow(() -> conflict("SESSION_NOT_FOUND", "2일차 세션을 찾을 수 없습니다."));
    }

    private long currentFundBalance(UUID roomId) {
        return accountRepository.findByRoomIdAndStudentIdIsNullAndAccountType(roomId, "CLASS_FUND")
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
        }
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
