package com.b201.ecovillage.course.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.course.domain.CourseTemplateModule;

public interface CourseTemplateModuleRepository
    extends JpaRepository<CourseTemplateModule, UUID> {

    List<CourseTemplateModule>
        findAllByCourseTemplateVersionIdOrderBySortOrderAscModuleCodeAsc(
            UUID courseTemplateVersionId
        );
}
