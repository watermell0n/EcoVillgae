package com.b201.ecovillage.quiz.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class ImageLessonService {

    private final UserAccountRepository userRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ActivitySessionRepository activityRepository;
    private final GuidedActivityCommandService commandService;
    private final Clock clock;

    public ImageLessonService(
        UserAccountRepository userRepository,
        ClassroomStudentRepository studentRepository,
        ActivitySessionRepository activityRepository,
        GuidedActivityCommandService commandService,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.activityRepository = activityRepository;
        this.commandService = commandService;
        this.clock = clock;
    }

    @Transactional
    public GuidedActivityResult start(
        String teacherSubject,
        UUID classroomId,
        UUID requestId,
        UUID correlationId,
        String title,
        String imageUrl
    ) {
        UUID teacherId = userRepository.findByExternalSubject(teacherSubject)
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "유효한 교사 로그인이 필요합니다."
            ))
            .getId();
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(classroomId, requestId)
            .orElseGet(() -> {
                ActivitySession created = ActivitySession.ready(
                    requestId,
                    classroomId,
                    "ECONOMIC_CONCEPT_IMAGE",
                    teacherId,
                    true,
                    clock.instant()
                );
                created.configurePresentationImage(title, imageUrl, clock.instant());
                return activityRepository.saveAndFlush(created);
            });
        List<UUID> participants = studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            )
            .stream()
            .map(student -> student.getId())
            .toList();
        if (participants.isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PARTICIPANT_INVALID",
                "수업을 시작할 활성 학생이 없습니다."
            );
        }
        return commandService.start(new GuidedActivityStartCommand(
            teacherSubject,
            classroomId,
            activity.getId(),
            requestId,
            correlationId,
            activity.getStateVersion(),
            participants,
            List.of("scene")
        ));
    }
}
