package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.StudentSessionStatus;

public interface StudentSessionRepository extends JpaRepository<StudentSession, UUID> {

    List<StudentSession> findAllByClassroomStudentIdAndStatus(
        UUID classroomStudentId,
        StudentSessionStatus status
    );

    Optional<StudentSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from StudentSession session where session.tokenHash = :tokenHash")
    Optional<StudentSession> findForRefreshByTokenHash(
        @Param("tokenHash") String tokenHash
    );
}
