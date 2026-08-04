package com.b201.ecovillage.classroom.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClassroomModule> findForUpdateByClassroomIdAndModuleCode(
        UUID classroomId,
        String moduleCode
    );

    boolean existsByClassroomIdAndModuleCodeAndStatus(
        UUID classroomId,
        String moduleCode,
        String status
    );
}
