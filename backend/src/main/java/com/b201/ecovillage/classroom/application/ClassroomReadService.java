package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.identity.authorization.AuthorizationDecision;
import com.b201.ecovillage.identity.authorization.ClassroomActorRole;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.realtime.application.StudentPresenceService;
import com.b201.ecovillage.realtime.application.StudentPresenceService.StudentPresenceSnapshot;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class ClassroomReadService {

    private static final String PRIVATE_POLICY_AREA = "AI_SETTINGS";
    // 학생 앱이 이 키들을 읽어 진행 중인 활동을 찾고 잠긴 모듈을 회색으로 표시한다.
    // 여기에 없는 키는 학생 응답에서 지워지므로, 새로 추가한 config 키는 반드시 함께 등록한다.
    private static final List<String> PUBLIC_MODULE_CONFIG_KEYS = List.of(
        "policy_area",
        "policy_version",
        "investment_mode",
        ClassroomModule.ACTIVE_QUIZ_SESSION_KEY,
        "quiz_session_id",
        "active_vote_id",
        "vote_id",
        "active_guided_activity_id",
        "guided_activity_id",
        ClassroomModule.STUDENT_ACCESS_SUSPENDED_KEY
    );

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomStudentRepository studentRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;
    private final StudentPresenceService presenceService;
    private final ActivitySessionRepository activitySessionRepository;
    private final Clock clock;

    public ClassroomReadService(
        ClassroomAuthorizationService authorizationService,
        ClassroomStudentRepository studentRepository,
        SalaryRoleRepository salaryRoleRepository,
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyVersionRepository,
        StudentPresenceService presenceService,
        ActivitySessionRepository activitySessionRepository,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.studentRepository = studentRepository;
        this.salaryRoleRepository = salaryRoleRepository;
        this.moduleRepository = moduleRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.presenceService = presenceService;
        this.activitySessionRepository = activitySessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RosterAndSalariesView rosterAndSalaries(
        String subject,
        UUID classroomId
    ) {
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_ROSTER,
            null
        );

        boolean salaryModuleActive = salaryModuleActive(classroomId);
        List<SalaryRole> salaryRoles = salaryModuleActive
            ? salaryRoleRepository
                .findAllByClassroomIdOrderBySortOrder(classroomId)
                .stream()
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .toList()
            : List.of();
        Map<UUID, SalaryRole> salaryRolesById = salaryRoles
            .stream()
            .collect(Collectors.toMap(SalaryRole::getId, Function.identity()));

        List<RosterAndSalariesView.StudentView> students = studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            )
            .stream()
            .map(student -> toStudentView(
                student,
                salaryRolesById,
                presenceService.get(classroomId, student.getId(), clock.instant())
            ))
            .toList();
        List<RosterAndSalariesView.SalaryRoleView> roles = salaryRoles
            .stream()
            .map(ClassroomReadService::toSalaryRoleView)
            .toList();

        return new RosterAndSalariesView(students, roles);
    }

    @Transactional(readOnly = true)
    public ModulesAndPoliciesView modulesAndPolicies(
        String subject,
        UUID classroomId
    ) {
        AuthorizationDecision authorization = authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_MODULES_AND_POLICIES,
            possibleStudentId(subject)
        );
        boolean student = authorization.actorType() == ClassroomActorRole.STU;

        List<ClassroomModule> moduleEntities = student
            ? moduleRepository.findAllByClassroomIdAndStatusOrderByModuleCode(
                classroomId,
                "ACTIVE"
            )
            : moduleRepository.findAllByClassroomIdOrderByModuleCode(classroomId);
        UUID activeGuidedActivityId = activitySessionRepository
            .findFirstByClassroomIdAndStatus(classroomId, "LIVE")
            .map(activity -> activity.getId())
            .orElse(null);
        List<ModulesAndPoliciesView.ModuleView> modules = moduleEntities
            .stream()
            .map(module -> toModuleView(module, student, activeGuidedActivityId))
            .toList();
        List<ModulesAndPoliciesView.PolicyVersionView> policies = policyVersionRepository
            .findAllByClassroomIdAndSupersededAtIsNullOrderByPolicyArea(classroomId)
            .stream()
            .filter(policy -> !student || !PRIVATE_POLICY_AREA.equals(policy.getPolicyArea()))
            .map(policy -> toPolicyView(policy, student))
            .toList();

        return new ModulesAndPoliciesView(modules, policies);
    }

    private boolean salaryModuleActive(UUID classroomId) {
        return moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(
            classroomId,
            "ROLE_SALARY",
            "ACTIVE"
        ) || moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(
            classroomId,
            "SALARY",
            "ACTIVE"
        );
    }

    private static RosterAndSalariesView.StudentView toStudentView(
        ClassroomStudent student,
        Map<UUID, SalaryRole> salaryRolesById,
        StudentPresenceSnapshot presence
    ) {
        SalaryRole role = student.getSalaryRoleId() == null
            ? null
            : salaryRolesById.get(student.getSalaryRoleId());
        return new RosterAndSalariesView.StudentView(
            student.getId(),
            student.getNickname(),
            student.getStatus().name(),
            Math.toIntExact(student.getStateVersion()),
            student.getStateVersion(),
            student.getRosterNumber(),
            student.getNickname(),
            role == null ? null : role.getRoleCode(),
            presence.status(),
            presence.currentScreen(),
            presence.lastSeenAt()
        );
    }

    private static RosterAndSalariesView.SalaryRoleView toSalaryRoleView(
        SalaryRole role
    ) {
        return new RosterAndSalariesView.SalaryRoleView(
            role.getId(),
            role.getName(),
            role.getStatus(),
            Math.toIntExact(role.getStateVersion()),
            role.getStateVersion(),
            role.getRoleCode(),
            role.getGrossAmount()
        );
    }

    private static ModulesAndPoliciesView.ModuleView toModuleView(
        ClassroomModule module,
        boolean publicView,
        UUID activeGuidedActivityId
    ) {
        Map<String, Object> config = new LinkedHashMap<>(
            publicView ? publicModuleConfig(module.getConfig()) : module.getConfig()
        );
        if (activeGuidedActivityId != null) {
            config.put("active_guided_activity_id", activeGuidedActivityId.toString());
        }
        return new ModulesAndPoliciesView.ModuleView(
            module.getId(),
            module.getModuleCode(),
            module.getStatus(),
            Math.toIntExact(module.getStateVersion()),
            module.getStateVersion(),
            module.getModuleCode(),
            Map.copyOf(config)
        );
    }

    private static ModulesAndPoliciesView.PolicyVersionView toPolicyView(
        ClassroomPolicyVersion policy,
        boolean publicView
    ) {
        return new ModulesAndPoliciesView.PolicyVersionView(
            policy.getId(),
            policy.getPolicyArea(),
            policy.getStatus(),
            policy.getVersion(),
            policy.getVersion(),
            policy.getPolicyArea(),
            publicView ? Map.of() : policy.getPolicyValues()
        );
    }

    private static Map<String, Object> publicModuleConfig(Map<String, Object> config) {
        Map<String, Object> visible = new LinkedHashMap<>();
        PUBLIC_MODULE_CONFIG_KEYS.forEach(key -> {
            if (config.containsKey(key)) {
                visible.put(key, config.get(key));
            }
        });
        return Map.copyOf(visible);
    }

    private static UUID possibleStudentId(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
