package com.b201.ecovillage.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.course.domain.CourseTemplate;
import com.b201.ecovillage.course.domain.CourseTemplateVersion;
import com.b201.ecovillage.course.repository.CourseTemplateRepository;
import com.b201.ecovillage.course.repository.CourseTemplateVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class CourseConfigurationCommandService {

    static final String POLICY_AREA = "COURSE_CONFIGURATION";
    private static final String ROUTE =
        "/classrooms/{classroom_id}/course-configuration";

    private final ClassroomMutationSupport mutationSupport;
    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final CourseTemplateRepository courseTemplateRepository;
    private final CourseTemplateVersionRepository courseVersionRepository;
    private final ActivitySessionRepository activityRepository;
    private final SetupPrecheckInvalidator precheckInvalidator;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public CourseConfigurationCommandService(
        ClassroomMutationSupport mutationSupport,
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        CourseTemplateRepository courseTemplateRepository,
        CourseTemplateVersionRepository courseVersionRepository,
        ActivitySessionRepository activityRepository,
        SetupPrecheckInvalidator precheckInvalidator,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.mutationSupport = mutationSupport;
        this.classroomRepository = classroomRepository;
        this.policyRepository = policyRepository;
        this.courseTemplateRepository = courseTemplateRepository;
        this.courseVersionRepository = courseVersionRepository;
        this.activityRepository = activityRepository;
        this.precheckInvalidator = precheckInvalidator;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public CourseConfigurationUpdateResult update(
        CourseConfigurationCommand command
    ) {
        validate(command);
        UserAccount teacher = mutationSupport.owner(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_COURSE_CONFIGURATION
        );
        String requestHash = mutationSupport.hash(hashValues(command));
        Map<String, Object> replay = mutationSupport.replay(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return new CourseConfigurationUpdateResult(
                UUID.fromString(replay.get("classroom_id").toString()),
                ((Number) replay.get("policy_version")).intValue(),
                ((Number) replay.get("state_version")).longValue(),
                true
            );
        }

        Classroom classroom = classroomRepository.findForUpdateById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        validateClassroom(command, classroom);
        if (activityRepository.existsByClassroomIdAndStatusIn(
            command.classroomId(),
            List.of("LIVE", "FINALIZING")
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "ACTIVITY_IN_PROGRESS",
                "Finish the current activity before changing the course configuration."
            );
        }
        validateRecommendedCourse(command);

        ClassroomPolicyVersion previous = policyRepository
            .findForUpdateByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                POLICY_AREA
            )
            .orElse(null);
        int currentVersion = previous == null ? 0 : previous.getVersion();
        if (command.expectedPolicyVersion() != currentVersion) {
            throw policyConflict();
        }

        Instant now = clock.instant();
        Map<String, Object> before = configurationSummary(classroom, currentVersion);
        int nextVersion = currentVersion + 1;
        if (previous != null) {
            previous.supersede(now);
            policyRepository.flush();
        }
        ClassroomPolicyVersion policy = ClassroomPolicyVersion.policy(
            uuidGenerator.generate(),
            command.classroomId(),
            POLICY_AREA,
            nextVersion,
            policyValues(command),
            "TEACHER",
            now
        );
        policyRepository.save(policy);
        classroom.updateCourseConfiguration(
            command.grade(),
            command.courseSize(),
            command.startsOn(),
            command.endsOn(),
            command.recommendedCourseId(),
            now
        );
        try {
            policyRepository.flush();
            classroomRepository.saveAndFlush(classroom);
        } catch (
            DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception
        ) {
            throw policyConflict();
        }

        CourseConfigurationUpdateResult result = new CourseConfigurationUpdateResult(
            classroom.getId(),
            nextVersion,
            classroom.getStateVersion(),
            false
        );
        precheckInvalidator.invalidate(command.classroomId());
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "COURSE_CONFIGURATION_CHANGED",
            "CLASSROOM",
            classroom.getId(),
            command.requestId(),
            command.correlationId(),
            before,
            configurationSummary(classroom, nextVersion),
            "POLICY",
            "Course configuration was updated."
        );
        mutationSupport.remember(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash,
            ClassroomMutationSupport.body(
                "classroom_id", result.classroomId().toString(),
                "policy_version", result.policyVersion(),
                "state_version", result.stateVersion()
            )
        );
        return result;
    }

    private static void validate(CourseConfigurationCommand command) {
        if (
            command == null || command.classroomId() == null
                || command.requestId() == null || command.courseSize() == null
                || command.startsOn() == null || command.endsOn() == null
                || command.difficultyProfile() == null
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the course configuration values."
            );
        }
        if (
            command.grade() < 4 || command.grade() > 6
                || command.endsOn().isBefore(command.startsOn())
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Grade and course dates are invalid."
            );
        }
        String expectedDifficulty = switch (command.grade()) {
            case 4 -> "GRADE_4_BASIC";
            case 5 -> "GRADE_5_STANDARD";
            case 6 -> "GRADE_6_ADVANCED";
            default -> "";
        };
        if (!expectedDifficulty.equals(command.difficultyProfile())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "The difficulty profile must match the selected grade."
            );
        }
    }

    private static void validateClassroom(
        CourseConfigurationCommand command,
        Classroom classroom
    ) {
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Course configuration can change only in an active classroom."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
        boolean codeChanged = command.classroomCode() != null
            && !classroom.getClassroomCode().equals(command.classroomCode());
        boolean currencyChanged = command.currencyName() != null
            && !classroom.getCurrencyName().equals(command.currencyName());
        if (codeChanged || currencyChanged) {
            throw operation(
                HttpStatus.CONFLICT,
                "IMMUTABLE_CLASSROOM_FIELD",
                "Classroom code and currency name cannot be changed."
            );
        }
    }

    private void validateRecommendedCourse(CourseConfigurationCommand command) {
        if (command.recommendedCourseId() == null) {
            return;
        }
        CourseTemplateVersion version = courseVersionRepository
            .findById(command.recommendedCourseId())
            .orElseThrow(CourseConfigurationCommandService::courseMissing);
        CourseTemplate template = courseTemplateRepository
            .findById(version.getCourseTemplateId())
            .orElseThrow(CourseConfigurationCommandService::courseMissing);
        if (!version.isPublished() || !template.isPublished()) {
            throw courseMissing();
        }
        if (
            command.grade() < template.getGradeMin()
                || command.grade() > template.getGradeMax()
                || command.courseSize() != template.getCourseSize()
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "The recommended course does not match the selected grade and size."
            );
        }
        Object configuredDifficulty = version.getDifficultyProfile().get(
            "difficulty_profile"
        );
        if (
            configuredDifficulty != null
                && !configuredDifficulty.toString().isBlank()
                && !command.difficultyProfile().equals(configuredDifficulty.toString())
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "The recommended course does not match the selected difficulty."
            );
        }
    }

    private static Map<String, Object> hashValues(
        CourseConfigurationCommand command
    ) {
        return ClassroomMutationSupport.body(
            "classroom_id", command.classroomId(),
            "expected_state_version", command.expectedStateVersion(),
            "grade", command.grade(),
            "course_size", command.courseSize(),
            "starts_on", command.startsOn(),
            "ends_on", command.endsOn(),
            "recommended_course_id", command.recommendedCourseId(),
            "difficulty_profile", command.difficultyProfile(),
            "expected_policy_version", command.expectedPolicyVersion(),
            "classroom_code", command.classroomCode(),
            "currency_name", command.currencyName()
        );
    }

    private static Map<String, Object> policyValues(
        CourseConfigurationCommand command
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("grade", command.grade());
        values.put("course_size", command.courseSize().name());
        values.put("starts_on", command.startsOn().toString());
        values.put("ends_on", command.endsOn().toString());
        values.put(
            "recommended_course_id",
            command.recommendedCourseId() == null
                ? ""
                : command.recommendedCourseId().toString()
        );
        values.put("difficulty_profile", command.difficultyProfile());
        return values;
    }

    private static Map<String, Object> configurationSummary(
        Classroom classroom,
        int policyVersion
    ) {
        return ClassroomMutationSupport.body(
            "grade", classroom.getGrade(),
            "course_size", classroom.getCourseSize().name(),
            "starts_on", classroom.getActualStartsOn() == null
                ? "" : classroom.getActualStartsOn().toString(),
            "ends_on", classroom.getActualEndsOn() == null
                ? "" : classroom.getActualEndsOn().toString(),
            "course_template_version_id",
            classroom.getCourseTemplateVersionId() == null
                ? "" : classroom.getCourseTemplateVersionId().toString(),
            "policy_version", policyVersion
        );
    }

    private static ClassroomOperationException policyConflict() {
        return operation(
            HttpStatus.CONFLICT,
            "POLICY_VERSION_CONFLICT",
            "The course configuration policy has changed."
        );
    }

    private static ClassroomOperationException courseMissing() {
        return operation(
            HttpStatus.CONFLICT,
            "COURSE_VERSION_MISSING",
            "The selected published course version could not be found."
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
