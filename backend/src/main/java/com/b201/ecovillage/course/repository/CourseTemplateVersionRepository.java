package com.b201.ecovillage.course.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.course.domain.CourseTemplateVersion;

public interface CourseTemplateVersionRepository
    extends JpaRepository<CourseTemplateVersion, UUID> {

    Optional<CourseTemplateVersion>
        findTopByCourseTemplateIdAndStatusOrderByVersionDesc(
            UUID courseTemplateId,
            String status
        );
}
