package com.b201.ecovillage.classroom.api;

import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.AutonomousScheduleCommand;
import com.b201.ecovillage.classroom.application.ClassroomConfigurationResult;
import com.b201.ecovillage.classroom.application.ClassroomConfigurationService;
import com.b201.ecovillage.classroom.application.ClassroomCreationService;
import com.b201.ecovillage.classroom.application.CreateClassroomCommand;
import com.b201.ecovillage.classroom.application.CreateClassroomResult;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms")
public class ClassroomController {

    private final ClassroomCreationService creationService;
    private final ClassroomConfigurationService configurationService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ClassroomController(
        ClassroomCreationService creationService,
        ClassroomConfigurationService configurationService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.creationService = creationService;
        this.configurationService = configurationService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateClassroomResponse>> create(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody CreateClassroomRequest request,
        Principal principal
    ) {
        List<AutonomousScheduleCommand> schedules = request.autonomousSchedules() == null
            ? List.of()
            : request.autonomousSchedules().stream()
                .map(schedule -> new AutonomousScheduleCommand(
                    schedule.dayOfWeek(),
                    schedule.localStartTime(),
                    schedule.localEndTime(),
                    schedule.moduleCodes()
                ))
                .toList();
        CreateClassroomResult result = creationService.create(
            new CreateClassroomCommand(
                principal == null ? null : principal.getName(),
                request.schoolId(),
                request.currencyName(),
                request.grade(),
                request.courseSize(),
                request.startsOn(),
                request.endsOn(),
                request.studentCount(),
                request.timezone(),
                request.salaryPolicy(),
                request.moduleCodes(),
                schedules,
                request.expectedStateVersion()
            )
        );
        CreateClassroomResponse data = new CreateClassroomResponse(
            result.classroomId(),
            result.classroomCode(),
            result.currencyName(),
            result.status(),
            result.stateVersion()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ApiResponse<>(data, meta(requestId, correlationId)));
    }

    @GetMapping("/{classroomId}/configuration")
    public ApiResponse<ClassroomConfigurationResult> configuration(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        ClassroomConfigurationResult data = configurationService.get(
            principal == null ? null : principal.getName(),
            classroomId
        );
        return new ApiResponse<>(data, meta(uuidGenerator.generate(), correlationId));
    }

    private ApiMeta meta(UUID requestId, UUID correlationId) {
        return new ApiMeta(
            requestId,
            correlationId == null ? uuidGenerator.generate() : correlationId,
            clock.instant()
        );
    }
}
