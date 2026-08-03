package com.b201.ecovillage.classroom.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.ClassroomModule;

public interface ClassroomModuleRepository extends JpaRepository<ClassroomModule, UUID> {

    List<ClassroomModule> findAllByClassroomIdAndStatusOrderByModuleCode(
        UUID classroomId,
        String status
    );

    List<ClassroomModule> findAllByClassroomIdOrderByModuleCode(UUID classroomId);

    Optional<ClassroomModule> findByClassroomIdAndModuleCode(
        UUID classroomId,
        String moduleCode
    );

    boolean existsByClassroomIdAndModuleCodeAndStatus(
        UUID classroomId,
        String moduleCode,
        String status
    );
}
