package com.b201.bibim.feature.auth.teacher;

import java.util.List;
import java.util.UUID;

import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.user.UserEntity;
import com.b201.bibim.domain.user.UserRepository;
import com.b201.bibim.feature.auth.teacher.TeacherAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.teacher.TeacherAuthDto.RoomSummary;
import com.b201.bibim.feature.auth.teacher.TeacherAuthDto.TeacherResponse;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.global.security.AuthSessionService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherAuthService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    public TeacherAuthService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService
    ) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        UserEntity teacher = userRepository
                .findByLoginIdAndRoleAndStatus(request.loginId().trim(), "TEACHER", "ACTIVE")
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "아이디 또는 비밀번호가 올바르지 않습니다."
                ));

        return new LoginResult(toResponse(teacher), authSessionService.createTeacherSession(teacher));
    }

    @Transactional(readOnly = true)
    public TeacherResponse me(UUID teacherId) {
        UserEntity teacher = userRepository.findById(teacherId)
                .filter(user -> "TEACHER".equals(user.getRole()))
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
        return toResponse(teacher);
    }

    private TeacherResponse toResponse(UserEntity teacher) {
        List<RoomSummary> rooms = roomRepository
                .findAllByTeacherIdAndStatusNotOrderByUpdatedAtDesc(teacher.getId(), "ARCHIVED")
                .stream()
                .map(this::toRoomSummary)
                .toList();
        return new TeacherResponse(teacher.getId(), teacher.getDisplayName(), rooms);
    }

    private RoomSummary toRoomSummary(RoomEntity room) {
        return new RoomSummary(
                room.getId(),
                room.getName(),
                room.getExpectedStudentCount(),
                studentRepository.countByRoomIdAndOnlineTrue(room.getId()),
                room.getStatus(),
                room.getCurrentDay(),
                room.getCurrentStage(),
                room.getMode(),
                room.getSyncVersion(),
                room.getUpdatedAt()
        );
    }

    public record LoginResult(TeacherResponse response, String token) {
    }
}
