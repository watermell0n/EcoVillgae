package com.b201.ecovillage.quiz.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.quiz.domain.ActivityPresentationSlide;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityPresentationSlideRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

/**
 * 선생님 수업 모듈을 열고, 자료를 순서대로 넘긴다.
 *
 * <p>자료는 시작할 때 한 번에 담고 수업 중에는 커서만 옮긴다. 넘길 때마다 자료를 다시
 * 받으면 교사 화면과 학생 화면이 다른 장을 보게 될 수 있다.
 *
 * <p>커서가 가리키는 장은 activity_sessions에 사본으로 남겨 활동 조회가 항상 현재 장을
 * 돌려주게 한다. 넘길 때는 WS-ACT-001 채널로 activity.presentation.changed를 발행해
 * 학생 화면이 폴링 주기를 기다리지 않고 즉시 다시 읽게 한다. 폴링은 연결이 끊겼을 때를
 * 위한 안전망으로만 남는다.
 */
@Service
public class ImageLessonService {

    /** 한 수업에 담을 수 있는 자료 수. 가장 많은 회차(6장)에 여유를 둔 값이다. */
    static final int MAX_SLIDES = 60;

    private final UserAccountRepository userRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityPresentationSlideRepository slideRepository;
    private final ClassroomAuthorizationService authorizationService;
    private final GuidedActivityCommandService commandService;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ImageLessonService(
        UserAccountRepository userRepository,
        ClassroomStudentRepository studentRepository,
        ActivitySessionRepository activityRepository,
        ActivityPresentationSlideRepository slideRepository,
        ClassroomAuthorizationService authorizationService,
        GuidedActivityCommandService commandService,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.activityRepository = activityRepository;
        this.slideRepository = slideRepository;
        this.authorizationService = authorizationService;
        this.commandService = commandService;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    /**
     * 자료 목록을 담아 수업을 시작한다. 첫 장이 곧바로 학생 화면에 걸린다.
     *
     * @param slides 순서대로 보여 줄 자료. 한 장 띄우기도 1장짜리 목록으로 들어온다.
     */
    @Transactional
    public GuidedActivityResult start(
        String teacherSubject,
        UUID classroomId,
        UUID requestId,
        UUID correlationId,
        List<Slide> slides
    ) {
        if (slides == null || slides.isEmpty()) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "보여 줄 수업 자료가 없습니다."
            );
        }
        if (slides.size() > MAX_SLIDES) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "한 수업에 담을 수 있는 자료는 " + MAX_SLIDES + "장까지입니다."
            );
        }
        UUID teacherId = userRepository.findByExternalSubject(teacherSubject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "유효한 교사 로그인이 필요합니다."
            ))
            .getId();
        Instant now = clock.instant();
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(classroomId, requestId)
            .orElseGet(() -> activityRepository.saveAndFlush(ActivitySession.ready(
                requestId,
                classroomId,
                ActivitySession.PRESENTATION_TYPE,
                teacherId,
                true,
                now
            )));

        // 같은 Idempotency-Key로 다시 들어와도 자료가 두 번 쌓이지 않게 합니다.
        if (!slideRepository.existsByActivitySessionId(activity.getId())) {
            List<ActivityPresentationSlide> saved = new ArrayList<>();
            int sequence = 1;
            for (Slide slide : slides) {
                saved.add(ActivityPresentationSlide.of(
                    uuidGenerator.generate(),
                    activity.getId(),
                    sequence++,
                    slide.title(),
                    slide.imageUrl(),
                    now
                ));
            }
            slideRepository.saveAll(saved);
            Slide first = slides.get(0);
            activity.movePresentationTo(1, first.title(), first.imageUrl(), now);
            activityRepository.saveAndFlush(activity);
        }

        List<UUID> participants = studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            )
            .stream()
            .map(student -> student.getId())
            .toList();
        if (participants.isEmpty()) {
            throw operation(
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

    /**
     * 보여 주는 장을 옮긴다. 끝에서 더 넘기려 하면 그대로 둔 채 현재 위치를 돌려준다.
     * 마지막 장에서 다음을 눌렀다고 수업이 끝나지는 않는다. 종료는 교사가 따로 누른다.
     */
    @Transactional
    public PresentationView move(
        String teacherSubject,
        UUID classroomId,
        UUID activityId,
        PresentationMoveCommandType command,
        Integer targetPage
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.MANAGE_GUIDED_ACTIVITIES,
            null
        );
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(classroomId, activityId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "수업을 찾을 수 없습니다."
            ));
        int total = slideRepository.countByActivitySessionId(activityId);
        if (total == 0) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PRESENTATION_EMPTY",
                "이 수업에는 넘길 자료가 없습니다."
            );
        }
        int current = activity.getPresentationCursor() == null
            ? 1
            : activity.getPresentationCursor();
        int next = switch (command) {
            case NEXT -> current + 1;
            case PREVIOUS -> current - 1;
            case GOTO -> targetPage == null ? current : targetPage;
        };
        // 범위를 벗어나면 실패로 만들지 않고 양 끝에 붙입니다. 교사가 수업 중에
        // 오류 안내를 보는 것보다 버튼이 반응하지 않는 편이 낫습니다.
        int clamped = Math.min(Math.max(next, 1), total);

        if (clamped != current) {
            ActivityPresentationSlide slide = slideRepository
                .findByActivitySessionIdAndSequenceNumber(activityId, clamped)
                .orElseThrow(() -> operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PRESENTATION_EMPTY",
                    "해당 순서의 자료를 찾을 수 없습니다."
                ));
            activity.movePresentationTo(
                clamped,
                slide.getTitle(),
                slide.getImageUrl(),
                clock.instant()
            );
            activityRepository.saveAndFlush(activity);
            // 학생 화면이 폴링 주기를 기다리지 않고 바로 따라오도록 알린다.
            // 자료 자체는 실어 보내지 않는다. 데이터 URI가 수백 KB라 이벤트로 나르면
            // 스트림이 무거워지고, 학생 앱은 알림을 받고 현재 장을 다시 읽으면 된다.
            eventPublisher.publish(
                classroomId,
                "ACTIVITY",
                activityId,
                "activity.presentation.changed",
                "ACTIVITY",
                activityId,
                activity.getStateVersion(),
                Map.of(
                    "activity_id", activityId.toString(),
                    "page", clamped,
                    "total_pages", total,
                    "title", activity.getPresentationTitle()
                ),
                true
            );
        }
        return new PresentationView(
            activity.getId(),
            activity.getStatus(),
            activity.getStateVersion(),
            clamped,
            total,
            activity.getPresentationTitle(),
            activity.getPresentationImageUrl()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    /** 서비스 경계에서 쓰는 자료 한 장. API DTO를 도메인까지 끌고 들어가지 않기 위한 것이다. */
    public record Slide(String title, String imageUrl) {
    }
}
