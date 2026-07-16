package com.b201.bibim.feature.settlement;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryEntity;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionEntity;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.settlement.SettlementItemEntity;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunEntity;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.settlement.Day1IncomeCalculator.Calculation;
import com.b201.bibim.feature.settlement.SettlementDto.Day1IncomePreviewResponse;
import com.b201.bibim.feature.settlement.SettlementDto.Day1IncomeSettlementResponse;
import com.b201.bibim.feature.settlement.SettlementDto.Day1PayslipResponse;
import com.b201.bibim.feature.settlement.SettlementDto.StudentIncomeResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private static final String DAY1_INCOME = "DAY1_INCOME";
    private static final String COMPLETED = "COMPLETED";

    private final RoomRepository roomRepository;
    private final RoomRulesRepository roomRulesRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;
    private final Day1IncomeCalculator calculator;

    public SettlementService(
            RoomRepository roomRepository,
            RoomRulesRepository roomRulesRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            QuizRepository quizRepository,
            QuizSubmissionRepository submissionRepository,
            SettlementRunRepository settlementRunRepository,
            SettlementItemRepository settlementItemRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository,
            Day1IncomeCalculator calculator
    ) {
        this.roomRepository = roomRepository;
        this.roomRulesRepository = roomRulesRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.quizRepository = quizRepository;
        this.submissionRepository = submissionRepository;
        this.settlementRunRepository = settlementRunRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public Day1IncomePreviewResponse previewDay1Income(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensureSettlementStage(room);
        return settlementRunRepository.findByRoomIdAndSettlementType(roomId, DAY1_INCOME)
                .filter(run -> COMPLETED.equals(run.getStatus()))
                .map(run -> previewFromCompletedRun(room, run))
                .orElseGet(() -> previewFromCurrentData(room));
    }

    @Transactional
    public Day1IncomeSettlementResponse settleDay1Income(
            UUID teacherId,
            UUID roomId,
            String idempotencyKey
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        RoomEntity room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        ensureSettlementStage(room);

        var existingRun = settlementRunRepository
                .findByRoomIdAndSettlementType(roomId, DAY1_INCOME);
        if (existingRun.isPresent()) {
            SettlementRunEntity run = existingRun.get();
            if (COMPLETED.equals(run.getStatus())) {
                return new Day1IncomeSettlementResponse(
                        previewFromCompletedRun(room, run),
                        true
                );
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_IN_PROGRESS",
                    "소득 정산이 이미 진행 중입니다. 잠시 후 다시 확인해 주세요."
            );
        }

        settlementRunRepository.findByIdempotencyKey(normalizedKey).ifPresent(run -> {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "같은 Idempotency-Key를 다른 정산에 사용할 수 없습니다."
            );
        });

        List<CalculatedStudent> calculatedStudents = calculateCurrentStudents(room);
        if (calculatedStudents.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_VALIDATION_FAILED",
                    "정산할 활성 학생이 없습니다."
            );
        }

        DaySessionEntity session = sessionRepository
                .findByRoomIdAndDayNumber(roomId, (short) 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SETTLEMENT_VALIDATION_FAILED",
                        "1일차 수업 세션을 찾을 수 없습니다."
                ));
        SettlementRunEntity run = settlementRunRepository.saveAndFlush(
                SettlementRunEntity.processing(
                        roomId,
                        session.getId(),
                        teacherId,
                        normalizedKey,
                        calculatedStudents.size()
                )
        );

        AccountEntity classFund = getOrCreateRoomAccount(roomId, "CLASS_FUND");
        AccountEntity systemTreasury = getOrCreateRoomAccount(roomId, "SYSTEM_TREASURY");
        long totalGrossIncome = 0;
        long totalIncomeTax = 0;
        long totalNetIncome = 0;

        for (CalculatedStudent calculated : calculatedStudents) {
            StudentEntity student = calculated.student();
            Calculation amount = calculated.amount();
            AccountEntity wallet = getOrCreateStudentWallet(roomId, student.getId());
            settlementItemRepository.save(toSettlementItem(run.getId(), calculated));

            transfer(
                    room,
                    session,
                    teacherId,
                    run,
                    student,
                    systemTreasury,
                    wallet,
                    amount.roleIncome(),
                    "ROLE_INCOME",
                    normalizedKey + ":" + student.getId() + ":role",
                    "역할 소득 지급"
            );
            transfer(
                    room,
                    session,
                    teacherId,
                    run,
                    student,
                    systemTreasury,
                    wallet,
                    amount.participationReward(),
                    "QUIZ_PARTICIPATION_REWARD",
                    normalizedKey + ":" + student.getId() + ":participation",
                    "퀴즈 참여수당 지급"
            );
            transfer(
                    room,
                    session,
                    teacherId,
                    run,
                    student,
                    systemTreasury,
                    wallet,
                    amount.correctReward(),
                    "QUIZ_CORRECT_REWARD",
                    normalizedKey + ":" + student.getId() + ":correct",
                    "퀴즈 정답수당 지급"
            );
            transfer(
                    room,
                    session,
                    teacherId,
                    run,
                    student,
                    wallet,
                    classFund,
                    amount.incomeTax(),
                    "INCOME_TAX",
                    normalizedKey + ":" + student.getId() + ":tax",
                    "1일차 소득세 납부"
            );

            totalGrossIncome = Math.addExact(totalGrossIncome, amount.grossIncome());
            totalIncomeTax = Math.addExact(totalIncomeTax, amount.incomeTax());
            totalNetIncome = Math.addExact(totalNetIncome, amount.netIncome());
        }

        run.complete(totalGrossIncome, totalIncomeTax, totalNetIncome);
        room.markEconomicStateChanged();

        return new Day1IncomeSettlementResponse(
                previewFromCompletedRun(room, run),
                false
        );
    }

    @Transactional(readOnly = true)
    public Day1PayslipResponse day1Payslip(UUID studentId) {
        StudentEntity student = studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(this::authenticationRequired);
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        if (!"DAY1_PAYSLIP_GUIDED".equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYSLIP_NOT_OPEN",
                    "현재는 월급명세서를 확인하는 단계가 아닙니다."
            );
        }
        SettlementRunEntity run = settlementRunRepository
                .findByRoomIdAndSettlementType(room.getId(), DAY1_INCOME)
                .filter(item -> COMPLETED.equals(item.getStatus()))
                .orElseThrow(this::payslipNotReady);
        SettlementItemEntity item = settlementItemRepository
                .findBySettlementRunIdAndStudentId(run.getId(), studentId)
                .orElseThrow(this::payslipNotReady);
        long walletBalance = walletBalance(room.getId(), studentId);

        return new Day1PayslipResponse(
                run.getId(),
                student.getId(),
                student.getParticipantNumber(),
                student.getNickname(),
                item.isQuizSubmitted(),
                item.getCorrectCount(),
                item.getRoleIncome(),
                item.getParticipationReward(),
                item.getCorrectReward(),
                item.getGrossIncome(),
                item.getIncomeTax(),
                item.getNetIncome(),
                walletBalance,
                run.getCompletedAt()
        );
    }

    private Day1IncomePreviewResponse previewFromCurrentData(RoomEntity room) {
        RoomRulesEntity rules = findRules(room.getId());
        List<StudentIncomeResponse> students = calculateCurrentStudents(room).stream()
                .map(this::toResponse)
                .toList();
        return new Day1IncomePreviewResponse(
                room.getId(),
                "NOT_STARTED",
                rules.getIncomeTaxBps(),
                students.size(),
                sum(students, StudentIncomeResponse::grossIncome),
                sum(students, StudentIncomeResponse::incomeTax),
                sum(students, StudentIncomeResponse::netIncome),
                classFundBalance(room.getId()),
                null,
                students
        );
    }

    private Day1IncomePreviewResponse previewFromCompletedRun(
            RoomEntity room,
            SettlementRunEntity run
    ) {
        RoomRulesEntity rules = findRules(room.getId());
        Map<UUID, StudentEntity> studentsById = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(room.getId())
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        List<StudentIncomeResponse> students = settlementItemRepository
                .findAllBySettlementRunId(run.getId())
                .stream()
                .map(item -> toResponse(item, studentsById.get(item.getStudentId())))
                .sorted(Comparator.comparingInt(StudentIncomeResponse::participantNumber))
                .toList();

        return new Day1IncomePreviewResponse(
                room.getId(),
                run.getStatus(),
                rules.getIncomeTaxBps(),
                students.size(),
                sum(students, StudentIncomeResponse::grossIncome),
                sum(students, StudentIncomeResponse::incomeTax),
                sum(students, StudentIncomeResponse::netIncome),
                classFundBalance(room.getId()),
                run.getCompletedAt(),
                students
        );
    }

    private List<CalculatedStudent> calculateCurrentStudents(RoomEntity room) {
        RoomRulesEntity rules = findRules(room.getId());
        QuizEntity quiz = quizRepository.findByRoomIdAndDayNumber(room.getId(), (short) 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SETTLEMENT_VALIDATION_FAILED",
                        "1일차 퀴즈를 찾을 수 없습니다."
                ));
        Map<UUID, QuizSubmissionEntity> submissions = submissionRepository
                .findAllByQuizId(quiz.getId())
                .stream()
                .collect(Collectors.toMap(
                        QuizSubmissionEntity::getStudentId,
                        Function.identity()
                ));

        return studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(room.getId(), "ACTIVE")
                .stream()
                .map(student -> {
                    QuizSubmissionEntity submission = submissions.get(student.getId());
                    boolean submitted = submission != null && submission.isSubmitted();
                    int correctCount = submitted ? submission.getCorrectCount() : 0;
                    Calculation amount = calculator.calculate(
                            rules.getRoleIncome(),
                            rules.getDay1ParticipationReward(),
                            rules.getDay1CorrectReward(),
                            rules.getIncomeTaxBps(),
                            submitted,
                            correctCount
                    );
                    return new CalculatedStudent(student, submitted, correctCount, amount);
                })
                .toList();
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
            String idempotencyKey,
            String description
    ) {
        if (amount == 0) {
            return;
        }
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.success(
                        room.getId(),
                        session.getId(),
                        student.getId(),
                        teacherId,
                        run.getId(),
                        transactionType,
                        idempotencyKey,
                        description,
                        Map.of("amount", amount, "dayNumber", 1)
                )
        );
        long fromBefore = from.getBalance();
        long toBefore = to.getBalance();
        from.changeBy(-amount);
        to.changeBy(amount);
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(
                        transaction.getId(),
                        from,
                        -amount,
                        fromBefore,
                        1
                ),
                FinancialEntryEntity.create(
                        transaction.getId(),
                        to,
                        amount,
                        toBefore,
                        2
                )
        ));
    }

    private AccountEntity getOrCreateStudentWallet(UUID roomId, UUID studentId) {
        return accountRepository
                .findStudentAccountForUpdate(roomId, studentId, "STUDENT_WALLET")
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.studentWallet(roomId, studentId)
                ));
    }

    private AccountEntity getOrCreateRoomAccount(UUID roomId, String accountType) {
        return accountRepository.findRoomAccountForUpdate(roomId, accountType)
                .orElseGet(() -> accountRepository.saveAndFlush(
                        "CLASS_FUND".equals(accountType)
                                ? AccountEntity.classFund(roomId)
                                : AccountEntity.systemTreasury(roomId)
                ));
    }

    private long classFundBalance(UUID roomId) {
        return accountRepository
                .findByRoomIdAndStudentIdIsNullAndAccountType(roomId, "CLASS_FUND")
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository
                .findByRoomIdAndStudentIdAndAccountType(
                        roomId,
                        studentId,
                        "STUDENT_WALLET"
                )
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private SettlementItemEntity toSettlementItem(
            UUID runId,
            CalculatedStudent calculated
    ) {
        Calculation amount = calculated.amount();
        return SettlementItemEntity.create(
                runId,
                calculated.student().getId(),
                calculated.quizSubmitted(),
                calculated.correctCount(),
                amount.roleIncome(),
                amount.participationReward(),
                amount.correctReward(),
                amount.grossIncome(),
                amount.incomeTax(),
                amount.netIncome()
        );
    }

    private StudentIncomeResponse toResponse(CalculatedStudent calculated) {
        StudentEntity student = calculated.student();
        Calculation amount = calculated.amount();
        return new StudentIncomeResponse(
                student.getId(),
                student.getParticipantNumber(),
                student.getNickname(),
                calculated.quizSubmitted(),
                calculated.correctCount(),
                amount.roleIncome(),
                amount.participationReward(),
                amount.correctReward(),
                amount.grossIncome(),
                amount.incomeTax(),
                amount.netIncome()
        );
    }

    private StudentIncomeResponse toResponse(
            SettlementItemEntity item,
            StudentEntity student
    ) {
        if (student == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_VALIDATION_FAILED",
                    "정산 대상 학생 정보를 찾을 수 없습니다."
            );
        }
        return new StudentIncomeResponse(
                student.getId(),
                student.getParticipantNumber(),
                student.getNickname(),
                item.isQuizSubmitted(),
                item.getCorrectCount(),
                item.getRoleIncome(),
                item.getParticipationReward(),
                item.getCorrectReward(),
                item.getGrossIncome(),
                item.getIncomeTax(),
                item.getNetIncome()
        );
    }

    private long sum(
            List<StudentIncomeResponse> students,
            java.util.function.ToLongFunction<StudentIncomeResponse> extractor
    ) {
        return students.stream().mapToLong(extractor).sum();
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        return room;
    }

    private void ensureOwner(RoomEntity room, UUID teacherId) {
        if (!room.getTeacherId().equals(teacherId)) {
            throw roomNotFound();
        }
    }

    private void ensureSettlementStage(RoomEntity room) {
        if (!"DAY1_INCOME_SETTLEMENT".equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_NOT_OPEN",
                    "현재는 1일차 소득을 정산하는 단계가 아닙니다."
            );
        }
    }

    private RoomRulesEntity findRules(UUID roomId) {
        return roomRulesRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SETTLEMENT_VALIDATION_FAILED",
                        "수업 경제 규칙을 찾을 수 없습니다."
                ));
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key 헤더가 필요합니다."
            );
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key는 100자 이하여야 합니다."
            );
        }
        return normalized;
    }

    private ApiException authenticationRequired() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "로그인이 필요합니다."
        );
    }

    private ApiException payslipNotReady() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PAYSLIP_NOT_READY",
                "소득 정산이 완료되지 않아 월급명세서를 만들 수 없습니다."
        );
    }

    private ApiException roomNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "수업 방을 찾을 수 없습니다."
        );
    }

    private record CalculatedStudent(
            StudentEntity student,
            boolean quizSubmitted,
            int correctCount,
            Calculation amount
    ) {
    }
}
