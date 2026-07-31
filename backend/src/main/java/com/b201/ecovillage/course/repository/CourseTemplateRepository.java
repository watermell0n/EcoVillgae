package com.b201.ecovillage.course.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.course.domain.CourseTemplate;

public interface CourseTemplateRepository
    extends JpaRepository<CourseTemplate, UUID> {

    List<CourseTemplate> findAllByStatusOrderByGradeMinAscNameAsc(String status);
}
