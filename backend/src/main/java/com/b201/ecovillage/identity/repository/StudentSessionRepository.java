package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.StudentSessionStatus;

public interface StudentSessionRepository extends JpaRepository<StudentSession, UUID> {

    List<StudentSession> findAllByClassroomStudentIdAndStatus(
        UUID classroomStudentId,
        StudentSessionStatus status
    );
}
