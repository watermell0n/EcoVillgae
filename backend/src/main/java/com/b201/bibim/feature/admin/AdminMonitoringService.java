package com.b201.bibim.feature.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.auth.AuthSessionRepository;
import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.monitoring.ActivityEventEntity;
import com.b201.bibim.domain.monitoring.ActivityEventRepository;
import com.b201.bibim.domain.monitoring.StudentPresenceEntity;
import com.b201.bibim.domain.monitoring.StudentPresenceRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.student.StudentFlowStateEntity;
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentActivityProgressRepository;
import com.b201.bibim.feature.admin.AdminMonitoringDto.ActivityResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomMonitoringResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomStudentsResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.SessionTerminationResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.StudentMonitoringResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.WalletAdjustmentResponse;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMonitoringService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofSeconds(10);
    private static final Duration OFFLINE_THRESHOLD = Duration.ofSeconds(30);

    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final StudentPresenceRepository presenceRepository;
    private final ActivityEventRepository activityEventRepository;
    private final ActivityEventService activityEventService;
    private final AccountRepository accountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final StudentFlowStateRepository flowStateRepository;
    private final StudentActivityProgressRepository progressRepository;

    public AdminMonitoringService(
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            StudentPresenceRepository presenceRepository,
            ActivityEventRepository activityEventRepository,
            ActivityEventService activityEventService,
            AccountRepository accountRepository,
            AuthSessionRepository authSessionRepository,
            StudentFlowStateRepository flowStateRepository,
            StudentActivityProgressRepository progressRepository
    ) {
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.presenceRepository = presenceRepository;
        this.activityEventRepository = activityEventRepository;
        this.activityEventService = activityEventService;
        this.accountRepository = accountRepository;
        this.authSessionRepository = authSessionRepository;
        this.flowStateRepository = flowStateRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomMonitoringResponse> rooms() {
        Instant now = Instant.now();
        return roomRepository.findAllByStatusNotOrderByUpdatedAtDesc("ARCHIVED").stream()
                .map(room -> toRoomResponse(room, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomStudentsResponse students(UUID roomId) {
        RoomEntity room = findRoom(roomId);
        Map<UUID, StudentPresenceEntity> presenceByStudent = presenceRepository
                .findAllByRoomId(roomId)
                .stream()
                .collect(Collectors.toMap(StudentPresenceEntity::getStudentId, Function.identity()));
        Instant now = Instant.now();
        List<StudentMonitoringResponse> students = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(roomId)
                .stream()
                .map(student -> toStudentResponse(
                        room,
                        student,
                        presenceByStudent.get(student.getId()),
                        now
                ))
                .toList();
        return new RoomStudentsResponse(
                room.getId(),
                room.getName(),
                room.getCurrentStage(),
                room.getSyncVersion(),
                students
        );
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> activities(UUID roomId, UUID studentId) {
        findStudentInRoom(roomId, studentId);
        return activityEventRepository
                .findAllByRoomIdAndStudentIdOrderByOccurredAtDesc(
                        roomId,
                        studentId,
                        PageRequest.of(0, 100)
                )
                .stream()
                .map(this::toActivityResponse)
                .toList();
    }

    @Transactional
    public SessionTerminationResponse terminateStudentSession(
            UUID adminId,
            UUID roomId,
            UUID studentId,
            String reason
    ) {
        RoomEntity room = findRoom(roomId);
        StudentEntity student = studentRepository.findByIdForUpdate(studentId)
                .filter(item -> item.getRoomId().equals(roomId))
                .orElseThrow(this::studentNotFound);
        Instant now = Instant.now();
        int revokedCount = authSessionRepository.revokeActiveStudentSessions(studentId, now);
        student.forceLogout();
        presenceRepository.findByStudentIdForUpdate(studentId)
                .ifPresent(presence -> presence.forceOffline(now));
        activityEventService.record(
                roomId,
                studentId,
                "ADMIN",
                adminId,
                "STUDENT_SESSION_TERMINATED",
                room.getCurrentStage(),
                null,
                "SESSION_TERMINATED",
                Map.of(
                        "reason", reason.trim(),
                        "revokedSessionCount", revokedCount
                )
        );
        return new SessionTerminationResponse(studentId, revokedCount, now);
    }

    @Transactional
    public WalletAdjustmentResponse adjustWallet(
            UUID adminId,
            UUID roomId,
            UUID studentId,
            long amount,
            String reason
    ) {
        if (amount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "조정할 금액은 0이 아니어야 합니다.");
        }
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다."
        ));
        StudentEntity student = findStudentInRoom(roomId, studentId);
        AccountEntity wallet = accountRepository
                .findStudentAccountForUpdate(roomId, studentId, "STUDENT_WALLET")
                .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.studentWallet(roomId, studentId)));
        try {
            wallet.changeBy(amount);
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "차감할 잔액이 부족합니다.");
        }
        room.markEconomicStateChanged();
        Instant adjustedAt = Instant.now();
        activityEventService.record(
                roomId, student.getId(), "ADMIN", adminId, "STUDENT_WALLET_ADJUSTED",
                room.getCurrentStage(), "WALLET", "ADMIN_WALLET_ADJUSTMENT",
                Map.of("amount", amount, "balance", wallet.getBalance(), "reason", reason.trim())
        );
        return new WalletAdjustmentResponse(studentId, wallet.getBalance(), amount, adjustedAt);
    }

    @Transactional
    public RoomMonitoringResponse archiveRoom(UUID roomId) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다."
        ));
        room.archive();
        return toRoomResponse(room, Instant.now());
    }

    private RoomMonitoringResponse toRoomResponse(RoomEntity room, Instant now) {
        Map<UUID, StudentPresenceEntity> presenceByStudent = presenceRepository
                .findAllByRoomId(room.getId())
                .stream()
                .collect(Collectors.toMap(StudentPresenceEntity::getStudentId, Function.identity()));
        List<String> statuses = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(room.getId())
                .stream()
                .map(student -> connectionStatus(presenceByStudent.get(student.getId()), now))
                .toList();
        return new RoomMonitoringResponse(
                room.getId(),
                room.getName(),
                room.getStatus(),
                room.getCurrentDay(),
                room.getCurrentStage(),
                room.getVersion(),
                room.getSyncVersion(),
                room.getExpectedStudentCount(),
                count(statuses, "ONLINE"),
                count(statuses, "UNSTABLE"),
                count(statuses, "AWAY"),
                count(statuses, "OFFLINE"),
                count(statuses, "NEVER_CONNECTED"),
                room.getUpdatedAt()
        );
    }

    private StudentMonitoringResponse toStudentResponse(
            RoomEntity room,
            StudentEntity student,
            StudentPresenceEntity presence,
            Instant now
    ) {
        long clientSyncVersion = presence == null ? 0 : presence.getClientSyncVersion();
        StudentFlowStateEntity flow = flowStateRepository.findByStudentId(student.getId())
                .orElse(null);
        String syncStatus = flow != null && !"LIVE".equals(flow.getFlowMode())
                ? "PERSONAL_PROGRESS"
                : presence == null
                ? "UNKNOWN"
                : clientSyncVersion == room.getSyncVersion() ? "SYNCED" : "DELAYED";
        long completedActivityCount = progressRepository.findAllByStudentId(student.getId())
                .stream()
                .filter(progress -> List.of("COMPLETED", "WAIVED", "RESULT_ONLY")
                        .contains(progress.getStatus()))
                .count();
        int totalActivityCount = flow == null ? 0
                : progressRepository.findAllByStudentId(student.getId()).size();
        return new StudentMonitoringResponse(
                student.getId(),
                student.getParticipantNumber(),
                student.getParticipationCode(),
                student.getNickname(),
                student.getStatus(),
                connectionStatus(presence, now),
                presence == null ? null : presence.getCurrentPage(),
                presence == null ? null : presence.getCurrentModule(),
                presence == null ? null : presence.getReportedStage(),
                room.getCurrentStage(),
                clientSyncVersion,
                room.getSyncVersion(),
                syncStatus,
                flow == null ? "NOT_STARTED" : flow.getFlowMode(),
                flow == null ? room.getCurrentStage() : flow.getEffectiveStage(),
                Math.toIntExact(completedActivityCount),
                totalActivityCount,
                flow == null ? 10_000 : flow.getRewardRateBps(),
                walletBalance(room.getId(), student.getId()),
                presence == null ? null : presence.getFirstSeenAt(),
                presence == null ? null : presence.getLastSeenAt(),
                presence == null ? null : presence.getReconnectedAt()
        );
    }

    private ActivityResponse toActivityResponse(ActivityEventEntity event) {
        return new ActivityResponse(
                event.getId(),
                event.getActorType(),
                event.getEventType(),
                event.getStage(),
                event.getModule(),
                event.getPage(),
                event.getRequestId(),
                event.getMetadata(),
                event.getOccurredAt()
        );
    }

    private String connectionStatus(StudentPresenceEntity presence, Instant now) {
        if (presence == null) {
            return "NEVER_CONNECTED";
        }
        if (presence.getForcedOfflineAt() != null) {
            return "OFFLINE";
        }
        Duration elapsed = Duration.between(presence.getLastSeenAt(), now);
        if (elapsed.compareTo(OFFLINE_THRESHOLD) > 0) {
            return "OFFLINE";
        }
        if ("HIDDEN".equals(presence.getVisibility())) {
            return "AWAY";
        }
        if (elapsed.compareTo(ONLINE_THRESHOLD) <= 0) {
            return "ONLINE";
        }
        return "UNSTABLE";
    }

    private int count(List<String> statuses, String target) {
        return Math.toIntExact(statuses.stream().filter(target::equals).count());
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository
                .findByRoomIdAndStudentIdAndAccountType(roomId, studentId, "STUDENT_WALLET")
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private RoomEntity findRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "수업 방을 찾을 수 없습니다."
        ));
    }

    private StudentEntity findStudentInRoom(UUID roomId, UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> student.getRoomId().equals(roomId))
                .orElseThrow(this::studentNotFound);
    }

    private ApiException studentNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "STUDENT_NOT_FOUND",
                "학생을 찾을 수 없습니다."
        );
    }
}
