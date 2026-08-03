package com.b201.ecovillage.identity.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.identity.domain.Classroom;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    Optional<Classroom> findByClassroomCode(String classroomCode);

    boolean existsByClassroomCode(String classroomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Classroom> findForUpdateById(UUID id);
}
