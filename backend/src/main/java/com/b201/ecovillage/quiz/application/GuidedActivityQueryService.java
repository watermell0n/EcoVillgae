package com.b201.ecovillage.quiz.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class GuidedActivityQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;

    public GuidedActivityQueryService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public GuidedActivityView get(
        String subject,
        UUID classroomId,
        UUID activityId
    ) {
        UUID studentId = userRepository.findByExternalSubject(subject).isPresent()
            ? null
            : parseStudentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_GUIDED_ACTIVITY,
            studentId
        );
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(classroomId, activityId)
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The guided activity could not be found."
            ));
        if (
            studentId != null
                && !participantRepository
                    .existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
                        classroomId,
                        activityId,
                        studentId
                    )
        ) {
            throw new ClassroomOperationException(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "The student is not a participant in this activity."
            );
        }
        return new GuidedActivityView(
            activity.getId(),
            activity.getStatus(),
            activity.getStateVersion(),
            participantRepository.countByClassroomIdAndActivitySessionId(
                classroomId,
                activityId
            ),
            activity.getActivityType(),
            activity.getPresentationTitle(),
            activity.getPresentationImageUrl()
        );
    }

    private static UUID parseStudentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (Exception exception) {
            throw new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid session is required."
            );
        }
    }
}
