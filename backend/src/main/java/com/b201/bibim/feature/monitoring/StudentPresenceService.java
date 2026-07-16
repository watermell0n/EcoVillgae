package com.b201.bibim.feature.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.b201.bibim.domain.monitoring.StudentPresenceEntity;
import com.b201.bibim.domain.monitoring.StudentPresenceRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.monitoring.StudentPresenceDto.HeartbeatRequest;
import com.b201.bibim.feature.monitoring.StudentPresenceDto.HeartbeatResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPresenceService {

    private static final Duration RECONNECT_THRESHOLD = Duration.ofSeconds(30);

    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final StudentPresenceRepository presenceRepository;
    private final ActivityEventService activityEventService;

    public StudentPresenceService(
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            StudentPresenceRepository presenceRepository,
            ActivityEventService activityEventService
    ) {
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.presenceRepository = presenceRepository;
        this.activityEventService = activityEventService;
    }

    @Transactional
    public HeartbeatResponse heartbeat(UUID studentId, HeartbeatRequest request) {
        StudentEntity student = studentRepository.findByIdForUpdate(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ROOM_NOT_FOUND",
                        "수업 방을 찾을 수 없습니다."
                ));
        Instant now = Instant.now();
        var existing = presenceRepository.findByStudentIdForUpdate(studentId);
        boolean firstSeen = existing.isEmpty();
        StudentPresenceEntity presence = existing.orElseGet(() -> StudentPresenceEntity.firstSeen(
                room.getId(),
                studentId,
                request.reportedStage(),
                normalizeNullable(request.currentModule()),
                request.currentPage(),
                request.clientSyncVersion(),
                request.visibility(),
                now
        ));
        String previousPage = firstSeen ? null : presence.getCurrentPage();
        boolean reconnected = !firstSeen && (
                presence.getForcedOfflineAt() != null
                        || presence.getLastSeenAt().isBefore(now.minus(RECONNECT_THRESHOLD))
        );

        if (!firstSeen) {
            presence.update(
                    request.reportedStage(),
                    normalizeNullable(request.currentModule()),
                    request.currentPage(),
                    request.clientSyncVersion(),
                    request.visibility(),
                    now,
                    reconnected
            );
        }
        presenceRepository.save(presence);

        if (firstSeen) {
            recordPresenceEvent(room, student, presence, "STUDENT_CONNECTED", Map.of());
        } else if (reconnected) {
            recordPresenceEvent(room, student, presence, "STUDENT_RECONNECTED", Map.of());
        }
        if (firstSeen || !request.currentPage().equals(previousPage)) {
            recordPresenceEvent(
                    room,
                    student,
                    presence,
                    "PAGE_ENTERED",
                    Map.of("clientSyncVersion", request.clientSyncVersion())
            );
        }

        return new HeartbeatResponse(
                studentId,
                "ONLINE",
                room.getCurrentStage(),
                room.getSyncVersion(),
                request.clientSyncVersion() == room.getSyncVersion(),
                now
        );
    }

    private void recordPresenceEvent(
            RoomEntity room,
            StudentEntity student,
            StudentPresenceEntity presence,
            String eventType,
            Map<String, Object> metadata
    ) {
        activityEventService.record(
                room.getId(),
                student.getId(),
                "STUDENT",
                student.getId(),
                eventType,
                presence.getReportedStage(),
                presence.getCurrentModule(),
                presence.getCurrentPage(),
                metadata
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
