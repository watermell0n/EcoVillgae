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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.AutonomousScheduleCommand;
import com.b201.ecovillage.classroom.application.AccessModeCommand;
import com.b201.ecovillage.classroom.application.AccessModeCommandService;
import com.b201.ecovillage.classroom.application.AccessModeResult;
import com.b201.ecovillage.classroom.application.ClassroomConfigurationResult;
import com.b201.ecovillage.classroom.application.ClassroomConfigurationService;
import com.b201.ecovillage.classroom.application.ClassroomCreationService;
import com.b201.ecovillage.classroom.application.ClassroomRosterService;
import com.b201.ecovillage.classroom.application.ModuleActivationCommand;
import com.b201.ecovillage.classroom.application.ModuleActivationResult;
import com.b201.ecovillage.classroom.application.ModuleActivationService;
import com.b201.ecovillage.classroom.application.CreateClassroomCommand;
import com.b201.ecovillage.classroom.application.CreateClassroomResult;
import com.b201.ecovillage.classroom.application.SalaryRoleCommand;
import com.b201.ecovillage.classroom.application.StudentRosterCommand;
import com.b201.ecovillage.classroom.application.UpdateRosterCommand;
import com.b201.ecovillage.classroom.application.UpdateRosterResult;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms")
public class ClassroomController {

    private final ClassroomCreationService creationService;
    private final ClassroomConfigurationService configurationService;
    private final ClassroomRosterService rosterService;
    private final AccessModeCommandService accessModeCommandService;
    private final ModuleActivationService moduleActivationService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ClassroomController(
        ClassroomCreationService creationService,
        ClassroomConfigurationService configurationService,
        ClassroomRosterService rosterService,
        AccessModeCommandService accessModeCommandService,
        ModuleActivationService moduleActivationService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.creationService = creationService;
        this.configurationService = configurationService;
        this.rosterService = rosterService;
        this.accessModeCommandService = accessModeCommandService;
        this.moduleActivationService = moduleActivationService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/{classroomId}/modules/{moduleCode}/activation-commands")
    public ApiResponse<ModuleActivationResponse> activateModule(
        @PathVariable UUID classroomId,
        @PathVariable String moduleCode,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody ModuleActivationCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        ModuleActivationResult result = moduleActivationService.activate(
            new ModuleActivationCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                moduleCode,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.confirmPrerequisites(),
                request.initialPolicyValues(),
                request.recommendationActionIds()
            )
        );
        return new ApiResponse<>(
            new ModuleActivationResponse(
                result.moduleCode(),
                result.status(),
                result.stateVersion(),
                result.policyArea(),
                result.policyVersion(),
                result.policyVersionId(),
                result.ruleVersion(),
                result.replayed()
            ),
            meta(requestId, resolvedCorrelationId)
        );
    }

    @PostMapping("/{classroomId}/access-mode-commands")
    public ApiResponse<AccessModeResponse> changeAccessMode(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody AccessModeCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        AccessModeResult result = accessModeCommandService.execute(
            new AccessModeCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.command(),
                request.durationMinutes()
            )
        );
        return new ApiResponse<>(
            new AccessModeResponse(
                result.mode().name(),
                result.source(),
                result.expiresAt(),
                result.moduleCodes(),
                result.stateVersion()
            ),
            meta(requestId, resolvedCorrelationId)
        );
    }

    @PutMapping("/{classroomId}/roster-and-salaries")
    public ApiResponse<RosterAndSalariesResponse> updateRoster(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody RosterAndSalariesRequest request,
        Principal principal
    ) {
        List<StudentRosterCommand> students = request.students().stream()
            .map(student -> new StudentRosterCommand(
                student.studentId(),
                student.attendanceNumber(),
                student.nickname(),
                student.roleCode()
            ))
            .toList();
        List<SalaryRoleCommand> salaryRoles = (request.salaryRoles() == null
            ? List.<SalaryRoleRequest>of()
            : request.salaryRoles()).stream()
            .map(role -> new SalaryRoleCommand(
                role.roleCode(),
                role.name(),
                role.grossAmount(),
                role.deductions(),
                role.sortOrder()
            ))
            .toList();
        UpdateRosterResult result = rosterService.update(
            new UpdateRosterCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                students,
                salaryRoles,
                request.expectedPolicyVersion(),
                request.expectedStateVersion()
            )
        );
        RosterAndSalariesResponse data = new RosterAndSalariesResponse(
            result.classroomId(),
            result.studentCount(),
            result.salaryRoleCount(),
            result.stateVersion()
        );
        return new ApiResponse<>(data, meta(requestId, correlationId));
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
