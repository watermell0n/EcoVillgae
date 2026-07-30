package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;

public interface ClassroomStudentRepository extends JpaRepository<ClassroomStudent, UUID> {

    Optional<ClassroomStudent> findByClassroomIdAndRosterNumberAndRetiredAtIsNull(
        UUID classroomId,
        int rosterNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select student
          from ClassroomStudent student
         where student.classroomId = :classroomId
           and student.rosterNumber = :rosterNumber
           and student.retiredAt is null
        """)
    Optional<ClassroomStudent> findForLogin(
        @Param("classroomId") UUID classroomId,
        @Param("rosterNumber") int rosterNumber
    );

    Optional<ClassroomStudent> findByClassroomIdAndNicknameIgnoreCaseAndRetiredAtIsNull(
        UUID classroomId,
        String nickname
    );

    List<ClassroomStudent> findAllByClassroomIdAndStatusOrderByRosterNumber(
        UUID classroomId,
        ClassroomStudentStatus status
    );
}
