package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

/**
 * 교사가 쓰겠다고 고른 투자 방식을 바꾸는 명령입니다.
 *
 * <p>학급은 단기 투자와 장기 투자를 함께 운영할 수 있습니다. 활성화는 한 번만 할 수 있어서
 * 단기만 켜 두었다가 장기를 나중에 더하려면 이 명령이 필요합니다.
 *
 * <p>목록에서 빼는 것은 "앞으로 새 시나리오를 만들지 않는다"는 뜻입니다. 이미 만든 시장과
 * 학생 보유 내역은 그대로 둡니다.
 */
@Service
public class InvestmentModeCommandService {

    private static final String MODULE_CODE = "INVESTMENT";
    private static final Set<String> SUPPORTED_MODES = Set.of("SHORT_TERM", "LONG_TERM");
    private static final String ROUTE =
        "/classrooms/{classroom_id}/modules/INVESTMENT/investment-mode-commands";

    private final ClassroomMutationSupport mutationSupport;
    private final ClassroomModuleRepository moduleRepository;
    private final RealtimeEventPublisher eventPublisher;
    private final AuditTrailService auditTrailService;
    private final Clock clock;

    public InvestmentModeCommandService(
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
    public InvestmentModeResult execute(InvestmentModeCommand command) {
        List<String> modes = validate(command);
        UserAccount teacher = mutationSupport.owner(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_MODULES
        );
        String requestHash = mutationSupport.hash(ClassroomMutationSupport.body(
            "classroom_id", command.classroomId(),
            "module_code", MODULE_CODE,
            "investment_modes", String.join(",", modes),
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
            .findForUpdateByClassroomIdAndModuleCode(command.classroomId(), MODULE_CODE)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "이 학급에는 투자 모듈이 없습니다."
            ));
        if (!"ACTIVE".equals(module.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                "투자 모듈을 먼저 활성화해 주세요."
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

        Object before = module.getConfig().get(ClassroomModule.INVESTMENT_MODES_KEY);
        Instant now = clock.instant();
        module.changeInvestmentModes(modes, now);
        try {
            moduleRepository.saveAndFlush(module);
        } catch (
            DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "모듈 상태가 변경되어 투자 방식을 저장하지 못했습니다."
            );
        }

        InvestmentModeResult result = new InvestmentModeResult(
            module.getId(),
            module.getModuleCode(),
            module.getStatus(),
            modes,
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
                "investment_modes", modes,
                "state_version", module.getStateVersion()
            ),
            true
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "MODULE_INVESTMENT_MODES_CHANGED",
            "CLASSROOM_MODULE",
            module.getId(),
            command.requestId(),
            command.correlationId(),
            Map.of("investment_modes", before == null ? "" : before),
            ClassroomMutationSupport.body(
                "investment_modes", String.join(",", modes),
                "state_version", result.stateVersion()
            ),
            "MODULE",
            "투자 운영 방식을 " + String.join(", ", modes) + "로 정했습니다."
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

    private static List<String> validate(InvestmentModeCommand command) {
        if (
            command == null || command.classroomId() == null || command.requestId() == null
                || command.investmentModes() == null || command.investmentModes().isEmpty()
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "사용할 투자 방식을 한 가지 이상 골라 주세요."
            );
        }
        LinkedHashSet<String> modes = new LinkedHashSet<>();
        for (String raw : command.investmentModes()) {
            String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_MODES.contains(normalized)) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_INVESTMENT_MODE",
                    "투자 방식은 단기 또는 장기만 고를 수 있습니다."
                );
            }
            modes.add(normalized);
        }
        return List.copyOf(modes);
    }

    private static InvestmentModeResult replayResult(Map<String, Object> replay) {
        String stored = replay.get("investment_modes") == null
            ? ""
            : replay.get("investment_modes").toString();
        List<String> modes = stored.isBlank() ? List.of() : List.of(stored.split(","));
        return new InvestmentModeResult(
            UUID.fromString(replay.get("module_id").toString()),
            replay.get("module_code").toString(),
            replay.get("status").toString(),
            modes,
            ((Number) replay.get("state_version")).longValue(),
            true
        );
    }

    private static Map<String, Object> resultBody(InvestmentModeResult result) {
        return ClassroomMutationSupport.body(
            "module_id", result.moduleId().toString(),
            "module_code", result.moduleCode(),
            "status", result.status(),
            "investment_modes", String.join(",", result.investmentModes()),
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
