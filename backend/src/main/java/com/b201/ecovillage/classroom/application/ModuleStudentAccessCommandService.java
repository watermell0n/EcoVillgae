package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ModuleCatalog;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

/**
 * 학생 이용만 잠그거나 다시 여는 명령입니다.
 *
 * <p>교사가 학급 설정에서 모듈을 끄려는 의도는 대부분 "지금은 학생이 못 들어가게" 하는 것이고,
 * 모듈을 학급에서 걷어내는 것이 아닙니다. stop-commands는 모듈을 STOPPING으로 바꿔 정리를
 * 요구하고 되돌릴 수 없으므로, 되돌릴 수 있는 잠금을 따로 둡니다.
 */
@Service
public class ModuleStudentAccessCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/modules/{module_code}/student-access-commands";

    private final ClassroomMutationSupport mutationSupport;
    private final ClassroomModuleRepository moduleRepository;
    private final RealtimeEventPublisher eventPublisher;
    private final AuditTrailService auditTrailService;
    private final Clock clock;

    public ModuleStudentAccessCommandService(
        ClassroomMutationSupport mutationSupport,
        ClassroomModuleRepository moduleRepository,
        RealtimeEventPublisher eventPublisher,
        AuditTrailService auditTrailService,
        Clock clock
    ) {
        this.mutationSupport = mutationSupport;
        this.moduleRepository = moduleRepository;
        this.eventPublisher = eventPublisher;
        this.auditTrailService = auditTrailService;
        this.clock = clock;
    }

    @Transactional
    public ModuleStudentAccessResult execute(ModuleStudentAccessCommand command) {
        String moduleCode = validate(command);
        UserAccount teacher = mutationSupport.owner(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_MODULES
        );
        String requestHash = mutationSupport.hash(ClassroomMutationSupport.body(
            "classroom_id", command.classroomId(),
            "module_code", moduleCode,
            "command", command.command().name(),
            "expected_state_version", command.expectedStateVersion()
        ));
        Map<String, Object> replay = mutationSupport.replay(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replayResult(replay);
        }

        ClassroomModule module = moduleRepository
            .findForUpdateByClassroomIdAndModuleCode(command.classroomId(), moduleCode)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "이 학급에서 해당 모듈을 찾을 수 없습니다."
            ));
        if (!"ACTIVE".equals(module.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                "사용 중인 모듈만 학생 이용을 잠그거나 열 수 있습니다."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != module.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "모듈 상태가 변경되었습니다."
            );
        }

        boolean suspend = command.command() == ModuleStudentAccessCommandType.SUSPEND;
        boolean before = module.isStudentAccessSuspended();
        Instant now = clock.instant();
        module.changeStudentAccess(suspend, now);
        try {
            moduleRepository.saveAndFlush(module);
        } catch (
            DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "모듈 상태가 변경되어 잠금을 적용하지 못했습니다."
            );
        }

        ModuleStudentAccessResult result = new ModuleStudentAccessResult(
            module.getId(),
            module.getModuleCode(),
            module.getStatus(),
            module.isStudentAccessSuspended(),
            module.getStateVersion(),
            false
        );
        eventPublisher.publish(
            command.classroomId(),
            "CLASSROOM_LIFECYCLE",
            null,
            "classroom.module.changed",
            "CLASSROOM_MODULE",
            module.getId(),
            module.getStateVersion(),
            Map.of(
                "module_id", module.getId().toString(),
                "module_code", module.getModuleCode(),
                "status", module.getStatus(),
                "student_access_suspended", result.studentAccessSuspended(),
                "state_version", module.getStateVersion()
            ),
            true
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            suspend ? "MODULE_STUDENT_ACCESS_SUSPENDED" : "MODULE_STUDENT_ACCESS_RESUMED",
            "CLASSROOM_MODULE",
            module.getId(),
            command.requestId(),
            command.correlationId(),
            Map.of("student_access_suspended", before),
            ClassroomMutationSupport.body(
                "student_access_suspended", result.studentAccessSuspended(),
                "state_version", result.stateVersion(),
                "reason", command.reason() == null ? "" : command.reason().trim()
            ),
            "MODULE",
            suspend
                ? moduleCode + " 모듈의 학생 이용을 잠갔습니다."
                : moduleCode + " 모듈의 학생 이용을 다시 열었습니다."
        );
        mutationSupport.remember(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash,
            resultBody(result)
        );
        return result;
    }

    private static String validate(ModuleStudentAccessCommand command) {
        if (
            command == null || command.classroomId() == null || command.requestId() == null
                || command.moduleCode() == null || command.moduleCode().isBlank()
                || command.command() == null
                || (command.reason() != null && command.reason().trim().length() > 500)
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "모듈 코드와 잠금 명령을 확인해 주세요."
            );
        }
        return ModuleCatalog.find(command.moduleCode())
            .map(ModuleCatalog.Definition::code)
            .orElseThrow(() -> operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_MODULE_CODE",
                "지원하지 않는 모듈 코드입니다."
            ));
    }

    private static ModuleStudentAccessResult replayResult(Map<String, Object> replay) {
        return new ModuleStudentAccessResult(
            java.util.UUID.fromString(replay.get("module_id").toString()),
            replay.get("module_code").toString(),
            replay.get("status").toString(),
            Boolean.TRUE.equals(replay.get("student_access_suspended")),
            ((Number) replay.get("state_version")).longValue(),
            true
        );
    }

    private static Map<String, Object> resultBody(ModuleStudentAccessResult result) {
        return ClassroomMutationSupport.body(
            "module_id", result.moduleId().toString(),
            "module_code", result.moduleCode(),
            "status", result.status(),
            "student_access_suspended", result.studentAccessSuspended(),
            "state_version", result.stateVersion()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return ClassroomMutationSupport.operation(status, code, message);
    }
}
