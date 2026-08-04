package com.b201.ecovillage.course.application;

import java.util.List;
import java.util.Map;
import java.util.Comparator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ModuleCatalog;
import com.b201.ecovillage.course.domain.CourseTemplate;
import com.b201.ecovillage.course.domain.CourseTemplateModule;
import com.b201.ecovillage.course.domain.CourseTemplateVersion;
import com.b201.ecovillage.course.repository.CourseTemplateModuleRepository;
import com.b201.ecovillage.course.repository.CourseTemplateRepository;
import com.b201.ecovillage.course.repository.CourseTemplateVersionRepository;
import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.UserType;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@Service
public class CourseRecommendationQueryService {

    private final UserAccountRepository userRepository;
    private final CourseTemplateRepository templateRepository;
    private final CourseTemplateVersionRepository versionRepository;
    private final CourseTemplateModuleRepository moduleRepository;

    public CourseRecommendationQueryService(
        UserAccountRepository userRepository,
        CourseTemplateRepository templateRepository,
        CourseTemplateVersionRepository versionRepository,
        CourseTemplateModuleRepository moduleRepository
    ) {
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.moduleRepository = moduleRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseRecommendationView> find(
        String teacherSubject,
        Integer grade,
        CourseSize courseSize
    ) {
        requireTeacher(teacherSubject);
        if ((grade == null) != (courseSize == null)) {
            throw validation(
                "grade and course_size must be provided together or both omitted."
            );
        }
        if (grade != null && (grade < 4 || grade > 6)) {
            throw validation("grade must be between 4 and 6.");
        }
        return templateRepository.findAllByStatusOrderByGradeMinAscNameAsc("PUBLISHED")
            .stream()
            .filter(template -> grade == null || (
                template.getGradeMin() <= grade
                    && template.getGradeMax() >= grade
                    && template.getCourseSize() == courseSize
            ))
            .sorted(
                Comparator.comparingInt(CourseTemplate::getGradeMin)
                    .thenComparingInt(template -> template.getCourseSize().ordinal())
            )
            .map(this::view)
            .toList();
    }

    private CourseRecommendationView view(CourseTemplate template) {
        CourseTemplateVersion version = versionRepository
            .findTopByCourseTemplateIdAndStatusOrderByVersionDesc(
                template.getId(),
                "PUBLISHED"
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "COURSE_VERSION_MISSING",
                "A published course template has no published version."
            ));
        Map<String, Object> profile = version.getDifficultyProfile();
        List<CourseModuleView> modules = moduleRepository
            .findAllByCourseTemplateVersionIdOrderBySortOrderAscModuleCodeAsc(
                version.getId()
            )
            .stream()
            .map(this::moduleView)
            .toList();
        return new CourseRecommendationView(
            version.getId(),
            template.getTemplateCode(),
            template.getName(),
            template.getGradeMin(),
            template.getCourseSize(),
            version.getVersion(),
            text(profile, "data_version"),
            version.getPublishedAt(),
            profile,
            text(profile, "learning_focus"),
            text(profile, "recommendation_reason"),
            modules
        );
    }

    private CourseModuleView moduleView(CourseTemplateModule module) {
        ModuleCatalog.Definition definition = ModuleCatalog.find(module.getModuleCode())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "COURSE_MODULE_CODE_INVALID",
                "A course template contains a non-canonical module code."
            ));
        return new CourseModuleView(
            definition.code(),
            definition.displayName(),
            module.getRecommendationLevel(),
            module.prerequisites().stream()
                .filter(code -> !ModuleCatalog.isWalletInfrastructure(code))
                .map(code -> ModuleCatalog.find(code).orElseThrow(() -> operation(
                    HttpStatus.CONFLICT,
                    "COURSE_MODULE_CODE_INVALID",
                    "A course template contains an invalid prerequisite module code."
                )).code())
                .distinct()
                .toList(),
            module.exclusionImpact()
        );
    }

    private UserAccount requireTeacher(String subject) {
        if (subject == null || subject.isBlank()) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            );
        }
        UserAccount teacher = userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
        if (
            teacher.getUserType() != UserType.TEACHER
                || teacher.getStatus() != AccountStatus.ACTIVE
        ) {
            throw operation(
                HttpStatus.FORBIDDEN,
                "ROLE_NOT_ALLOWED",
                "Only an active teacher can view course recommendations."
            );
        }
        return teacher;
    }

    private static String text(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
