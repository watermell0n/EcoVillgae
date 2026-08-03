package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;

@Service
public class ClassroomRosterService {

    private static final Pattern ROLE_CODE =
        Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final SetupPrecheckInvalidator precheckInvalidator;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ClassroomRosterService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        SalaryRoleRepository salaryRoleRepository,
        ClassroomPolicyVersionRepository policyVersionRepository,
        ClassroomModuleRepository moduleRepository,
        SetupPrecheckInvalidator precheckInvalidator,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.salaryRoleRepository = salaryRoleRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.moduleRepository = moduleRepository;
        this.precheckInvalidator = precheckInvalidator;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public UpdateRosterResult update(UpdateRosterCommand command) {
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_ROSTER,
            null
        );
        Classroom classroom = classroomRepository
            .findById(command.classroomId())
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "경제교실을 찾을 수 없습니다."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw new ClassroomOperationException(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "활성 경제교실의 학생 명단만 변경할 수 있습니다."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw new ClassroomOperationException(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "경제교실 상태가 변경되었습니다."
            );
        }

        boolean salaryModuleActive = salaryModuleActive(command.classroomId());
        List<ValidatedStudent> students = validateStudents(
            command.students(),
            salaryModuleActive
        );
        if (!salaryModuleActive) {
            rejectSalaryFieldsWithoutModule(command, students);
            Instant now = clock.instant();
            upsertStudents(command.classroomId(), students, Map.of(), now);
            classroom.touch(now);
            try {
                studentRepository.flush();
                classroomRepository.flush();
            } catch (DataIntegrityViolationException exception) {
                throw translateConstraintViolation(exception);
            }
            precheckInvalidator.invalidate(command.classroomId());
            return new UpdateRosterResult(
                classroom.getId(),
                Math.toIntExact(studentRepository.countByClassroomIdAndStatus(
                    classroom.getId(),
                    ClassroomStudentStatus.ACTIVE
                )),
                0,
                classroom.getStateVersion()
            );
        }
        if (command.expectedPolicyVersion() == null) {
            throw validation("expected_policy_version is required while salary is active.");
        }
        ClassroomPolicyVersion currentPolicy = policyVersionRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                "SALARY"
            )
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "현재 급여 정책을 확인할 수 없습니다."
            ));
        if (currentPolicy.getVersion() != command.expectedPolicyVersion()) {
            throw new ClassroomOperationException(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "급여 정책이 변경되었습니다."
            );
        }

        List<ValidatedSalaryRole> salaryRoles = validateSalaryRoles(command.salaryRoles());
        Instant now = clock.instant();
        Map<String, SalaryRole> rolesByCode = upsertSalaryRoles(
            command.classroomId(),
            salaryRoles,
            now
        );
        validateRoleReferences(students, rolesByCode.keySet());
        upsertStudents(command.classroomId(), students, rolesByCode, now);

        currentPolicy.supersede(now);
        policyVersionRepository.flush();
        policyVersionRepository.save(
            ClassroomPolicyVersion.salary(
                uuidGenerator.generate(),
                command.classroomId(),
                currentPolicy.getVersion() + 1,
                salaryPolicyValues(rolesByCode.values()),
                "TEACHER",
                now
            )
        );
        classroom.touch(now);
        try {
            studentRepository.flush();
            salaryRoleRepository.flush();
            classroomRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraintViolation(exception);
        }
        precheckInvalidator.invalidate(command.classroomId());

        return new UpdateRosterResult(
            classroom.getId(),
            Math.toIntExact(
                studentRepository.countByClassroomIdAndStatus(
                    classroom.getId(),
                    ClassroomStudentStatus.ACTIVE
                )
            ),
            rolesByCode.size(),
            classroom.getStateVersion()
        );
    }

    private Map<String, SalaryRole> upsertSalaryRoles(
        UUID classroomId,
        List<ValidatedSalaryRole> commands,
        Instant now
    ) {
        Map<String, SalaryRole> existing = salaryRoleRepository
            .findAllByClassroomIdOrderBySortOrder(classroomId)
            .stream()
            .collect(Collectors.toMap(SalaryRole::getRoleCode, Function.identity()));
        for (ValidatedSalaryRole command : commands) {
            SalaryRole role = existing.get(command.roleCode());
            if (role == null) {
                role = SalaryRole.create(
                    uuidGenerator.generate(),
                    classroomId,
                    command.roleCode(),
                    command.name(),
                    command.grossAmount(),
                    command.deductions(),
                    command.sortOrder(),
                    now
                );
                existing.put(command.roleCode(), role);
            } else {
                role.update(
                    command.name(),
                    command.grossAmount(),
                    command.deductions(),
                    command.sortOrder(),
                    now
                );
            }
        }
        salaryRoleRepository.saveAll(existing.values());
        return existing;
    }

    private void upsertStudents(
        UUID classroomId,
        List<ValidatedStudent> commands,
        Map<String, SalaryRole> rolesByCode,
        Instant now
    ) {
        List<ClassroomStudent> active = studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            );
        Map<UUID, ClassroomStudent> byId = active.stream()
            .collect(Collectors.toMap(ClassroomStudent::getId, Function.identity()));
        validateAgainstExisting(commands, active);

        List<ClassroomStudent> changed = new ArrayList<>();
        for (ValidatedStudent command : commands) {
            ClassroomStudent student;
            if (command.studentId() == null) {
                student = ClassroomStudent.enroll(
                    uuidGenerator.generate(),
                    classroomId,
                    command.attendanceNumber(),
                    command.nickname(),
                    now
                );
            } else {
                student = byId.get(command.studentId());
                if (student == null) {
                    throw new ClassroomOperationException(
                        HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND",
                        "수정할 학생을 현재 명단에서 찾을 수 없습니다."
                    );
                }
            }
            student.updateRosterAndRole(
                command.attendanceNumber(),
                command.nickname(),
                command.roleCode() == null
                    ? null
                    : rolesByCode.get(command.roleCode()).getId(),
                now
            );
            changed.add(student);
        }
        studentRepository.saveAll(changed);
    }

    private static List<ValidatedSalaryRole> validateSalaryRoles(
        List<SalaryRoleCommand> commands
    ) {
        if (commands == null || commands.isEmpty()) {
            throw validation("salary_roles는 하나 이상이어야 합니다.");
        }
        List<ValidatedSalaryRole> validated = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (SalaryRoleCommand command : commands) {
            String code = command.roleCode() == null
                ? ""
                : command.roleCode().trim().toUpperCase(Locale.ROOT);
            String name = command.name() == null ? "" : command.name().trim();
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (
                !ROLE_CODE.matcher(code).matches()
                    || name.isEmpty()
                    || name.length() > 80
                    || command.grossAmount() < 0
            ) {
                throw validation("역할 코드·이름·급여를 확인해 주세요.");
            }
            if (!codes.add(code) || !names.add(normalizedName)) {
                throw validation("역할 코드와 역할 이름은 중복될 수 없습니다.");
            }
            validated.add(
                new ValidatedSalaryRole(
                    code,
                    name,
                    command.grossAmount(),
                    command.deductions() == null ? Map.of() : command.deductions(),
                    command.sortOrder()
                )
            );
        }
        return validated;
    }

    private static List<ValidatedStudent> validateStudents(
        List<StudentRosterCommand> commands,
        boolean roleRequired
    ) {
        if (commands == null || commands.isEmpty()) {
            throw validation("students는 하나 이상이어야 합니다.");
        }
        Set<Integer> attendanceNumbers = new HashSet<>();
        Set<String> nicknames = new HashSet<>();
        Set<UUID> studentIds = new HashSet<>();
        List<ValidatedStudent> validated = new ArrayList<>();
        for (StudentRosterCommand command : commands) {
            String nickname = command.nickname() == null ? "" : command.nickname().trim();
            String roleCode = command.roleCode() == null
                ? ""
                : command.roleCode().trim().toUpperCase(Locale.ROOT);
            if (
                command.attendanceNumber() <= 0
                    || nickname.isEmpty()
                    || nickname.length() > 40
                    || (roleRequired && !ROLE_CODE.matcher(roleCode).matches())
                    || (!roleCode.isEmpty() && !ROLE_CODE.matcher(roleCode).matches())
            ) {
                throw validation("학생 번호·닉네임·역할을 확인해 주세요.");
            }
            if (!attendanceNumbers.add(command.attendanceNumber())) {
                throw duplicateAttendanceNumber();
            }
            if (!nicknames.add(nickname.toLowerCase(Locale.ROOT))) {
                throw duplicateNickname();
            }
            if (command.studentId() != null && !studentIds.add(command.studentId())) {
                throw validation("같은 학생을 한 요청에서 두 번 수정할 수 없습니다.");
            }
            validated.add(
                new ValidatedStudent(
                    command.studentId(),
                    command.attendanceNumber(),
                    nickname,
                    roleCode.isEmpty() ? null : roleCode
                )
            );
        }
        return validated;
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

    private static void rejectSalaryFieldsWithoutModule(
        UpdateRosterCommand command,
        List<ValidatedStudent> students
    ) {
        boolean hasRoles = command.salaryRoles() != null && !command.salaryRoles().isEmpty();
        boolean hasAssignments = students.stream().anyMatch(student -> student.roleCode() != null);
        if (hasRoles || hasAssignments || command.expectedPolicyVersion() != null) {
            throw new ClassroomOperationException(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                "Salary roles can be configured only while the role and salary module is active."
            );
        }
    }

    private static void validateRoleReferences(
        List<ValidatedStudent> students,
        Set<String> roleCodes
    ) {
        if (students.stream().anyMatch(student -> !roleCodes.contains(student.roleCode()))) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SALARY_ROLE_NOT_FOUND",
                "학생이 참조한 급여 역할을 찾을 수 없습니다."
            );
        }
    }

    private static void validateAgainstExisting(
        List<ValidatedStudent> commands,
        List<ClassroomStudent> existing
    ) {
        Map<Integer, UUID> attendanceOwners = new HashMap<>();
        Map<String, UUID> nicknameOwners = new HashMap<>();
        for (ClassroomStudent student : existing) {
            attendanceOwners.put(student.getRosterNumber(), student.getId());
            nicknameOwners.put(student.getNickname().toLowerCase(Locale.ROOT), student.getId());
        }
        for (ValidatedStudent command : commands) {
            UUID attendanceOwner = attendanceOwners.get(command.attendanceNumber());
            if (attendanceOwner != null && !attendanceOwner.equals(command.studentId())) {
                throw duplicateAttendanceNumber();
            }
            UUID nicknameOwner = nicknameOwners.get(
                command.nickname().toLowerCase(Locale.ROOT)
            );
            if (nicknameOwner != null && !nicknameOwner.equals(command.studentId())) {
                throw duplicateNickname();
            }
        }
    }

    private static Map<String, Object> salaryPolicyValues(
        java.util.Collection<SalaryRole> roles
    ) {
        List<Map<String, Object>> values = roles.stream()
            .map(role -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role_code", role.getRoleCode());
                item.put("name", role.getName());
                item.put("gross_amount", role.getGrossAmount());
                item.put("deductions", role.getDeductions());
                return item;
            })
            .toList();
        return Map.of("roles", values);
    }

    private static ClassroomOperationException translateConstraintViolation(
        DataIntegrityViolationException exception
    ) {
        String rawMessage = exception.getMostSpecificCause().getMessage();
        String message = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.ROOT);
        if (message.contains("roster")) {
            return duplicateAttendanceNumber();
        }
        if (message.contains("nickname")) {
            return duplicateNickname();
        }
        return new ClassroomOperationException(
            HttpStatus.CONFLICT,
            "POLICY_VIOLATION",
            "명단 또는 급여 정책이 현재 데이터와 충돌합니다."
        );
    }

    private static ClassroomOperationException duplicateAttendanceNumber() {
        return new ClassroomOperationException(
            HttpStatus.CONFLICT,
            "DUPLICATE_ATTENDANCE_NUMBER",
            "같은 학급에서 학생 번호는 중복될 수 없습니다."
        );
    }

    private static ClassroomOperationException duplicateNickname() {
        return new ClassroomOperationException(
            HttpStatus.CONFLICT,
            "DUPLICATE_NICKNAME",
            "같은 학급에서 닉네임은 대소문자와 관계없이 중복될 수 없습니다."
        );
    }

    private static ClassroomOperationException validation(String message) {
        return new ClassroomOperationException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            message
        );
    }

    private record ValidatedSalaryRole(
        String roleCode,
        String name,
        long grossAmount,
        Map<String, Object> deductions,
        int sortOrder
    ) {
    }

    private record ValidatedStudent(
        UUID studentId,
        int attendanceNumber,
        String nickname,
        String roleCode
    ) {
    }
}
