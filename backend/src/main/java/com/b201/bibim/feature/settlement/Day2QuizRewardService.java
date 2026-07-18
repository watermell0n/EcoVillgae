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
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity.QuizOption;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
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
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.settlement.Day2QuizRewardDto.SettlementResponse;
import com.b201.bibim.feature.settlement.Day2QuizRewardDto.StudentRewardResponse;
import com.b201.bibim.feature.settlement.Day2QuizRewardDto.TeacherRewardPreviewResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Day2QuizRewardService {

    private static final String SETTLEMENT_TYPE = "DAY2_QUIZ_REWARD";
    private static final List<QuestionDefinition> QUESTIONS = List.of(
            question(
                    "내가 일해서 받는 돈을 무엇이라고 할까요?",
                    "세금", "월급", "이자", "투자", "B",
                    "일을 한 대가로 받는 돈을 월급이라고 합니다."
            ),
            question(
                    "국가나 지역을 위해 내는 돈을 무엇이라고 할까요?",
                    "월급", "이자", "세금", "저축", "C",
                    "세금은 국가나 지역 사회의 공공서비스와 공동체 운영을 위해 내는 돈입니다."
            ),
            question(
                    "세금을 내고 실제로 받게 되는 금액을 무엇이라고 할까요?",
                    "총소득", "실수령액", "공동기금", "이자", "B",
                    "총소득에서 세금처럼 빠져나가는 금액을 뺀 뒤 실제로 받는 돈이 실수령액입니다."
            ),
            question(
                    "돈을 은행에 맡겨 나중에 사용하도록 모아 두는 것을 무엇이라고 할까요?",
                    "소비", "세금", "투자", "저축", "D",
                    "저축은 지금 쓰지 않은 돈을 은행 등에 맡겨 미래에 사용하도록 모으는 것입니다."
            ),
            question(
                    "은행에 돈을 맡기면 돌려받는 추가 금액을 무엇이라고 할까요?",
                    "세금", "월급", "인플레이션", "이자", "D",
                    "은행에 돈을 맡기면 약속된 조건에 따라 원금에 더해 이자를 받을 수 있습니다."
            ),
            question(
                    "미래에 더 많은 돈을 얻기 위해 돈을 사용하지만, 손해를 볼 수도 있는 활동은 무엇이라고 할까요?",
                    "투자", "저축", "세금", "월급", "A",
                    "투자는 더 큰 수익을 기대할 수 있지만 원금 손실의 위험도 있는 활동입니다."
            ),
            question(
                    "필요한 물건이나 서비스를 사기 위해 돈을 사용하는 것을 무엇이라고 할까요?",
                    "저축", "투자", "소비", "이자", "C",
                    "필요한 물건이나 서비스를 사기 위해 돈을 사용하는 활동을 소비라고 합니다."
            ),
            question(
                    "분산투자를 하는 가장 큰 이유는 무엇이라고 할까요?",
                    "더 많이 벌기 위해", "위험을 나누기 위해", "세금을 줄이기 위해", "빨리 쓰기 위해", "B",
                    "분산투자는 돈을 여러 곳에 나누어 투자해 한 곳의 손실이 전체에 미치는 위험을 줄이는 방법입니다."
            ),
            question(
                    "돈의 가치가 떨어져서 물건 가격이 오르는 현상을 무엇이라고 할까요?",
                    "디플레이션", "인플레이션", "이자율", "세금", "B",
                    "인플레이션은 전반적인 물가가 올라 같은 돈으로 살 수 있는 양이 줄어드는 현상입니다."
            ),
            question(
                    "우리 반에서 모은 세금을 가장 알맞게 사용하는 방법은 무엇일까요?",
                    "한 학생이 원하는 장난감을 산다.",
                    "선생님이 개인적으로 사용한다.",
                    "반 전체가 사용할 공용 물품을 산다.",
                    "세금을 가장 많이 낸 학생에게 모두 준다.",
                    "C",
                    "세금은 특정 개인이 아니라 공동체 구성원 모두에게 필요한 공용 물품이나 서비스에 사용하는 것이 알맞습니다."
            )
    );

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final RoomRepository roomRepository;
    private final RoomRulesRepository rulesRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final StudentFlowStateRepository flowStateRepository;
    private final SettlementRunRepository runRepository;
    private final SettlementItemRepository itemRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;

    public Day2QuizRewardService(
            QuizRepository quizRepository,
            QuizQuestionRepository questionRepository,
            QuizSubmissionRepository submissionRepository,
            RoomRepository roomRepository,
            RoomRulesRepository rulesRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            StudentFlowStateRepository flowStateRepository,
            SettlementRunRepository runRepository,
            SettlementItemRepository itemRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository
    ) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.roomRepository = roomRepository;
        this.rulesRepository = rulesRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.flowStateRepository = flowStateRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public void ensureQuiz(UUID roomId) {
        if (quizRepository.findByRoomIdAndDayNumber(roomId, (short) 2).isPresent()) return;
        QuizEntity quiz = quizRepository.saveAndFlush(QuizEntity.dayTwoReady(roomId, QUESTIONS.size()));
        questionRepository.saveAll(java.util.stream.IntStream.range(0, QUESTIONS.size())
                .mapToObj(index -> QUESTIONS.get(index).toEntity(quiz.getId(), index + 1))
                .toList());
    }

    @Transactional(readOnly = true)
    public TeacherRewardPreviewResponse preview(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensurePreviewStage(room);
        return runRepository.findByRoomIdAndSettlementType(roomId, SETTLEMENT_TYPE)
                .filter(run -> "COMPLETED".equals(run.getStatus()))
                .map(run -> completedPreview(room, run))
                .orElseGet(() -> currentPreview(room));
    }

    @Transactional(readOnly = true)
    public StudentRewardResponse studentResult(UUID studentId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureStudentPreviewStage(room, studentId);
        var completed = runRepository.findByRoomIdAndSettlementType(room.getId(), SETTLEMENT_TYPE)
                .filter(run -> "COMPLETED".equals(run.getStatus()));
        if (completed.isPresent()) {
            SettlementItemEntity item = itemRepository
                    .findBySettlementRunIdAndStudentId(completed.get().getId(), studentId)
                    .orElseThrow(this::rewardNotReady);
            return storedStudent(room, student, item);
        }
        return calculateStudent(room, rules(room.getId()), quiz(room.getId()), student);
    }

    @Transactional
    public SettlementResponse settle(UUID teacherId, UUID roomId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        if (!"DAY2_QUIZ_SETTLEMENT".equals(room.getCurrentStage())) {
            throw conflict("DAY2_QUIZ_REWARD_NOT_OPEN", "경제 퀴즈 보상 정산 단계에서만 실행할 수 있습니다.");
        }
        var existing = runRepository.findByRoomIdAndSettlementType(roomId, SETTLEMENT_TYPE);
        if (existing.isPresent()) {
            if ("COMPLETED".equals(existing.get().getStatus())) {
                return new SettlementResponse(completedPreview(room, existing.get()), true);
            }
            throw conflict("SETTLEMENT_IN_PROGRESS", "경제 퀴즈 보상 정산이 진행 중입니다.");
        }
        runRepository.findByIdempotencyKey(key).ifPresent(run -> {
            throw conflict("IDEMPOTENCY_KEY_REUSED", "같은 요청 키를 다른 정산에 사용할 수 없습니다.");
        });

        RoomRulesEntity rules = rules(roomId);
        QuizEntity quiz = quiz(roomId);
        if (!"CLOSED".equals(quiz.getStatus())) {
            throw conflict("QUIZ_NOT_CLOSED", "퀴즈를 마감한 뒤 보상을 정산해 주세요.");
        }
        List<StudentEntity> students = activeStudents(roomId).stream()
                .filter(student -> flowStateRepository.findByStudentId(student.getId())
                        .map(state -> !"CATCH_UP".equals(state.getFlowMode())
                                || submissionRepository
                                .findByQuizIdAndStudentId(quiz.getId(), student.getId())
                                .map(QuizSubmissionEntity::isSubmitted)
                                .orElse(false))
                        .orElse(true))
                .toList();
        DaySessionEntity session = sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2)
                .orElseThrow(() -> conflict("SESSION_NOT_FOUND", "2일차 세션을 찾을 수 없습니다."));
        SettlementRunEntity run = runRepository.saveAndFlush(SettlementRunEntity.processing(
                roomId, session.getId(), teacherId, key, students.size(), SETTLEMENT_TYPE
        ));
        AccountEntity treasury = accountRepository.findRoomAccountForUpdate(roomId, "SYSTEM_TREASURY")
                .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.systemTreasury(roomId)));
        int submittedCount = 0;
        long totalReward = 0;
        for (StudentEntity student : students) {
            StudentRewardResponse calculated = calculateStudent(room, rules, quiz, student);
            int rewardRateBps = flowStateRepository.findByStudentId(student.getId())
                    .filter(state -> !"LIVE".equals(state.getFlowMode()))
                    .map(state -> state.getRewardRateBps())
                    .orElse(10_000);
            AccountEntity wallet = accountRepository.findStudentAccountForUpdate(
                            roomId, student.getId(), "STUDENT_WALLET"
                    )
                    .orElseGet(() -> accountRepository.saveAndFlush(
                            AccountEntity.studentWallet(roomId, student.getId())
                    ));
            itemRepository.save(SettlementItemEntity.create(
                    run.getId(), student.getId(), calculated.submitted(), calculated.correctCount(),
                    0, calculated.participationReward(), calculated.correctReward(),
                    calculated.totalReward(), 0, calculated.totalReward(),
                    rewardRateBps < 10_000 ? "LATE" : "LIVE", rewardRateBps
            ));
            transferReward(room, session, teacherId, run, student, treasury, wallet, calculated, key);
            if (calculated.submitted()) submittedCount += 1;
            totalReward = Math.addExact(totalReward, calculated.totalReward());
        }
        run.completeQuizReward(totalReward, submittedCount);
        room.markEconomicStateChanged();
        return new SettlementResponse(completedPreview(room, run), false);
    }

    @Transactional
    public boolean settleCatchUpStudent(UUID studentId, int rewardRateBps) {
        StudentEntity student = studentRepository.findByIdForUpdate(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."
                ));
        RoomEntity room = roomRepository.findByIdForUpdate(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        SettlementRunEntity run = runRepository
                .findByRoomIdAndSettlementType(room.getId(), SETTLEMENT_TYPE)
                .filter(item -> "COMPLETED".equals(item.getStatus()))
                .orElse(null);
        if (run == null) return false;
        if (itemRepository.findBySettlementRunIdAndStudentId(run.getId(), studentId).isPresent()) {
            return true;
        }

        RoomRulesEntity rules = rules(room.getId());
        QuizEntity quiz = quiz(room.getId());
        StudentRewardResponse reward = calculateStudent(
                room, rules, quiz, student, rewardRateBps
        );
        if (!reward.submitted()) {
            throw conflict("QUIZ_SUBMISSION_REQUIRED", "보충 퀴즈를 제출한 뒤 정산할 수 있습니다.");
        }
        itemRepository.save(SettlementItemEntity.create(
                run.getId(), studentId, true, reward.correctCount(),
                0, reward.participationReward(), reward.correctReward(),
                reward.totalReward(), 0, reward.totalReward(), "LATE", rewardRateBps
        ));
        DaySessionEntity session = sessionRepository
                .findByRoomIdAndDayNumber(room.getId(), (short) 2)
                .orElseThrow(() -> conflict("SESSION_NOT_FOUND", "2일차 세션을 찾을 수 없습니다."));
        AccountEntity treasury = accountRepository
                .findRoomAccountForUpdate(room.getId(), "SYSTEM_TREASURY")
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.systemTreasury(room.getId())
                ));
        AccountEntity wallet = accountRepository.findStudentAccountForUpdate(
                        room.getId(), studentId, "STUDENT_WALLET"
                )
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.studentWallet(room.getId(), studentId)
                ));
        transferReward(
                room, session, room.getTeacherId(), run, student, treasury, wallet,
                reward, "catchup:day2-quiz:" + studentId
        );
        room.markEconomicStateChanged();
        return true;
    }

    private void transferReward(
            RoomEntity room,
            DaySessionEntity session,
            UUID teacherId,
            SettlementRunEntity run,
            StudentEntity student,
            AccountEntity treasury,
            AccountEntity wallet,
            StudentRewardResponse reward,
            String key
    ) {
        if (reward.totalReward() == 0) return;
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.success(
                        room.getId(), session.getId(), student.getId(), teacherId, run.getId(),
                        SETTLEMENT_TYPE, key + ":" + student.getId(), "경제 퀴즈 보상 지급",
                        Map.of(
                                "participationReward", reward.participationReward(),
                                "correctReward", reward.correctReward(),
                                "correctCount", reward.correctCount()
                        )
                )
        );
        long treasuryBefore = treasury.getBalance();
        long walletBefore = wallet.getBalance();
        treasury.changeBy(-reward.totalReward());
        wallet.changeBy(reward.totalReward());
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(transaction.getId(), treasury, -reward.totalReward(), treasuryBefore, 1),
                FinancialEntryEntity.create(transaction.getId(), wallet, reward.totalReward(), walletBefore, 2)
        ));
    }

    private TeacherRewardPreviewResponse currentPreview(RoomEntity room) {
        RoomRulesEntity rules = rules(room.getId());
        QuizEntity quiz = quiz(room.getId());
        List<StudentRewardResponse> students = activeStudents(room.getId()).stream()
                .map(student -> calculateStudent(room, rules, quiz, student))
                .toList();
        return previewResponse(room, rules, "NOT_STARTED", null, students);
    }

    private TeacherRewardPreviewResponse completedPreview(RoomEntity room, SettlementRunEntity run) {
        RoomRulesEntity rules = rules(room.getId());
        Map<UUID, StudentEntity> studentsById = activeStudents(room.getId()).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        List<StudentRewardResponse> students = itemRepository.findAllBySettlementRunId(run.getId()).stream()
                .map(item -> storedStudent(room, studentsById.get(item.getStudentId()), item))
                .sorted(Comparator.comparingInt(StudentRewardResponse::participantNumber))
                .toList();
        return previewResponse(room, rules, "COMPLETED", run.getCompletedAt(), students);
    }

    private TeacherRewardPreviewResponse previewResponse(
            RoomEntity room,
            RoomRulesEntity rules,
            String status,
            java.time.Instant completedAt,
            List<StudentRewardResponse> students
    ) {
        int submitted = (int) students.stream().filter(StudentRewardResponse::submitted).count();
        long total = students.stream().mapToLong(StudentRewardResponse::totalReward).sum();
        return new TeacherRewardPreviewResponse(
                room.getId(), status, rules.getDay2ParticipationReward(), rules.getDay2CorrectReward(),
                students.size(), submitted, students.size() - submitted, total, completedAt, students
        );
    }

    private StudentRewardResponse calculateStudent(
            RoomEntity room,
            RoomRulesEntity rules,
            QuizEntity quiz,
            StudentEntity student
    ) {
        int rewardRateBps = flowStateRepository.findByStudentId(student.getId())
                .filter(state -> !"LIVE".equals(state.getFlowMode()))
                .map(state -> state.getRewardRateBps())
                .orElse(10_000);
        return calculateStudent(room, rules, quiz, student, rewardRateBps);
    }

    private StudentRewardResponse calculateStudent(
            RoomEntity room,
            RoomRulesEntity rules,
            QuizEntity quiz,
            StudentEntity student,
            int rewardRateBps
    ) {
        QuizSubmissionEntity submission = submissionRepository
                .findByQuizIdAndStudentId(quiz.getId(), student.getId())
                .filter(QuizSubmissionEntity::isSubmitted)
                .orElse(null);
        boolean submitted = submission != null;
        int correct = submitted ? submission.getCorrectCount() : 0;
        long participation = submitted ? rules.getDay2ParticipationReward() : 0;
        long rawCorrectReward = Math.multiplyExact(correct, rules.getDay2CorrectReward());
        long correctReward = Math.floorDiv(
                Math.multiplyExact(rawCorrectReward, rewardRateBps), 10_000L
        );
        long total = Math.addExact(participation, correctReward);
        return new StudentRewardResponse(
                student.getId(), student.getParticipantNumber(), student.getNickname(), submitted,
                correct, participation, correctReward, total,
                Math.addExact(walletBalance(room.getId(), student.getId()), total)
        );
    }

    private StudentRewardResponse storedStudent(
            RoomEntity room,
            StudentEntity student,
            SettlementItemEntity item
    ) {
        if (student == null) throw rewardNotReady();
        return new StudentRewardResponse(
                student.getId(), student.getParticipantNumber(), student.getNickname(),
                item.isQuizSubmitted(), item.getCorrectCount(), item.getParticipationReward(),
                item.getCorrectReward(), item.getNetIncome(), walletBalance(room.getId(), student.getId())
        );
    }

    private List<StudentEntity> activeStudents(UUID roomId) {
        return studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE");
    }

    private StudentEntity activeStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
    }

    private RoomRulesEntity rules(UUID roomId) {
        return rulesRepository.findByRoomId(roomId)
                .orElseThrow(() -> conflict("ROOM_RULES_NOT_FOUND", "수업 규칙을 찾을 수 없습니다."));
    }

    private QuizEntity quiz(UUID roomId) {
        return quizRepository.findByRoomIdAndDayNumber(roomId, (short) 2)
                .orElseThrow(() -> conflict("QUIZ_NOT_FOUND", "경제 퀴즈를 찾을 수 없습니다."));
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(roomId, studentId, "STUDENT_WALLET")
                .map(AccountEntity::getBalance)
                .orElse(0L);
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
        if (room.getCurrentDay() != 2 || !List.of(
                "DAY2_QUIZ", "DAY2_QUIZ_SETTLEMENT", "DAY2_TAX_INFLATION_GUIDED",
                "DAY2_INFLATION_APPLY", "DAY2_FINAL_SHOP"
        ).contains(room.getCurrentStage())) {
            throw conflict("DAY2_QUIZ_REWARD_NOT_OPEN", "경제 퀴즈 이후에 보상 결과를 확인할 수 있습니다.");
        }
    }

    private void ensureStudentPreviewStage(RoomEntity room, UUID studentId) {
        boolean catchUpResult = flowStateRepository.findByStudentId(studentId)
                .filter(state -> "CATCH_UP".equals(state.getFlowMode()))
                .map(state -> "DAY2_QUIZ_SETTLEMENT".equals(state.getEffectiveStage()))
                .orElse(false);
        if (!catchUpResult) ensurePreviewStage(room);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "100자 이하의 Idempotency-Key가 필요합니다.");
        }
        return key.trim();
    }

    private ApiException rewardNotReady() {
        return conflict("DAY2_QUIZ_REWARD_NOT_READY", "경제 퀴즈 보상 결과를 찾을 수 없습니다.");
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static QuestionDefinition question(
            String prompt,
            String a,
            String b,
            String c,
            String d,
            String answer,
            String explanation
    ) {
        return new QuestionDefinition(prompt, List.of(
                new QuizOption("A", a), new QuizOption("B", b),
                new QuizOption("C", c), new QuizOption("D", d)
        ), answer, explanation);
    }

    private record QuestionDefinition(
            String prompt,
            List<QuizOption> options,
            String answer,
            String explanation
    ) {
        QuizQuestionEntity toEntity(UUID quizId, int orderNo) {
            return QuizQuestionEntity.multipleChoice(
                    quizId, orderNo, prompt, options, answer, explanation
            );
        }
    }
}
