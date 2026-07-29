package com.b201.ecovillage.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.StudentCredential;

public interface StudentCredentialRepository extends JpaRepository<StudentCredential, UUID> {

    Optional<StudentCredential> findByClassroomIdAndClassroomStudentId(
        UUID classroomId,
        UUID classroomStudentId
    );
}
