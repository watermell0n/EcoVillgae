package com.b201.ecovillage.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.School;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findBySchoolCode(String schoolCode);
}
