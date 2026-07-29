package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.ClassroomTeacher;

public interface ClassroomTeacherRepository extends JpaRepository<ClassroomTeacher, UUID> {

    List<ClassroomTeacher> findAllByClassroomIdAndEndedAtIsNull(UUID classroomId);

    List<ClassroomTeacher> findAllByUserIdAndEndedAtIsNull(UUID userId);
}
