package com.b201.bibim.feature.room;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.room.RoomCommandRequestEntity;
import com.b201.bibim.domain.room.RoomCommandRequestRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomStage;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.feature.room.RoomSessionDto.CommandRequest;
import com.b201.bibim.feature.room.RoomSessionDto.CommandResponse;
import com.b201.bibim.feature.room.RoomSessionDto.ModuleStateResponse;
import com.b201.bibim.feature.room.RoomSessionDto.StudentRoomStateResponse;
import com.b201.bibim.feature.room.RoomSessionDto.TeacherRoomStateResponse;
import com.b201.bibim.global.exception.ApiException;

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

    public RoomSessionService(
            RoomRepository roomRepository,
            RoomCommandRequestRepository commandRequestRepository,
            StudentRepository studentRepository,
            AccountRepository accountRepository,
            SettlementRunRepository settlementRunRepository
    ) {
        this.roomRepository = roomRepository;
        this.commandRequestRepository = commandRequestRepository;
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.settlementRunRepository = settlementRunRepository;
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

        if (!"SET_STAGE".equals(command)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_COMMAND",
                    "현재 단계에서는 SET_STAGE 명령만 사용할 수 있습니다."
            );
        }

        RoomStage targetStage = RoomStage.find(targetStageValue)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_STAGE",
                        "존재하지 않는 수업 단계입니다."
                ));

        if (room.getVersion() != request.expectedVersion()) {
            throw stateConflict("다른 화면에서 수업 상태가 변경되었습니다. 최신 상태를 확인해 주세요.");
        }

        RoomStage currentStage = RoomStage.find(room.getCurrentStage())
                .orElseThrow(() -> stateConflict("현재 수업 단계를 확인할 수 없습니다."));
        if (!currentStage.canMoveTo(targetStage)) {
            throw stateConflict("수업 단계는 현재 단계의 이전 또는 다음 단계로만 이동할 수 있습니다.");
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
