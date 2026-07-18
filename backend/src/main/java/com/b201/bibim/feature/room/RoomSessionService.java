package com.b201.bibim.feature.room;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomCommandRequestEntity;
import com.b201.bibim.domain.room.RoomCommandRequestRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomStage;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.vote.VoteSessionRepository;
import com.b201.bibim.feature.room.RoomSessionDto.CommandRequest;
import com.b201.bibim.feature.room.RoomSessionDto.CommandResponse;
import com.b201.bibim.feature.room.RoomSessionDto.ModuleStateResponse;
import com.b201.bibim.feature.room.RoomSessionDto.StudentRoomStateResponse;
import com.b201.bibim.feature.room.RoomSessionDto.TeacherRoomStateResponse;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.day1.Day1Service;
import com.b201.bibim.feature.flow.StudentFlowDto.FlowResponse;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.feature.settlement.Day2QuizRewardService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomSessionService {

    private static final List<String> MODULE_TYPES = List.of(
            "QUIZ",
            "PAYSLIP",
            "KNOWLEDGE",
            "SHOP",
            "DEPOSIT",
            "INVESTMENT",
            "WALLET",
            "INVESTMENT_RESULT",
            "FUND_VOTE",
            "FINAL_RESULT",
            "SURVEY"
    );

    private final RoomRepository roomRepository;
    private final RoomCommandRequestRepository commandRequestRepository;
    private final StudentRepository studentRepository;
    private final AccountRepository accountRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final QuizRepository quizRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final Day1Service day1Service;
    private final DaySessionRepository sessionRepository;
    private final Day2QuizRewardService day2QuizRewardService;
    private final FundExpenseRepository fundExpenseRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final StudentFlowService studentFlowService;

    public RoomSessionService(
            RoomRepository roomRepository,
            RoomCommandRequestRepository commandRequestRepository,
            StudentRepository studentRepository,
            AccountRepository accountRepository,
            SettlementRunRepository settlementRunRepository,
            QuizRepository quizRepository,
            QuizSubmissionRepository quizSubmissionRepository,
            Day1Service day1Service,
            DaySessionRepository sessionRepository,
            Day2QuizRewardService day2QuizRewardService,
            FundExpenseRepository fundExpenseRepository,
            VoteSessionRepository voteSessionRepository,
            StudentFlowService studentFlowService
    ) {
        this.roomRepository = roomRepository;
        this.commandRequestRepository = commandRequestRepository;
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.settlementRunRepository = settlementRunRepository;
        this.quizRepository = quizRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.day1Service = day1Service;
        this.sessionRepository = sessionRepository;
        this.day2QuizRewardService = day2QuizRewardService;
        this.fundExpenseRepository = fundExpenseRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.studentFlowService = studentFlowService;
    }

    @Transactional(readOnly = true)
    public TeacherRoomStateResponse teacherState(
            UUID teacherId,
            UUID roomId,
            Long clientSyncVersion
    ) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        return toTeacherState(room, hasChanged(room, clientSyncVersion));
    }

    @Transactional
    public StudentRoomStateResponse studentState(UUID studentId, Long clientSyncVersion) {
        StudentEntity student = studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        FlowResponse flow = studentFlowService.current(studentId);

        return new StudentRoomStateResponse(
                room.getId(),
                room.getStatus(),
                room.getCurrentDay(),
                room.getCurrentStage(),
                room.getCurrentStage(),
                flow.effectiveStage(),
                RoomStage.find(flow.effectiveStage())
                        .map(RoomStage::mode)
                        .orElse(room.getMode()),
                walletBalance(room.getId(), student.getId()),
                moduleStates(room),
                room.getSyncVersion(),
                hasChanged(room, clientSyncVersion),
                flow.flowMode(),
                flow.joinStatus(),
                flow.attemptType(),
                flow.rewardRateBps(),
                flow.completedCount(),
                flow.totalCount()
        );
    }

    @Transactional
    public CommandResponse command(
            UUID teacherId,
            UUID roomId,
            String idempotencyKey,
            CommandRequest request
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String command = request.command().trim().toUpperCase(Locale.ROOT);
        String targetStageValue = request.targetStage().trim().toUpperCase(Locale.ROOT);

        RoomEntity room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);

        var previousRequest = commandRequestRepository
                .findByRoomIdAndIdempotencyKey(roomId, normalizedKey);
        if (previousRequest.isPresent()) {
            if (!previousRequest.get().matches(
                    command,
                    targetStageValue,
                    request.expectedVersion()
            )) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED",
                        "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다."
                );
            }
            return new CommandResponse(toTeacherState(room, true), true);
        }

        if (room.getVersion() != request.expectedVersion()) {
            throw stateConflict("다른 화면에서 수업 상태가 변경되었습니다. 최신 상태를 확인해 주세요.");
        }

        if ("COMPLETED".equals(room.getStatus()) || RoomStage.DAY2_COMPLETED.name().equals(room.getCurrentStage())) {
            throw stateConflict("종료된 수업에서는 상태를 변경할 수 없습니다.");
        }

        if (Set.of("OPEN_QUIZ", "CLOSE_QUIZ", "FORCE_CLOSE_QUIZ").contains(command)) {
            controlQuiz(room, command);
            commandRequestRepository.save(RoomCommandRequestEntity.completed(
                    room,
                    normalizedKey,
                    command,
                    targetStageValue,
                    request.expectedVersion()
            ));
            return new CommandResponse(toTeacherState(room, true), false);
        }

        if ("CLOSE_DAY1".equals(command)) {
            studentFlowService.captureStageCompletion(room, room.getCurrentStage());
            day1Service.closeDayOne(teacherId, room);
            studentFlowService.roomStageChanged(room);
            commandRequestRepository.save(RoomCommandRequestEntity.completed(
                    room,
                    normalizedKey,
                    command,
                    targetStageValue,
                    request.expectedVersion()
            ));
            return new CommandResponse(toTeacherState(room, true), false);
        }

        if ("START_DAY2".equals(command)) {
            if (!RoomStage.DAY1_CLOSED.name().equals(room.getCurrentStage())) {
                throw stateConflict("1일차를 마감한 뒤 2일차를 시작해 주세요.");
            }
            if (sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2).isPresent()) {
                throw stateConflict("2일차 세션이 이미 시작되었습니다.");
            }
            sessionRepository.save(DaySessionEntity.dayTwoActive(roomId));
            day2QuizRewardService.ensureQuiz(roomId);
            room.startDayTwo();
            studentFlowService.roomStageChanged(room);
            commandRequestRepository.save(RoomCommandRequestEntity.completed(
                    room,
                    normalizedKey,
                    command,
                    RoomStage.DAY2_LOGIN.name(),
                    request.expectedVersion()
            ));
            return new CommandResponse(toTeacherState(room, true), false);
        }

        if ("COMPLETE_ROOM".equals(command)) {
            if (!RoomStage.DAY2_SURVEY.name().equals(room.getCurrentStage())
                    || !RoomStage.DAY2_COMPLETED.name().equals(targetStageValue)) {
                throw stateConflict("설문 단계에서만 수업을 종료할 수 있습니다.");
            }
            if (studentFlowService.hasPendingStudents(roomId)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CATCH_UP_STUDENTS_PENDING",
                        "보충 중이거나 합류 승인 대기 중인 학생을 먼저 처리해 주세요."
                );
            }
            DaySessionEntity session = sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2)
                    .orElseThrow(() -> stateConflict("2일차 세션을 찾을 수 없습니다."));
            session.closeDayTwo();
            room.complete();
            commandRequestRepository.save(RoomCommandRequestEntity.completed(
                    room,
                    normalizedKey,
                    command,
                    RoomStage.DAY2_COMPLETED.name(),
                    request.expectedVersion()
            ));
            return new CommandResponse(toTeacherState(room, true), false);
        }

        if (!"SET_STAGE".equals(command)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_COMMAND",
                    "사용할 수 없는 수업 제어 명령입니다."
            );
        }

        RoomStage targetStage = RoomStage.find(targetStageValue)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_STAGE",
                        "존재하지 않는 수업 단계입니다."
                ));

        if (targetStage == RoomStage.DAY1_CLOSED || targetStage == RoomStage.DAY2_COMPLETED) {
            throw stateConflict("수업 마감은 전용 종료 명령으로 진행해 주세요.");
        }

        RoomStage currentStage = RoomStage.find(room.getCurrentStage())
                .orElseThrow(() -> stateConflict("현재 수업 단계를 확인할 수 없습니다."));
        if (currentStage == RoomStage.DAY1_CLOSED || targetStage == RoomStage.DAY2_LOGIN) {
            throw stateConflict("2일차 시작은 START_DAY2 명령으로 진행해 주세요.");
        }
        if (!currentStage.canMoveTo(targetStage)) {
            throw stateConflict("현재 일차 안의 다른 단계만 선택할 수 있습니다.");
        }
        validateStageNavigation(room, roomId, currentStage, targetStage);

        int expectedVersion = request.expectedVersion();
        if (targetStage == RoomStage.DAY2_QUIZ) {
            day2QuizRewardService.ensureQuiz(roomId);
        }
        if (targetStage.ordinal() > currentStage.ordinal()) {
            studentFlowService.captureStageCompletion(room, currentStage.name());
        }
        room.changeStage(targetStage);
        studentFlowService.roomStageChanged(room);
        commandRequestRepository.save(RoomCommandRequestEntity.completed(
                room,
                normalizedKey,
                command,
                targetStage.name(),
                expectedVersion
        ));

        return new CommandResponse(toTeacherState(room, true), false);
    }

    private void validateStageNavigation(
            RoomEntity room,
            UUID roomId,
            RoomStage currentStage,
            RoomStage targetStage
    ) {
        if ((movesForwardAcross(currentStage, targetStage, RoomStage.DAY1_QUIZ)
                || movesForwardAcross(currentStage, targetStage, RoomStage.DAY2_QUIZ))
                && !"CLOSED".equals(currentQuiz(room).getStatus())) {
            throw stateConflict("퀴즈를 마감한 뒤 다음 단계로 이동해 주세요.");
        }
        if (currentStage.ordinal() < RoomStage.DAY1_QUIZ_RESULT.ordinal()
                && targetStage.ordinal() > RoomStage.DAY1_QUIZ_RESULT.ordinal()) {
            throw stateConflict("1일차 퀴즈 결과를 확인한 뒤 소득 정산 단계로 이동해 주세요.");
        }
        if (movesForwardAcross(currentStage, targetStage, RoomStage.DAY1_INCOME_SETTLEMENT)
                && !settlementCompleted(roomId, "DAY1_INCOME")) {
            throw stateConflict("소득 정산을 완료한 뒤 월급명세서 이후 단계로 이동해 주세요.");
        }
        if (movesForwardAcross(currentStage, targetStage, RoomStage.DAY2_FINANCE_SETTLEMENT)
                && !settlementCompleted(roomId, "DAY2_FINANCE")) {
            throw stateConflict("금융 정산을 완료한 뒤 개인 결과 이후 단계로 이동해 주세요.");
        }
        if (movesForwardAcross(currentStage, targetStage, RoomStage.DAY2_QUIZ_SETTLEMENT)
                && !settlementCompleted(roomId, "DAY2_QUIZ_REWARD")) {
            throw stateConflict("2일차 퀴즈 보상 정산을 완료한 뒤 다음 단계로 이동해 주세요.");
        }
        if (movesForwardAcross(currentStage, targetStage, RoomStage.DAY2_INFLATION_APPLY)
                && room.getInflationAppliedAt() == null) {
            throw stateConflict("물가상승을 적용한 뒤 최종 상점 이후 단계로 이동해 주세요.");
        }
        if (movesForwardAcross(currentStage, targetStage, RoomStage.DAY2_FUND_COST)
                && voteSessionRepository.findByRoomId(roomId).isEmpty()) {
            throw stateConflict("공동비용을 검토하고 투표 선택지를 준비한 뒤 투표 단계로 이동해 주세요.");
        }
        if (movesForwardAcross(currentStage, targetStage, RoomStage.DAY2_FUND_VOTE)
                && !voteSessionRepository.existsByRoomIdAndStatus(roomId, "DECIDED")) {
            throw stateConflict("공동기금 투표 결과를 확정한 뒤 결과 이후 단계로 이동해 주세요.");
        }

        if (movesBackwardBefore(currentStage, targetStage, RoomStage.DAY1_INCOME_SETTLEMENT)
                && settlementCompleted(roomId, "DAY1_INCOME")) {
            throw stateConflict("완료된 소득 정산 이전 단계로는 돌아갈 수 없습니다.");
        }
        if (currentStage.day() == 2 && targetStage.ordinal() < currentStage.ordinal()) {
            var vote = voteSessionRepository.findByRoomId(roomId);
            if (vote.filter(item -> "DECIDED".equals(item.getStatus())).isPresent()
                    && targetStage.ordinal() < RoomStage.DAY2_FUND_RESULT.ordinal()) {
                throw stateConflict("확정된 공동기금 투표 결과 이전 단계로는 돌아갈 수 없습니다.");
            }
            if (vote.filter(item -> !"READY".equals(item.getStatus())).isPresent()
                    && targetStage.ordinal() < RoomStage.DAY2_FUND_VOTE.ordinal()) {
                throw stateConflict("투표를 시작한 뒤에는 공동비용 이전 단계로 돌아갈 수 없습니다.");
            }
            if ((vote.isPresent() || fundExpenseRepository.existsByRoomId(roomId))
                    && targetStage.ordinal() < RoomStage.DAY2_FUND_COST.ordinal()) {
                throw stateConflict("공동기금 처리를 확정한 뒤에는 최종 상점 이전 단계로 돌아갈 수 없습니다.");
            }
        }
    }

    private boolean settlementCompleted(UUID roomId, String settlementType) {
        return settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId,
                settlementType,
                "COMPLETED"
        );
    }

    private boolean movesForwardAcross(
            RoomStage currentStage,
            RoomStage targetStage,
            RoomStage checkpoint
    ) {
        return currentStage.ordinal() <= checkpoint.ordinal()
                && targetStage.ordinal() > checkpoint.ordinal();
    }

    private boolean movesBackwardBefore(
            RoomStage currentStage,
            RoomStage targetStage,
            RoomStage checkpoint
    ) {
        return targetStage.ordinal() < currentStage.ordinal()
                && targetStage.ordinal() < checkpoint.ordinal()
                && currentStage.ordinal() >= checkpoint.ordinal();
    }

    private void controlQuiz(RoomEntity room, String command) {
        String expectedStage = room.getCurrentDay() == 1 ? "DAY1_QUIZ" : "DAY2_QUIZ";
        if (!expectedStage.equals(room.getCurrentStage())) {
            throw stateConflict("퀴즈 단계에서만 퀴즈를 시작하거나 마감할 수 있습니다.");
        }

        QuizEntity quiz = currentQuiz(room);
        if ("OPEN_QUIZ".equals(command)) {
            if (!"READY".equals(quiz.getStatus())) {
                throw stateConflict("준비 상태의 퀴즈만 시작할 수 있습니다.");
            }
            quiz.open();
        } else {
            if (!"OPEN".equals(quiz.getStatus())) {
                throw stateConflict("진행 중인 퀴즈만 마감할 수 있습니다.");
            }
            if ("CLOSE_QUIZ".equals(command) && hasIncompleteQuizStudents(room, quiz)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "QUIZ_INCOMPLETE",
                        "아직 퀴즈를 제출하지 않은 학생이 있습니다."
                );
            }
            quiz.close();
            studentFlowService.markQuizClosed(room, quiz.getDayNumber());
        }
        room.markControlStateChanged();
    }

    private boolean hasIncompleteQuizStudents(RoomEntity room, QuizEntity quiz) {
        Set<UUID> submittedStudentIds = quizSubmissionRepository.findAllByQuizId(quiz.getId())
                .stream()
                .filter(submission -> "SUBMITTED".equals(submission.getStatus()))
                .map(submission -> submission.getStudentId())
                .collect(java.util.stream.Collectors.toSet());
        return studentRepository
                .findAllByRoomIdAndStatusOrderByParticipantNumber(room.getId(), "ACTIVE")
                .stream()
                .anyMatch(student -> !submittedStudentIds.contains(student.getId()));
    }

    private QuizEntity currentQuiz(RoomEntity room) {
        return quizRepository.findByRoomIdAndDayNumber(
                room.getId(),
                (short) room.getCurrentDay()
        ).orElseThrow(() -> stateConflict("현재 일차의 퀴즈를 찾을 수 없습니다."));
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

    private TeacherRoomStateResponse toTeacherState(RoomEntity room, boolean changed) {
        return new TeacherRoomStateResponse(
                room.getId(),
                room.getName(),
                room.getStatus(),
                room.getCurrentDay(),
                room.getCurrentStage(),
                room.getMode(),
                room.getVersion(),
                room.getSyncVersion(),
                room.getExpectedStudentCount(),
                studentRepository.countByRoomIdAndOnlineTrue(room.getId()),
                moduleStates(room),
                changed,
                room.getUpdatedAt()
        );
    }

    private List<ModuleStateResponse> moduleStates(RoomEntity room) {
        if ("CLOSED".equals(room.getMode())) {
            return MODULE_TYPES.stream()
                    .map(type -> new ModuleStateResponse(type, "CLOSED"))
                    .toList();
        }

        Set<String> activeTypes = activeModuleTypes(room);
        String enabledState = "FREE".equals(room.getMode()) ? "ACTIVE" : "GUIDED";
        return MODULE_TYPES.stream()
                .map(type -> new ModuleStateResponse(
                        type,
                        activeTypes.contains(type) ? enabledState : "LOCKED"
                ))
                .toList();
    }

    private Set<String> activeModuleTypes(RoomEntity room) {
        if ("FREE".equals(room.getMode())) {
            if (room.getCurrentDay() == 2) {
                return Set.of("KNOWLEDGE", "SHOP", "WALLET");
            }
            return Set.of("KNOWLEDGE", "SHOP", "DEPOSIT", "INVESTMENT", "WALLET");
        }

        String stage = room.getCurrentStage();
        if (stage.contains("QUIZ")) {
            return Set.of("QUIZ");
        }
        if (stage.contains("PAYSLIP") || stage.contains("INCOME_SETTLEMENT")) {
            return Set.of("PAYSLIP");
        }
        if (stage.contains("KNOWLEDGE") || stage.contains("TAX_INFLATION")) {
            return Set.of("KNOWLEDGE");
        }
        if (stage.contains("SHOP")) {
            return Set.of("SHOP");
        }
        if (stage.contains("FINANCE") || stage.contains("SETTLEMENT_WAIT")) {
            return Set.of("DEPOSIT", "INVESTMENT", "INVESTMENT_RESULT");
        }
        if (stage.contains("FUND")) {
            return Set.of("FUND_VOTE");
        }
        if (stage.contains("FINAL_RESULT") || stage.contains("SUMMARY")) {
            return Set.of("FINAL_RESULT");
        }
        if (stage.contains("SURVEY")) {
            return Set.of("SURVEY");
        }
        return Set.of();
    }

    private boolean hasChanged(RoomEntity room, Long clientSyncVersion) {
        return clientSyncVersion == null || clientSyncVersion != room.getSyncVersion();
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

    private ApiException stateConflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", message);
    }

    private ApiException roomNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "수업 방을 찾을 수 없습니다."
        );
    }
}
