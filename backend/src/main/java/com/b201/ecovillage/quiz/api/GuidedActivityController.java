package com.b201.ecovillage.quiz.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.quiz.application.GuidedActivityCommandService;
import com.b201.ecovillage.quiz.application.GuidedActivityFinishCommand;
import com.b201.ecovillage.quiz.application.GuidedActivityQueryService;
import com.b201.ecovillage.quiz.application.GuidedActivityResult;
import com.b201.ecovillage.quiz.application.GuidedActivityStartCommand;
import com.b201.ecovillage.quiz.application.GuidedActivityView;
import com.b201.ecovillage.quiz.application.ImageLessonService;
import com.b201.ecovillage.quiz.application.PresentationView;

@RestController
@RequestMapping("/classrooms/{classroomId}/guided-activities")
public class GuidedActivityController {

    private final GuidedActivityCommandService commandService;
    private final GuidedActivityQueryService queryService;
    private final ImageLessonService imageLessonService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public GuidedActivityController(
        GuidedActivityCommandService commandService,
        GuidedActivityQueryService queryService,
        ImageLessonService imageLessonService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.imageLessonService = imageLessonService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/image-lessons")
    public ApiResponse<GuidedActivityResponse> startImageLesson(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody ImageLessonStartRequest request,
        Principal principal
    ) {
        UUID correlation = resolve(correlationId);
        GuidedActivityResult result = imageLessonService.start(
            subject(principal),
            classroomId,
            requestId,
            correlation,
            request.resolvedSlides().stream()
                .map(slide -> new ImageLessonService.Slide(slide.title(), slide.imageUrl()))
                .toList()
        );
        return new ApiResponse<>(response(result), meta(requestId, correlation));
    }

    /**
     * 수업 중에 보여 주는 자료를 넘긴다. 커서만 옮기고 자료는 바꾸지 않는다.
     * 학생 화면은 활동 조회를 폴링하고 있어 넘긴 결과가 다음 폴링에 따라온다.
     */
    @PostMapping("/{activityId}/presentation-commands")
    public ApiResponse<PresentationResponse> movePresentation(
        @PathVariable UUID classroomId,
        @PathVariable UUID activityId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody PresentationMoveRequest request,
        Principal principal
    ) {
        UUID correlation = resolve(correlationId);
        PresentationView view = imageLessonService.move(
            subject(principal),
            classroomId,
            activityId,
            request.command(),
            request.page()
        );
        return new ApiResponse<>(
            new PresentationResponse(
                view.activityId(),
                view.status(),
                view.stateVersion(),
                view.page(),
                view.totalPages(),
                view.title(),
                view.imageUrl(),
                view.hasPrevious(),
                view.hasNext()
            ),
            meta(requestId, correlation)
        );
    }

    @PostMapping("/{activityId}/start-commands")
    public ApiResponse<GuidedActivityResponse> start(
        @PathVariable UUID classroomId,
        @PathVariable UUID activityId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody GuidedActivityStartRequest request,
        Principal principal
    ) {
        UUID correlation = resolve(correlationId);
        GuidedActivityResult result = commandService.start(
            new GuidedActivityStartCommand(
                subject(principal),
                classroomId,
                activityId,
                requestId,
                correlation,
                request.expectedStateVersion(),
                request.participantIds(),
                request.publicFields()
            )
        );
        return new ApiResponse<>(
            response(result),
            meta(requestId, correlation)
        );
    }

    @PostMapping("/{activityId}/finish-commands")
    public ApiResponse<GuidedActivityResponse> finish(
        @PathVariable UUID classroomId,
        @PathVariable UUID activityId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody GuidedActivityFinishRequest request,
        Principal principal
    ) {
        UUID correlation = resolve(correlationId);
        GuidedActivityResult result = commandService.finish(
            new GuidedActivityFinishCommand(
                subject(principal),
                classroomId,
                activityId,
                requestId,
                correlation,
                request.expectedStateVersion(),
                request.command(),
                request.reason()
            )
        );
        return new ApiResponse<>(
            response(result),
            meta(requestId, correlation)
        );
    }

    @GetMapping("/{activityId}")
    public ApiResponse<GuidedActivityViewResponse> get(
        @PathVariable UUID classroomId,
        @PathVariable UUID activityId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = resolve(correlationId);
        GuidedActivityView result = queryService.get(
            subject(principal),
            classroomId,
            activityId
        );
        return new ApiResponse<>(
            new GuidedActivityViewResponse(
                result.activityId(),
                result.status(),
                result.stateVersion(),
                result.participantCount(),
                result.activityType(),
                result.presentationTitle(),
                result.presentationImageUrl(),
                result.presentationPage(),
                result.presentationTotalPages()
            ),
            meta(requestId, correlation)
        );
    }

    private GuidedActivityResponse response(GuidedActivityResult result) {
        return new GuidedActivityResponse(
            result.activityId(),
            result.status(),
            result.stateVersion()
        );
    }

    private ApiMeta meta(UUID requestId, UUID correlationId) {
        return new ApiMeta(requestId, correlationId, clock.instant());
    }

    private UUID resolve(UUID correlationId) {
        return correlationId == null ? uuidGenerator.generate() : correlationId;
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
