package com.b201.ecovillage.character.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.character.domain.StudentCharacterProfile;

public interface StudentCharacterProfileRepository
    extends JpaRepository<StudentCharacterProfile, UUID> {

    Optional<StudentCharacterProfile> findByClassroomIdAndClassroomStudentId(
        UUID classroomId,
        UUID classroomStudentId
    );
}
