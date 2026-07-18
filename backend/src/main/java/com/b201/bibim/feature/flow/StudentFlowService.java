package com.b201.bibim.feature.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.monitoring.StudentPresenceEntity;
import com.b201.bibim.domain.monitoring.StudentPresenceRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomStage;
import com.b201.bibim.domain.student.StudentActivityProgressEntity;
import com.b201.bibim.domain.student.StudentActivityProgressRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentFlowStateEntity;
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.flow.StudentFlowDto.FlowResponse;
import com.b201.bibim.feature.flow.StudentFlowDto.TeacherFlowListResponse;
import com.b201.bibim.feature.flow.StudentFlowDto.TeacherStudentFlowResponse;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.feature.settlement.Day2FinanceService;
import com.b201.bibim.feature.settlement.Day2QuizRewardService;
import com.b201.bibim.feature.settlement.SettlementService;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentFlowService {

    private static final int LIVE_REWARD_RATE = 10_000;
    private static final int LATE_REWARD_RATE = 8_000;
    private static final Duration ACTIVE_PRESENCE_WINDOW = Duration.ofSeconds(30);

    private static final List<String> CATCH_UP_STAGES = List.of(
            "DAY1_PROFILE",
            "DAY1_QUIZ",
            "DAY1_INCOME_SETTLEMENT",
            "DAY1_PAYSLIP_GUIDED",
            "DAY1_KNOWLEDGE_GUIDED",
            "DAY1_SHOP_GUIDED",
            "DAY1_FINANCE_GUIDED",
            "DAY1_FREE_ACTIVITY",
            "DAY1_SUMMARY",
            "DAY2_RESULT_GUIDED",
            "DAY2_QUIZ",
            "DAY2_QUIZ_SETTLEMENT",
            "DAY2_TAX_INFLATION_GUIDED",
            "DAY2_FINAL_SHOP",
            "DAY2_FUND_RESULT",
            "DAY2_FINAL_RESULT"
    );

    private static final List<String> UNSAFE_JOIN_STAGES = List.of(
            "DAY1_INCOME_SETTLEMENT",
            "DAY2_FINANCE_SETTLEMENT",
            "DAY2_QUIZ_SETTLEMENT",
            "DAY2_INFLATION_APPLY"
    );

    private final StudentFlowStateRepository flowRepository;
    private final StudentActivityProgressRepository progressRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final AccountRepository accountRepository;
    private final StudentPresenceRepository presenceRepository;
    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final SettlementService settlementService;
    private final Day2FinanceService day2FinanceService;
    private final Day2QuizRewardService day2QuizRewardService;
    private final ActivityEventService activityEventService;

    public StudentFlowService(
            StudentFlowStateRepository flowRepository,
            StudentActivityProgressRepository progressRepository,
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            AccountRepository accountRepository,
            StudentPresenceRepository presenceRepository,
            QuizRepository quizRepository,
            QuizSubmissionRepository submissionRepository,
            SettlementService settlementService,
            Day2FinanceService day2FinanceService,
            Day2QuizRewardService day2QuizRewardService,
            ActivityEventService activityEventService
    ) {
        this.flowRepository = flowRepository;
        this.progressRepository = progressRepository;
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.accountRepository = accountRepository;
        this.presenceRepository = presenceRepository;
        this.quizRepository = quizRepository;
        this.submissionRepository = submissionRepository;
        this.settlementService = settlementService;
        this.day2FinanceService = day2FinanceService;
        this.day2QuizRewardService = day2QuizRewardService;
        this.activityEventService = activityEventService;
    }

    @Transactional
    public StudentFlowStateEntity initialize(StudentEntity student, RoomEntity room) {
        StudentFlowStateEntity existing = flowRepository.findByStudentId(student.getId()).orElse(null);
        if (existing != null) return existing;

        ensureStudentAccounts(room.getId(), student.getId());
        boolean startsLive = List.of("DAY1_LOBBY", "DAY1_PROFILE").contains(room.getCurrentStage());
        StudentFlowStateEntity state = startsLive
                ? StudentFlowStateEntity.live(room.getId(), student.getId(), room.getCurrentStage())
                : StudentFlowStateEntity.catchUp(room.getId(), student.getId());
        flowRepository.save(state);
        if (!startsLive) ensureProgress(state, "DAY1_PROFILE", "LATE", LATE_REWARD_RATE);
        return state;
    }

    @Transactional
    public FlowResponse current(UUID studentId) {
        StudentEntity student = activeStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(studentId)
                .orElseGet(() -> initializeExisting(student, room));
        refreshState(state, room);
        return response(student, room, state);
    }

    @Transactional
    public FlowResponse advance(UUID studentId) {
        StudentEntity student = activeStudentForUpdate(studentId);
        RoomEntity room = roomRepository.findByIdForUpdate(student.getRoomId()).orElseThrow(this::roomNotFound);
        StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> conflict("FLOW_NOT_FOUND", "학생 진행 상태를 찾을 수 없습니다."));
        if (!"CATCH_UP".equals(state.getFlowMode())) {
            throw conflict("ACTIVITY_LOCKED", "현재는 개인 보충 활동을 진행하는 상태가 아닙니다.");
        }

        String stage = state.getEffectiveStage();
        int activityRewardRate = currentActivityRewardRate(state, stage);
        validateCompletion(student, room, stage, activityRewardRate);
        completeProgress(state, stage);
        moveToNext(state, room);
        activityEventService.record(
                room.getId(), studentId, "STUDENT", studentId,
                "CATCH_UP_ACTIVITY_COMPLETED", room.getCurrentStage(), module(stage), stage,
                Map.of("activityCode", stage, "rewardRateBps", activityRewardRate)
        );
        room.markControlStateChanged();
        return response(student, room, state);
    }

    @Transactional(readOnly = true)
    public boolean canUseStage(UUID studentId, String stage) {
        return flowRepository.findByStudentId(studentId)
                .filter(state -> "CATCH_UP".equals(state.getFlowMode()))
                .map(state -> stage.equals(state.getEffectiveStage()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String effectiveStage(UUID studentId, String roomStage) {
        return flowRepository.findByStudentId(studentId)
                .map(state -> "LIVE".equals(state.getFlowMode()) ? roomStage : state.getEffectiveStage())
                .orElse(roomStage);
    }

    @Transactional(readOnly = true)
    public int rewardRate(UUID studentId) {
        return flowRepository.findByStudentId(studentId)
                .map(StudentFlowStateEntity::getRewardRateBps)
                .orElse(LIVE_REWARD_RATE);
    }

    @Transactional(readOnly = true)
    public boolean hasPendingStudents(UUID roomId) {
        return flowRepository.findAllByRoomIdOrderByStudentId(roomId).stream()
                .anyMatch(state -> List.of("CATCH_UP", "AWAITING_JOIN")
                        .contains(state.getFlowMode()));
    }

    @Transactional
    public void markQuizClosed(RoomEntity room, int dayNumber) {
        String quizStage = dayNumber == 1 ? "DAY1_QUIZ" : "DAY2_QUIZ";
        var quiz = quizRepository.findByRoomIdAndDayNumber(room.getId(), (short) dayNumber)
                .orElseThrow(() -> conflict("QUIZ_NOT_FOUND", "퀴즈를 찾을 수 없습니다."));
        var submittedStudentIds = submissionRepository.findAllByQuizId(quiz.getId()).stream()
                .filter(item -> item.isSubmitted())
                .map(item -> item.getStudentId())
                .collect(Collectors.toSet());
        for (StudentEntity student : studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(room.getId(), "ACTIVE")) {
            StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(student.getId())
                    .orElseGet(() -> initializeExisting(student, room));
            if (!"LIVE".equals(state.getFlowMode())) continue;
            if (submittedStudentIds.contains(student.getId())) {
                ensureProgress(state, quizStage, "LIVE", LIVE_REWARD_RATE).complete();
                continue;
            }
            state.moveTo(quizStage);
            ensureProgress(state, quizStage, "LATE", LATE_REWARD_RATE);
            activityEventService.record(
                    room.getId(), student.getId(), "SYSTEM", null,
                    "QUIZ_MARKED_LATE", room.getCurrentStage(), "QUIZ", quizStage,
                    Map.of("dayNumber", dayNumber, "rewardRateBps", LATE_REWARD_RATE)
            );
        }
    }

    @Transactional
    public void captureStageCompletion(RoomEntity room, String leavingStage) {
        if (!CATCH_UP_STAGES.contains(leavingStage)
                || List.of("DAY1_QUIZ", "DAY2_QUIZ").contains(leavingStage)) {
            return;
        }

        Instant activeSince = Instant.now().minus(ACTIVE_PRESENCE_WINDOW);
        Map<UUID, StudentPresenceEntity> presenceByStudent = presenceRepository
                .findAllByRoomId(room.getId()).stream()
                .collect(Collectors.toMap(StudentPresenceEntity::getStudentId, Function.identity()));

        for (StudentEntity student : studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(room.getId(), "ACTIVE")) {
            StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(student.getId())
                    .orElseGet(() -> initializeExisting(student, room));
            if (!"LIVE".equals(state.getFlowMode())) continue;

            StudentPresenceEntity presence = presenceByStudent.get(student.getId());
            boolean completedLive = presence != null
                    && presence.getForcedOfflineAt() == null
                    && leavingStage.equals(presence.getReportedStage())
                    && !presence.getLastSeenAt().isBefore(activeSince);
            if (completedLive) {
                ensureProgress(state, leavingStage, "LIVE", LIVE_REWARD_RATE).complete();
                continue;
            }

            state.moveTo(leavingStage);
            ensureProgress(state, leavingStage, "LATE", LATE_REWARD_RATE);
            activityEventService.record(
                    room.getId(), student.getId(), "SYSTEM", null,
                    "ACTIVITY_MARKED_LATE", leavingStage, module(leavingStage), leavingStage,
                    Map.of("activityCode", leavingStage, "rewardRateBps", LATE_REWARD_RATE)
            );
        }
    }

    @Transactional
    public void roomStageChanged(RoomEntity room) {
        if (UNSAFE_JOIN_STAGES.contains(room.getCurrentStage())) return;
        for (StudentFlowStateEntity state : flowRepository
                .findAllByRoomIdOrderByStudentId(room.getId())) {
            if ("APPROVED_PENDING_BOUNDARY".equals(state.getJoinStatus())) {
                state.joinAt(room.getCurrentStage());
            }
        }
    }

    @Transactional
    public TeacherFlowListResponse teacherFlows(UUID teacherId, UUID roomId) {
        RoomEntity room = ownedRoom(teacherId, roomId);
        Map<UUID, StudentFlowStateEntity> states = flowRepository
                .findAllByRoomIdOrderByStudentId(roomId).stream()
                .collect(Collectors.toMap(StudentFlowStateEntity::getStudentId, Function.identity()));
        List<TeacherStudentFlowResponse> students = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(roomId).stream()
                .map(student -> {
                    StudentFlowStateEntity state = states.get(student.getId());
                    if (state != null) refreshState(state, room);
                    FlowResponse flow = state == null ? null : response(student, room, state);
                    return new TeacherStudentFlowResponse(
                            student.getId(), student.getParticipantNumber(), student.getNickname(),
                            student.getStatus(), student.isOnline(), flow
                    );
                })
                .toList();
        return new TeacherFlowListResponse(roomId, room.getCurrentStage(), students);
    }

    @Transactional
    public FlowResponse approveJoin(UUID teacherId, UUID roomId, UUID studentId) {
        RoomEntity room = ownedRoomForUpdate(teacherId, roomId);
        StudentEntity student = studentInRoom(studentId, roomId);
        StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> conflict("FLOW_NOT_FOUND", "학생 진행 상태를 찾을 수 없습니다."));
        refreshState(state, room);
        if (!"AWAITING_JOIN".equals(state.getFlowMode())) {
            throw conflict("CATCH_UP_NOT_COMPLETED", "학생의 필수 보충 활동이 아직 끝나지 않았습니다.");
        }
        boolean pendingBoundary = UNSAFE_JOIN_STAGES.contains(room.getCurrentStage());
        state.approve(teacherId, room.getCurrentStage(), pendingBoundary);
        activityEventService.record(
                roomId, studentId, "TEACHER", teacherId,
                "CATCH_UP_JOIN_APPROVED", room.getCurrentStage(), null, state.getEffectiveStage(),
                Map.of("pendingBoundary", pendingBoundary)
        );
        room.markControlStateChanged();
        return response(student, room, state);
    }

    @Transactional
    public FlowResponse waive(
            UUID teacherId, UUID roomId, UUID studentId, String activityCode, String reason
    ) {
        RoomEntity room = ownedRoomForUpdate(teacherId, roomId);
        StudentEntity student = studentInRoom(studentId, roomId);
        StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> conflict("FLOW_NOT_FOUND", "학생 진행 상태를 찾을 수 없습니다."));
        StudentActivityProgressEntity progress = progressRepository
                .findByStudentIdAndActivityCode(studentId, activityCode)
                .orElseGet(() -> ensureProgress(state, activityCode, "LATE", 0));
        progress.waive(reason.trim());
        if (activityCode.equals(state.getEffectiveStage())) moveToNext(state, room);
        recordOverride(room, studentId, teacherId, "CATCH_UP_ACTIVITY_WAIVED", activityCode, reason);
        return response(student, room, state);
    }

    @Transactional
    public FlowResponse reopen(
            UUID teacherId, UUID roomId, UUID studentId, String activityCode, String reason
    ) {
        if ("DAY2_FUND_VOTE".equals(activityCode)) {
            throw conflict("ACTIVITY_CLOSED", "마감된 공동기금 투표는 다시 열 수 없습니다.");
        }
        RoomEntity room = ownedRoomForUpdate(teacherId, roomId);
        StudentEntity student = studentInRoom(studentId, roomId);
        StudentFlowStateEntity state = flowRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> conflict("FLOW_NOT_FOUND", "학생 진행 상태를 찾을 수 없습니다."));
        StudentActivityProgressEntity progress = progressRepository
                .findByStudentIdAndActivityCode(studentId, activityCode)
                .orElseGet(() -> ensureProgress(state, activityCode, "RECOVERY", LIVE_REWARD_RATE));
        progress.reopen(reason.trim());
        state.moveToRecovery(activityCode);
        recordOverride(room, studentId, teacherId, "CATCH_UP_ACTIVITY_REOPENED", activityCode, reason);
        return response(student, room, state);
    }

    private void validateCompletion(
            StudentEntity student, RoomEntity room, String stage, int rewardRateBps
    ) {
        if ("DAY1_QUIZ".equals(stage) || "DAY2_QUIZ".equals(stage)) {
            short day = (short) ("DAY1_QUIZ".equals(stage) ? 1 : 2);
            var quiz = quizRepository.findByRoomIdAndDayNumber(room.getId(), day)
                    .orElseThrow(() -> conflict("QUIZ_NOT_FOUND", "퀴즈를 찾을 수 없습니다."));
            boolean submitted = submissionRepository
                    .findByQuizIdAndStudentId(quiz.getId(), student.getId())
                    .map(item -> item.isSubmitted())
                    .orElse(false);
            if (!submitted) {
                throw conflict("QUIZ_SUBMISSION_REQUIRED", "퀴즈를 제출한 뒤 다음 활동으로 이동해 주세요.");
            }
        }
        if ("DAY1_INCOME_SETTLEMENT".equals(stage)
                && !settlementService.settleCatchUpStudent(student.getId(), rewardRateBps)) {
            throw conflict("SETTLEMENT_NOT_READY", "반 소득 정산이 끝난 뒤 보충 정산을 진행할 수 있습니다.");
        }
        if ("DAY1_FREE_ACTIVITY".equals(stage)) {
            day2FinanceService.settleCatchUpStudent(student.getId(), rewardRateBps);
        }
        if ("DAY2_RESULT_GUIDED".equals(stage)
                && !day2FinanceService.settleCatchUpStudent(student.getId(), rewardRateBps)) {
            throw conflict("SETTLEMENT_NOT_READY", "반 금융 정산이 끝난 뒤 보충 결과를 확인할 수 있습니다.");
        }
        if ("DAY2_QUIZ_SETTLEMENT".equals(stage)
                && !day2QuizRewardService.settleCatchUpStudent(student.getId(), rewardRateBps)) {
            throw conflict("SETTLEMENT_NOT_READY", "반 퀴즈 보상 정산이 끝난 뒤 보충 정산을 진행할 수 있습니다.");
        }
    }

    private void refreshState(StudentFlowStateEntity state, RoomEntity room) {
        if ("DAY2_COMPLETED".equals(room.getCurrentStage()) || "ARCHIVED".equals(room.getStatus())) {
            state.lock(room.getCurrentStage());
            return;
        }
        if ("LIVE".equals(state.getFlowMode())) {
            state.syncLive(room.getCurrentStage());
            return;
        }
        if ("APPROVED_PENDING_BOUNDARY".equals(state.getJoinStatus())
                && !UNSAFE_JOIN_STAGES.contains(room.getCurrentStage())) {
            state.joinAt(room.getCurrentStage());
            return;
        }
        if ("AWAITING_JOIN".equals(state.getFlowMode())) {
            String next = nextMissingStage(state.getStudentId(), room.getCurrentStage());
            if (next != null) {
                state.moveTo(next);
                ensureProgress(state, next, "LATE", state.getRewardRateBps());
            }
        }
    }

    private void moveToNext(StudentFlowStateEntity state, RoomEntity room) {
        String next = nextMissingStage(state.getStudentId(), room.getCurrentStage());
        if (next == null) {
            state.awaitJoin();
            return;
        }
        state.moveTo(next);
        ensureProgress(state, next, "LATE", state.getRewardRateBps());
    }

    private String nextMissingStage(UUID studentId, String roomStage) {
        int roomIndex = stageIndex(roomStage);
        Map<String, StudentActivityProgressEntity> progress = progressRepository
                .findAllByStudentId(studentId).stream()
                .collect(Collectors.toMap(StudentActivityProgressEntity::getActivityCode, Function.identity()));
        for (String stage : CATCH_UP_STAGES) {
            if (stageIndex(stage) >= roomIndex) continue;
            StudentActivityProgressEntity item = progress.get(stage);
            if (item == null || !List.of("COMPLETED", "WAIVED", "RESULT_ONLY").contains(item.getStatus())) {
                return stage;
            }
        }
        return null;
    }

    private void completeProgress(StudentFlowStateEntity state, String stage) {
        StudentActivityProgressEntity progress = progressRepository
                .findByStudentIdAndActivityCode(state.getStudentId(), stage)
                .orElseGet(() -> ensureProgress(
                        state, stage, "LATE", state.getRewardRateBps()
                ));
        progress.complete();
    }

    private StudentActivityProgressEntity ensureProgress(
            StudentFlowStateEntity state, String stage, String attemptType, int rewardRateBps
    ) {
        return progressRepository.findByStudentIdAndActivityCode(state.getStudentId(), stage)
                .orElseGet(() -> progressRepository.save(
                        StudentActivityProgressEntity.available(
                                state.getRoomId(), state.getStudentId(), stage,
                                attemptType, rewardRateBps
                        )
                ));
    }

    private FlowResponse response(
            StudentEntity student, RoomEntity room, StudentFlowStateEntity state
    ) {
        List<StudentActivityProgressEntity> progress = progressRepository
                .findAllByStudentId(student.getId());
        int completed = (int) progress.stream()
                .filter(item -> List.of("COMPLETED", "WAIVED", "RESULT_ONLY").contains(item.getStatus()))
                .count();
        int total = applicableStages(room.getCurrentStage()).size();
        String attemptType = progress.stream()
                .filter(item -> item.getActivityCode().equals(state.getEffectiveStage()))
                .map(StudentActivityProgressEntity::getAttemptType)
                .findFirst()
                .orElse("LIVE".equals(state.getFlowMode()) ? "LIVE" : "LATE");
        int displayedRewardRate = progress.stream()
                .filter(item -> item.getActivityCode().equals(state.getEffectiveStage()))
                .map(StudentActivityProgressEntity::getRewardRateBps)
                .findFirst()
                .orElse(state.getRewardRateBps());
        return new FlowResponse(
                student.getId(), room.getId(), room.getCurrentStage(), state.getEffectiveStage(),
                state.getFlowMode(), state.getJoinStatus(), attemptType,
                displayedRewardRate, completed, total,
                "CATCH_UP".equals(state.getFlowMode())
        );
    }

    private List<String> applicableStages(String roomStage) {
        int roomIndex = stageIndex(roomStage);
        return CATCH_UP_STAGES.stream().filter(stage -> stageIndex(stage) < roomIndex).toList();
    }

    private int stageIndex(String stage) {
        return RoomStage.find(stage).map(value -> List.of(RoomStage.values()).indexOf(value)).orElse(-1);
    }

    private int currentActivityRewardRate(StudentFlowStateEntity state, String stage) {
        return progressRepository.findByStudentIdAndActivityCode(state.getStudentId(), stage)
                .map(StudentActivityProgressEntity::getRewardRateBps)
                .orElse(state.getRewardRateBps());
    }

    private void ensureStudentAccounts(UUID roomId, UUID studentId) {
        List<AccountEntity> missing = new ArrayList<>();
        if (accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_WALLET").isEmpty()) {
            missing.add(AccountEntity.studentWallet(roomId, studentId));
        }
        if (accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_DEPOSIT").isEmpty()) {
            missing.add(AccountEntity.studentDeposit(roomId, studentId));
        }
        if (accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_INVESTMENT").isEmpty()) {
            missing.add(AccountEntity.studentInvestment(roomId, studentId));
        }
        if (!missing.isEmpty()) accountRepository.saveAll(missing);
    }

    private StudentFlowStateEntity initializeExisting(StudentEntity student, RoomEntity room) {
        StudentFlowStateEntity state = StudentFlowStateEntity.live(
                room.getId(), student.getId(), room.getCurrentStage()
        );
        ensureStudentAccounts(room.getId(), student.getId());
        flowRepository.save(state);
        for (String completedStage : applicableStages(room.getCurrentStage())) {
            ensureProgress(state, completedStage, "LIVE", LIVE_REWARD_RATE).complete();
        }
        return state;
    }

    private String module(String stage) {
        if (stage.contains("QUIZ")) return "QUIZ";
        if (stage.contains("SHOP")) return "SHOP";
        if (stage.contains("FINANCE") || stage.contains("PAYSLIP")) return "FINANCE";
        return "LEARNING";
    }

    private void recordOverride(
            RoomEntity room, UUID studentId, UUID teacherId,
            String eventType, String activityCode, String reason
    ) {
        activityEventService.record(
                room.getId(), studentId, "TEACHER", teacherId, eventType,
                room.getCurrentStage(), module(activityCode), activityCode,
                Map.of("activityCode", activityCode, "reason", reason.trim())
        );
        room.markControlStateChanged();
    }

    private StudentEntity activeStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private StudentEntity activeStudentForUpdate(UUID studentId) {
        return studentRepository.findByIdForUpdate(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private StudentEntity studentInRoom(UUID studentId, UUID roomId) {
        return studentRepository.findById(studentId)
                .filter(student -> student.getRoomId().equals(roomId))
                .orElseThrow(this::roomNotFound);
    }

    private RoomEntity ownedRoom(UUID teacherId, UUID roomId) {
        return roomRepository.findById(roomId)
                .filter(room -> room.getTeacherId().equals(teacherId))
                .orElseThrow(this::roomNotFound);
    }

    private RoomEntity ownedRoomForUpdate(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        if (!room.getTeacherId().equals(teacherId)) throw roomNotFound();
        return room;
    }

    private ApiException authenticationRequired() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."
        );
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
