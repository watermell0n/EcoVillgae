package com.b201.ecovillage.course.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.course.domain.CourseTemplate;
import com.b201.ecovillage.course.domain.CourseTemplateModule;
import com.b201.ecovillage.course.domain.CourseTemplateVersion;
import com.b201.ecovillage.course.repository.CourseTemplateModuleRepository;
import com.b201.ecovillage.course.repository.CourseTemplateRepository;
import com.b201.ecovillage.course.repository.CourseTemplateVersionRepository;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

class CourseRecommendationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void filtersPublishedProductDataByGradeAndCourseSize() {
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        CourseTemplateRepository templateRepository =
            mock(CourseTemplateRepository.class);
        CourseTemplateVersionRepository versionRepository =
            mock(CourseTemplateVersionRepository.class);
        CourseTemplateModuleRepository moduleRepository =
            mock(CourseTemplateModuleRepository.class);
        CourseRecommendationQueryService service = new CourseRecommendationQueryService(
            userRepository,
            templateRepository,
            versionRepository,
            moduleRepository
        );
        CourseTemplate selected = CourseTemplate.published(
            UUID.randomUUID(),
            "G4_ONE_WEEK",
            "Grade 4 one week",
            4,
            CourseSize.ONE_WEEK,
            NOW
        );
        CourseTemplate other = CourseTemplate.published(
            UUID.randomUUID(),
            "G5_ONE_WEEK",
            "Grade 5 one week",
            5,
            CourseSize.ONE_WEEK,
            NOW
        );
        CourseTemplateVersion version = CourseTemplateVersion.published(
            UUID.randomUUID(),
            selected.getId(),
            1,
            Map.of(
                "profile", "GRADE_4_BASIC",
                "learning_focus", "saving",
                "recommendation_reason", "first cycle",
                "data_version", "2026.07.29"
            ),
            NOW
        );
        CourseTemplateModule module = CourseTemplateModule.published(
            UUID.randomUUID(),
            version.getId(),
            "DEPOSIT",
            "CORE",
            1,
            Map.of(
                "label", "Fixed deposit",
                "prerequisites", List.of("WALLET", "BUDGET")
            ),
            NOW
        );
        when(userRepository.findByExternalSubject("teacher"))
            .thenReturn(Optional.of(UserAccount.createTeacher(
                UUID.randomUUID(),
                "teacher",
                "Teacher",
                NOW
            )));
        when(templateRepository.findAllByStatusOrderByGradeMinAscNameAsc("PUBLISHED"))
            .thenReturn(List.of(selected, other));
        when(
            versionRepository.findTopByCourseTemplateIdAndStatusOrderByVersionDesc(
                selected.getId(),
                "PUBLISHED"
            )
        ).thenReturn(Optional.of(version));
        when(
            moduleRepository
                .findAllByCourseTemplateVersionIdOrderBySortOrderAscModuleCodeAsc(
                    version.getId()
                )
        ).thenReturn(List.of(module));

        List<CourseRecommendationView> result = service.find(
            "teacher",
            4,
            CourseSize.ONE_WEEK
        );

        assertEquals(1, result.size());
        assertEquals("G4_ONE_WEEK", result.getFirst().templateCode());
        assertEquals(List.of("BUDGET"), result.getFirst()
            .modules()
            .getFirst()
            .prerequisites());
    }

    @Test
    void rejectsPartialFilter() {
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        when(userRepository.findByExternalSubject("teacher"))
            .thenReturn(Optional.of(UserAccount.createTeacher(
                UUID.randomUUID(),
                "teacher",
                "Teacher",
                NOW
            )));
        CourseRecommendationQueryService service = new CourseRecommendationQueryService(
            userRepository,
            mock(CourseTemplateRepository.class),
            mock(CourseTemplateVersionRepository.class),
            mock(CourseTemplateModuleRepository.class)
        );

        assertThrows(
            ClassroomOperationException.class,
            () -> service.find("teacher", 4, null)
        );
    }
}
