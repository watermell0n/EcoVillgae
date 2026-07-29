package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;

public interface ClassroomStudentRepository extends JpaRepository<ClassroomStudent, UUID> {

    Optional<ClassroomStudent> findByClassroomIdAndRosterNumberAndRetiredAtIsNull(
        UUID classroomId,
        int rosterNumber
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
