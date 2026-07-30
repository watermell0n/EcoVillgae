package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;

public interface ClassroomTeacherRepository extends JpaRepository<ClassroomTeacher, UUID> {

    List<ClassroomTeacher> findAllByClassroomIdAndEndedAtIsNull(UUID classroomId);

    List<ClassroomTeacher> findAllByUserIdAndEndedAtIsNull(UUID userId);

    boolean existsByClassroomIdAndUserIdAndRoleAndEndedAtIsNull(
        UUID classroomId,
        UUID userId,
        ClassroomTeacherRole role
    );

    boolean existsByClassroomIdAndUserIdAndEndedAtIsNull(UUID classroomId, UUID userId);

    List<ClassroomTeacher> findAllByClassroomIdAndUserIdAndEndedAtIsNull(
        UUID classroomId,
        UUID userId
    );
}
