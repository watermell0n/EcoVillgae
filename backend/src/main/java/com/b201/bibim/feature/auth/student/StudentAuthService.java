package com.b201.bibim.feature.auth.student;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.auth.student.StudentAuthDto.EnterRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.EnterResponse;
import com.b201.bibim.feature.auth.student.StudentAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.RequiredAction;
import com.b201.bibim.feature.auth.student.StudentAuthDto.SetupRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.StudentResponse;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.global.security.AuthSessionService;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.feature.flow.StudentFlowService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentAuthService {

    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final AccountRepository accountRepository;
    private final ActivityEventService activityEventService;
    private final StudentFlowService studentFlowService;

    public StudentAuthService(
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService,
            AccountRepository accountRepository,
            ActivityEventService activityEventService,
            StudentFlowService studentFlowService
    ) {
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.accountRepository = accountRepository;
        this.activityEventService = activityEventService;
        this.studentFlowService = studentFlowService;
    }

    @Transactional(readOnly = true)
    public EnterResponse enter(EnterRequest request) {
        StudentEntity student = findForEntry(request);
        RoomEntity room = findRoom(student.getRoomId());

        RequiredAction requiredAction;
        if (isArchived(room)) {
            requiredAction = RequiredAction.ROOM_CLOSED;
        } else if ("INVITED".equals(student.getStatus())) {
            requiredAction = isCompleted(room)
                    ? RequiredAction.ROOM_CLOSED
                    : RequiredAction.SETUP;
        } else if (("ACTIVE".equals(student.getStatus()) || "ABSENT".equals(student.getStatus()))
                && student.getPasswordHash() == null) {
            requiredAction = RequiredAction.RESET_PASSWORD;
        } else if ("ACTIVE".equals(student.getStatus()) || "ABSENT".equals(student.getStatus())) {
            requiredAction = RequiredAction.LOGIN;
        } else {
            throw invalidParticipationCode();
        }

        return new EnterResponse(
                student.getParticipationCode(),
                room.getId(),
                room.getName(),
                student.getParticipantNumber(),
                student.getNickname(),
                requiredAction
        );
    }

    @Transactional
    public LoginResult setup(SetupRequest request) {
        StudentEntity student = findByCodeForUpdate(request.participationCode());
        boolean firstSetup = "INVITED".equals(student.getStatus());
        boolean passwordReset = ("ACTIVE".equals(student.getStatus()) || "ABSENT".equals(student.getStatus()))
                && student.getPasswordHash() == null;
        if (!firstSetup && !passwordReset) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PARTICIPATION_CODE_ALREADY_REGISTERED",
                    "이미 설정이 완료된 참여 코드입니다. 로그인해 주세요."
            );
        }

        RoomEntity room = findRoom(student.getRoomId());
        if (isArchived(room) || (firstSetup && isCompleted(room))) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_CLOSED", "이미 종료된 수업입니다.");
        }

        if (firstSetup) {
            if (request.nickname() == null || request.nickname().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NICKNAME_REQUIRED", "닉네임을 입력해 주세요.");
            }
            student.completeSetup(request.nickname().trim(), passwordEncoder.encode(request.password()));
            studentFlowService.initialize(student, room);
        } else {
            student.completePasswordReset(passwordEncoder.encode(request.password()));
        }
        String token = authSessionService.createStudentSession(student);
        recordAuthEvent(student, room, firstSetup ? "STUDENT_SETUP_COMPLETED" : "STUDENT_PASSWORD_RESET_COMPLETED");
        return new LoginResult(toResponse(student, room), token);
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        StudentEntity student = findByCodeForUpdate(request.participationCode());
        if (!("ACTIVE".equals(student.getStatus()) || "ABSENT".equals(student.getStatus()))
                || student.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), student.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "참여 코드 또는 비밀번호가 올바르지 않습니다."
            );
        }

        RoomEntity room = findRoom(student.getRoomId());
        if (isArchived(room)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_CLOSED", "이미 종료된 수업입니다.");
        }

        if ("ABSENT".equals(student.getStatus())) {
            student.reactivateFromAbsence();
        } else {
            student.recordLogin();
        }
        String token = authSessionService.createStudentSession(student);
        recordAuthEvent(student, room, "STUDENT_LOGIN");
        return new LoginResult(toResponse(student, room), token);
    }

    @Transactional(readOnly = true)
    public StudentResponse me(UUID studentId) {
        StudentEntity student = studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
        return toResponse(student, findRoom(student.getRoomId()));
    }

    private StudentEntity findByCode(String code) {
        return studentRepository.findByParticipationCode(normalizeCode(code))
                .orElseThrow(this::invalidParticipationCode);
    }

    private StudentEntity findForEntry(EnterRequest request) {
        if (request.roomId() != null && request.participantNumber() != null) {
            return studentRepository
                    .findByRoomIdAndParticipantNumber(request.roomId(), request.participantNumber())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "INVALID_STUDENT_NUMBER",
                            "이 교실에서 해당 학생 번호를 찾지 못했어요. 자기 번호를 다시 확인해 주세요."
                    ));
        }
        if (request.participationCode() == null || request.participationCode().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "STUDENT_ENTRY_REQUIRED",
                    "학생 번호를 입력해 주세요. 선생님이 알려 준 교실 주소로 접속하면 자기 번호만 입력할 수 있어요."
            );
        }
        return findByCode(request.participationCode());
    }

    private StudentEntity findByCodeForUpdate(String code) {
        return studentRepository.findByParticipationCodeForUpdate(normalizeCode(code))
                .orElseThrow(this::invalidParticipationCode);
    }

    private RoomEntity findRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ROOM_NOT_FOUND",
                        "수업 방을 찾을 수 없습니다."
                ));
    }

    private StudentResponse toResponse(StudentEntity student, RoomEntity room) {
        return new StudentResponse(
                student.getId(),
                room.getId(),
                room.getName(),
                student.getParticipantNumber(),
                student.getNickname(),
                room.getStatus(),
                room.getCurrentDay(),
                room.getCurrentStage(),
                room.getMode(),
                walletBalance(room.getId(), student.getId()),
                room.getSyncVersion(),
                "CONNECTED"
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
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

    private boolean isClosed(RoomEntity room) {
        return isCompleted(room) || isArchived(room);
    }

    private boolean isCompleted(RoomEntity room) {
        return "COMPLETED".equals(room.getStatus());
    }

    private boolean isArchived(RoomEntity room) {
        return "ARCHIVED".equals(room.getStatus());
    }

    private void recordAuthEvent(StudentEntity student, RoomEntity room, String eventType) {
        activityEventService.record(
                room.getId(),
                student.getId(),
                "STUDENT",
                student.getId(),
                eventType,
                room.getCurrentStage(),
                null,
                "AUTH",
                Map.of("participantNumber", student.getParticipantNumber())
        );
    }

    private ApiException invalidParticipationCode() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "INVALID_PARTICIPATION_CODE",
                "학생 번호 또는 입장 코드를 다시 확인해 주세요."
        );
    }

    public record LoginResult(StudentResponse response, String token) {
    }
}
