package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

/**
 * API-AI-005 학급 AI 사용 설정을 처리한다.
 *
 * <p>끄기와 다시 켜기를 모두 허용하는 양방향 토글이다. 처음에는 계약이 끄기만 허용해 되돌릴
 * 방법이 없었는데, 의도한 안전 조치가 아니라 계약 누락이었으므로 양방향으로 바꿨다.
 * 어느 방향이든 사유를 받고 AI_SETTINGS 정책 버전과 감사 이벤트를 남긴다.
 *
 * <p>AI 제안을 적용하는 중에는 방향과 무관하게 변경을 막아 일관성을 지킨다.
 *
 * <p>학교 단위 API-ADM-004는 권한·학급 설정과의 우선순위·일괄 적용 이력을 따로 정해야 해서
 * 끄기 전용을 유지한다. 긴급 차단은 운영 환경의 전역 스위치로 대응한다.
 */
@Service
public class AiSettingsService {

    private static final String APPLYING_STATUS = "APPLYING";
    private static final String DISABLED_STATUS = "DISABLED";
    private static final String ENABLED_STATUS = "ENABLED";

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final AiRunRepository runRepository;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AiSettingsService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        AiRunRepository runRepository,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.policyRepository = policyRepository;
        this.runRepository = runRepository;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public AiSettingsResult update(AiSettingsCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_AI_SETTINGS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A teacher session is required."
            ));
        if (!classroomRepository.existsById(command.classroomId())) {
            throw operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The classroom was not found.");
        }
        if (runRepository.existsByClassroomIdAndStatus(command.classroomId(), APPLYING_STATUS)) {
            throw operation(
                HttpStatus.CONFLICT,
                "AI_APPLICATION_IN_PROGRESS",
                "An AI proposal is being applied. Try again after it finishes."
            );
        }

        ClassroomPolicyVersion previous = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                ClassroomAiSettings.POLICY_AREA
            )
            .orElse(null);
        long currentVersion = previous == null ? 0L : previous.getVersion();
        if (command.expectedStateVersion() != null
            && command.expectedStateVersion() != currentVersion) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "The AI settings version changed. Reload and try again."
            );
        }

        Instant now = clock.instant();
        if (previous != null) {
            previous.supersede(now);
        }
        int nextVersion = (int) currentVersion + 1;
        ClassroomPolicyVersion policy = policyRepository.save(ClassroomPolicyVersion.policy(
            uuidGenerator.generate(),
            command.classroomId(),
            ClassroomAiSettings.POLICY_AREA,
            nextVersion,
            Map.of(
                ClassroomAiSettings.ENABLED_KEY, command.enabled(),
                "reason", command.reason()
            ),
            "TEACHER",
            now
        ));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put(
            ClassroomAiSettings.ENABLED_KEY,
            previous == null
                ? true
                : previous.getPolicyValues().get(ClassroomAiSettings.ENABLED_KEY)
        );
        Map<String, Object> after = new LinkedHashMap<>();
        after.put(ClassroomAiSettings.ENABLED_KEY, command.enabled());
        after.put("reason", command.reason());
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "AI_SETTINGS_UPDATED",
            "AI_SETTINGS",
            policy.getId(),
            command.requestId(),
            command.correlationId(),
            before,
            after,
            "AI",
            command.enabled()
                ? "학급 AI 기능을 다시 켰습니다."
                : "학급 AI 기능이 꺼졌습니다."
        );

        return new AiSettingsResult(
            policy.getId(),
            command.enabled() ? ENABLED_STATUS : DISABLED_STATUS,
            nextVersion
        );
    }

    private void validate(AiSettingsCommand command) {
        if (command.teacherSubject() == null || command.teacherSubject().isBlank()) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required.");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason must not be blank.");
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
