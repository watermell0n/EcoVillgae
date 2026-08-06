package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class ClassroomArchiveService {

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ActivitySessionRepository activityRepository;
    private final Clock clock;

    public ClassroomArchiveService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ActivitySessionRepository activityRepository,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.activityRepository = activityRepository;
        this.clock = clock;
    }

    @Transactional
    public ClassroomArchiveResult archive(
        String teacherSubject,
        UUID classroomId,
        long expectedStateVersion
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.ARCHIVE_CLASSROOM,
            null
        );
        Classroom classroom = classroomRepository.findForUpdateById(classroomId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "교실을 찾을 수 없습니다."
            ));
        if (classroom.getStatus() == ClassroomStatus.ARCHIVED) {
            return result(classroom);
        }
        if (classroom.getStateVersion() != expectedStateVersion) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "교실 상태가 변경되었습니다. 목록을 새로고침한 뒤 다시 시도해 주세요."
            );
        }
        if (activityRepository.existsByClassroomIdAndStatusIn(
            classroomId,
            List.of("LIVE", "FINALIZING")
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "ACTIVE_ACTIVITY_EXISTS",
                "진행 중인 수업이나 퀴즈를 먼저 종료해 주세요."
            );
        }
        classroom.changeStatus(ClassroomStatus.ARCHIVED, clock.instant());
        classroomRepository.saveAndFlush(classroom);
        return result(classroom);
    }

    private static ClassroomArchiveResult result(Classroom classroom) {
        return new ClassroomArchiveResult(
            classroom.getId(),
            classroom.getStatus().name(),
            classroom.getStateVersion()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
