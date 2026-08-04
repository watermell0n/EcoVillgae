package com.b201.ecovillage.classroom.application;

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

@Service
public class ClassroomReadService {

    private static final String PRIVATE_POLICY_AREA = "AI_SETTINGS";
    private static final List<String> PUBLIC_MODULE_CONFIG_KEYS = List.of(
        "policy_area",
        "policy_version",
        "investment_mode"
    );

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomStudentRepository studentRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;

    public ClassroomReadService(
        ClassroomAuthorizationService authorizationService,
        ClassroomStudentRepository studentRepository,
        SalaryRoleRepository salaryRoleRepository,
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyVersionRepository
    ) {
        this.authorizationService = authorizationService;
        this.studentRepository = studentRepository;
        this.salaryRoleRepository = salaryRoleRepository;
        this.moduleRepository = moduleRepository;
        this.policyVersionRepository = policyVersionRepository;
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
            .map(student -> toStudentView(student, salaryRolesById))
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
        List<ModulesAndPoliciesView.ModuleView> modules = moduleEntities
            .stream()
            .map(module -> toModuleView(module, student))
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
        Map<UUID, SalaryRole> salaryRolesById
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
            role == null ? null : role.getRoleCode()
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
        boolean publicView
    ) {
        return new ModulesAndPoliciesView.ModuleView(
            module.getId(),
            module.getModuleCode(),
            module.getStatus(),
            Math.toIntExact(module.getStateVersion()),
            module.getStateVersion(),
            module.getModuleCode(),
            publicView ? publicModuleConfig(module.getConfig()) : module.getConfig()
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
