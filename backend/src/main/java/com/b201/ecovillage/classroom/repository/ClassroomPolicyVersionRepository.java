package com.b201.ecovillage.classroom.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;

public interface ClassroomPolicyVersionRepository
    extends JpaRepository<ClassroomPolicyVersion, UUID> {

    Optional<ClassroomPolicyVersion> findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
        UUID classroomId,
        String policyArea
    );

    List<ClassroomPolicyVersion> findAllByClassroomIdAndSupersededAtIsNullOrderByPolicyArea(
        UUID classroomId
    );
}
