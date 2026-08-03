package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ModuleStateTransition;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ModuleStateTransitionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.trade.domain.TradeStatus;
import com.b201.ecovillage.trade.repository.TradeRepository;

@Service
public class ModuleStopCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/modules/{module_code}/stop-commands";
    private static final Set<TradeStatus> OPEN_TRADE_STATUSES = Set.of(
        TradeStatus.AWAITING_COUNTERPART,
        TradeStatus.AWAITING_TEACHER,
        TradeStatus.SETTLING
    );

    private final ClassroomMutationSupport mutationSupport;
    private final ClassroomModuleRepository moduleRepository;
    private final ModuleStateTransitionRepository transitionRepository;
    private final ActivitySessionRepository activityRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final ClassroomStudentRepository studentRepository;
    private final DepositAccountRepository depositAccountRepository;
    private final TradeRepository tradeRepository;
    private final SetupPrecheckInvalidator precheckInvalidator;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ModuleStopCommandService(
        ClassroomMutationSupport mutationSupport,
        ClassroomModuleRepository moduleRepository,
        ModuleStateTransitionRepository transitionRepository,
        ActivitySessionRepository activityRepository,
        SalaryRoleRepository salaryRoleRepository,
        ClassroomStudentRepository studentRepository,
        DepositAccountRepository depositAccountRepository,
        TradeRepository tradeRepository,
        SetupPrecheckInvalidator precheckInvalidator,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.mutationSupport = mutationSupport;
        this.moduleRepository = moduleRepository;
        this.transitionRepository = transitionRepository;
        this.activityRepository = activityRepository;
        this.salaryRoleRepository = salaryRoleRepository;
        this.studentRepository = studentRepository;
        this.depositAccountRepository = depositAccountRepository;
        this.tradeRepository = tradeRepository;
        this.precheckInvalidator = precheckInvalidator;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public ModuleStopResult stop(ModuleStopCommand command) {
        String moduleCode = validate(command);
        UserAccount teacher = mutationSupport.owner(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_MODULES
        );
        String requestHash = mutationSupport.hash(ClassroomMutationSupport.body(
            "classroom_id", command.classroomId(),
            "module_code", moduleCode,
            "expected_state_version", command.expectedStateVersion(),
            "reason", command.reason().trim()
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
            .findForUpdateByClassroomIdAndModuleCode(
                command.classroomId(),
                moduleCode
            )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The selected module could not be found."
            ));
        if (!"ACTIVE".equals(module.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                "Only an active module can be stopped."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != module.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The module state has changed."
            );
        }

        List<String> blockers = cleanupBlockers(command.classroomId(), moduleCode);
        Instant now = clock.instant();
        Map<String, Object> config = new LinkedHashMap<>(module.getConfig());
        config.put("stop_reason", command.reason().trim());
        config.put("stop_requested_at", now.toString());
        config.put("cleanup_required", true);
        config.put("cleanup_blockers", blockers);
        module.beginStopping(config, now);
        try {
            moduleRepository.saveAndFlush(module);
            transitionRepository.saveAndFlush(ModuleStateTransition.stopped(
                uuidGenerator.generate(),
                command.classroomId(),
                module.getId(),
                teacher.getId(),
                module.getStateVersion(),
                blockers.isEmpty() ? "CLEANUP_REQUIRED" : "SETTLEMENT_REQUIRED",
                command.reason().trim(),
                now
            ));
        } catch (
            DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The module state changed while the stop command was applied."
            );
        }

        ModuleStopResult result = new ModuleStopResult(
            module.getId(),
            module.getModuleCode(),
            module.getStatus(),
            module.getStateVersion(),
            true,
            blockers,
            false
        );
        precheckInvalidator.invalidate(command.classroomId());
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "MODULE_STOP_REQUESTED",
            "CLASSROOM_MODULE",
            module.getId(),
            command.requestId(),
            command.correlationId(),
            Map.of("status", "ACTIVE", "state_version", module.getStateVersion() - 1),
            ClassroomMutationSupport.body(
                "status", module.getStatus(),
                "state_version", module.getStateVersion(),
                "cleanup_blockers", blockers
            ),
            "MODULE",
            moduleCode + " module cleanup started."
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

    private List<String> cleanupBlockers(
        java.util.UUID classroomId,
        String moduleCode
    ) {
        List<String> blockers = new ArrayList<>();
        if (activityRepository.existsByClassroomIdAndStatusIn(
            classroomId,
            List.of("LIVE", "FINALIZING")
        )) {
            blockers.add("LIVE_ACTIVITY_EXISTS");
        }
        if ("ROLE_SALARY".equals(moduleCode) || "SALARY".equals(moduleCode)) {
            boolean activeRoles = salaryRoleRepository
                .findAllByClassroomIdOrderBySortOrder(classroomId)
                .stream()
                .anyMatch(role -> "ACTIVE".equals(role.getStatus()));
            boolean assignedRoles = studentRepository
                .findAllByClassroomIdAndStatusOrderByRosterNumber(
                    classroomId,
                    ClassroomStudentStatus.ACTIVE
                )
                .stream()
                .anyMatch(student -> student.getSalaryRoleId() != null);
            if (activeRoles || assignedRoles) {
                blockers.add("SALARY_ROLE_SETTLEMENT_REQUIRED");
            }
        }
        if (
            "DEPOSIT".equals(moduleCode)
                && depositAccountRepository.existsByClassroomIdAndStatus(
                    classroomId,
                    DepositAccountStatus.ACTIVE
                )
        ) {
            blockers.add("DEPOSIT_SETTLEMENT_REQUIRED");
        }
        if (
            "TRADE".equals(moduleCode)
                && tradeRepository.existsByClassroomIdAndStatusIn(
                    classroomId,
                    OPEN_TRADE_STATUSES
                )
        ) {
            blockers.add("TRADE_SETTLEMENT_REQUIRED");
        }
        return List.copyOf(blockers);
    }

    private static String validate(ModuleStopCommand command) {
        if (
            command == null || command.classroomId() == null
                || command.requestId() == null || command.moduleCode() == null
                || command.moduleCode().isBlank() || command.reason() == null
                || command.reason().isBlank() || command.reason().trim().length() > 500
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Module code and a stop reason are required."
            );
        }
        return command.moduleCode().trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static ModuleStopResult replayResult(Map<String, Object> replay) {
        List<String> blockers = ((List<Object>) replay.getOrDefault(
            "cleanup_blockers",
            List.of()
        )).stream().map(Object::toString).toList();
        return new ModuleStopResult(
            java.util.UUID.fromString(replay.get("module_id").toString()),
            replay.get("module_code").toString(),
            replay.get("status").toString(),
            ((Number) replay.get("state_version")).longValue(),
            Boolean.TRUE.equals(replay.get("cleanup_required")),
            blockers,
            true
        );
    }

    private static Map<String, Object> resultBody(ModuleStopResult result) {
        return ClassroomMutationSupport.body(
            "module_id", result.moduleId().toString(),
            "module_code", result.moduleCode(),
            "status", result.status(),
            "state_version", result.stateVersion(),
            "cleanup_required", result.cleanupRequired(),
            "cleanup_blockers", result.cleanupBlockers()
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
