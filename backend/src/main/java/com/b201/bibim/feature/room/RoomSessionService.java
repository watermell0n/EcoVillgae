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
    private final Day1Service day1Service;
    private final DaySessionRepository sessionRepository;
    private final Day2QuizRewardService day2QuizRewardService;
    private final FundExpenseRepository fundExpenseRepository;
    private final VoteSessionRepository voteSessionRepository;

    public RoomSessionService(
            RoomRepository roomRepository,
            RoomCommandRequestRepository commandRequestRepository,
            StudentRepository studentRepository,
            AccountRepository accountRepository,
            SettlementRunRepository settlementRunRepository,
            QuizRepository quizRepository,
            Day1Service day1Service,
            DaySessionRepository sessionRepository,
            Day2QuizRewardService day2QuizRewardService,
            FundExpenseRepository fundExpenseRepository,
            VoteSessionRepository voteSessionRepository
    ) {
        this.roomRepository = roomRepository;
        this.commandRequestRepository = commandRequestRepository;
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.settlementRunRepository = settlementRunRepository;
        this.quizRepository = quizRepository;
        this.day1Service = day1Service;
        this.sessionRepository = sessionRepository;
        this.day2QuizRewardService = day2QuizRewardService;
        this.fundExpenseRepository = fundExpenseRepository;
        this.voteSessionRepository = voteSessionRepository;
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

    @Transactional(readOnly = true)
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

        return new StudentRoomStateResponse(
                room.getId(),
                room.getStatus(),
                room.getCurrentDay(),
                room.getCurrentStage(),
                room.getMode(),
                walletBalance(room.getId(), student.getId()),
                moduleStates(room),
                room.getSyncVersion(),
                hasChanged(room, clientSyncVersion)
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

        if ("OPEN_QUIZ".equals(command) || "CLOSE_QUIZ".equals(command)) {
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
            day1Service.closeDayOne(teacherId, room);
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
        if (currentStage == RoomStage.DAY2_FINANCE_SETTLEMENT
                && targetStage == RoomStage.DAY2_RESULT_GUIDED
                && !settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                        roomId,
                        "DAY2_FINANCE",
                        "COMPLETED"
                )) {
            throw stateConflict("금융 정산을 완료한 뒤 개인 결과 단계로 이동해 주세요.");
        }
        if (currentStage == RoomStage.DAY2_QUIZ_SETTLEMENT
                && targetStage == RoomStage.DAY2_TAX_INFLATION_GUIDED
                && !settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                        roomId,
                        "DAY2_QUIZ_REWARD",
                        "COMPLETED"
                )) {
            throw stateConflict("2일차 퀴즈 보상 정산을 완료한 뒤 다음 단계로 이동해 주세요.");
        }
        if (currentStage == RoomStage.DAY2_INFLATION_APPLY
                && targetStage == RoomStage.DAY2_FINAL_SHOP
                && room.getInflationAppliedAt() == null) {
            throw stateConflict("물가상승을 적용한 뒤 최종 상점을 열어 주세요.");
        }
        if (currentStage == RoomStage.DAY2_FUND_COST
                && targetStage == RoomStage.DAY2_FUND_VOTE
                && voteSessionRepository.findByRoomId(roomId).isEmpty()) {
            throw stateConflict("공동비용을 검토하고 투표 선택지를 준비한 뒤 투표 단계로 이동해 주세요.");
        }
        if (currentStage == RoomStage.DAY2_FUND_COST
                && targetStage == RoomStage.DAY2_FINAL_SHOP
                && (fundExpenseRepository.existsByRoomId(roomId)
                || voteSessionRepository.findByRoomId(roomId).isPresent())) {
            throw stateConflict("공동기금 처리를 확정한 뒤에는 최종 상점 단계로 돌아갈 수 없습니다.");
        }
        if (currentStage == RoomStage.DAY2_FUND_VOTE
                && targetStage == RoomStage.DAY2_FINAL_RESULT
                && !voteSessionRepository.existsByRoomIdAndStatus(roomId, "DECIDED")) {
            throw stateConflict("공동기금 투표 결과를 확정한 뒤 최종 결과로 이동해 주세요.");
        }
        if (currentStage == RoomStage.DAY2_FUND_VOTE
                && targetStage == RoomStage.DAY2_FUND_COST
                && voteSessionRepository.findByRoomId(roomId)
                .filter(vote -> !"READY".equals(vote.getStatus())).isPresent()) {
            throw stateConflict("투표를 시작한 뒤에는 공동비용 단계로 돌아갈 수 없습니다.");
        }
        if (!currentStage.canMoveTo(targetStage)) {
            throw stateConflict("수업 단계는 현재 단계의 이전 또는 다음 단계로만 이동할 수 있습니다.");
        }
        if (((currentStage == RoomStage.DAY1_QUIZ
                && targetStage == RoomStage.DAY1_INCOME_SETTLEMENT)
                || (currentStage == RoomStage.DAY2_QUIZ
                && targetStage == RoomStage.DAY2_QUIZ_SETTLEMENT))
                && !currentQuiz(room).getStatus().equals("CLOSED")) {
            throw stateConflict("퀴즈를 마감한 뒤 다음 단계로 이동해 주세요.");
        }
        if (currentStage == RoomStage.DAY1_INCOME_SETTLEMENT
                && targetStage == RoomStage.DAY1_PAYSLIP_GUIDED
                && !settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                        roomId,
                        "DAY1_INCOME",
                        "COMPLETED"
                )) {
            throw stateConflict("소득 정산을 완료한 뒤 월급명세서 단계로 이동해 주세요.");
        }
        if (currentStage == RoomStage.DAY1_INCOME_SETTLEMENT
                && targetStage == RoomStage.DAY1_QUIZ
                && settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                        roomId,
                        "DAY1_INCOME",
                        "COMPLETED"
                )) {
            throw stateConflict("완료된 소득 정산 이전 단계로는 돌아갈 수 없습니다.");
        }

        int expectedVersion = request.expectedVersion();
        if (targetStage == RoomStage.DAY2_QUIZ) {
            day2QuizRewardService.ensureQuiz(roomId);
        }
        room.changeStage(targetStage);
        commandRequestRepository.save(RoomCommandRequestEntity.completed(
                room,
                normalizedKey,
                command,
                targetStage.name(),
                expectedVersion
        ));

        return new CommandResponse(toTeacherState(room, true), false);
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
            quiz.close();
        }
        room.markControlStateChanged();
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
