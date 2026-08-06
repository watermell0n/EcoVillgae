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
import com.b201.ecovillage.quiz.repository.ActivityPresentationSlideRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class GuidedActivityQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final ActivityPresentationSlideRepository slideRepository;

    public GuidedActivityQueryService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        ActivityPresentationSlideRepository slideRepository
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.slideRepository = slideRepository;
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
        // 조회는 잠그지 않습니다. 쓰기 잠금을 거는 finder는 읽기 전용 트랜잭션에서
        // PostgreSQL이 거절해 이 API가 늘 500으로 실패했습니다.
        ActivitySession activity = activityRepository
            .findOneByClassroomIdAndId(classroomId, activityId)
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
        // 자료가 한 장인 기존 수업은 슬라이드 행이 없어 총 장수가 0으로 나옵니다.
        // 화면이 "1 / 0"을 보지 않도록 최소 1로 맞춥니다.
        int totalPages = Math.max(slideRepository.countByActivitySessionId(activityId), 1);
        int page = activity.getPresentationCursor() == null
            ? 1
            : Math.min(activity.getPresentationCursor(), totalPages);
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
            activity.getPresentationImageUrl(),
            page,
            totalPages
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
