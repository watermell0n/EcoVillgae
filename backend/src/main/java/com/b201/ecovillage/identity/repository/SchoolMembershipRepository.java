package com.b201.ecovillage.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.SchoolMembership;

public interface SchoolMembershipRepository extends JpaRepository<SchoolMembership, UUID> {

    List<SchoolMembership> findAllBySchoolIdAndUserIdAndStatus(
        UUID schoolId,
        UUID userId,
        AccountStatus status
    );
}
