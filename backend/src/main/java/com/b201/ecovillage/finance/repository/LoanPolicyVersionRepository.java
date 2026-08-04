package com.b201.ecovillage.finance.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.finance.domain.LoanPolicyVersion;

public interface LoanPolicyVersionRepository extends JpaRepository<LoanPolicyVersion, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select policy
          from LoanPolicyVersion policy
         where policy.classroomId = :classroomId
           and policy.id = :policyId
        """)
    Optional<LoanPolicyVersion> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("policyId") UUID policyId
    );

    Optional<LoanPolicyVersion> findTopByClassroomIdAndStatusOrderByVersionDesc(
        UUID classroomId,
        String status
    );
}
