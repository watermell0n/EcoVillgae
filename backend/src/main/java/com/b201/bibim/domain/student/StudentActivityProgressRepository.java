package com.b201.bibim.domain.student;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentActivityProgressRepository
        extends JpaRepository<StudentActivityProgressEntity, UUID> {

    List<StudentActivityProgressEntity> findAllByStudentId(UUID studentId);

    Optional<StudentActivityProgressEntity> findByStudentIdAndActivityCode(
            UUID studentId, String activityCode
    );
}
