package com.b201.ecovillage.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.TeacherCredential;

public interface TeacherCredentialRepository extends JpaRepository<TeacherCredential, UUID> {

    Optional<TeacherCredential> findByEmail(String email);

    boolean existsByEmail(String email);
}
