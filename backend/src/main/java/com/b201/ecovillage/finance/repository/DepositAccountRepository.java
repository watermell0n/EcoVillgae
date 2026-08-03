package com.b201.ecovillage.finance.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.DepositAccountStatus;

public interface DepositAccountRepository extends JpaRepository<DepositAccount, UUID> {

    Optional<DepositAccount> findByClassroomIdAndOpeningTransactionGroupId(
        UUID classroomId,
        UUID openingTransactionGroupId
    );

    Optional<DepositAccount> findByClassroomIdAndClosingTransactionGroupId(
        UUID classroomId,
        UUID closingTransactionGroupId
    );

    boolean existsByClassroomIdAndStatus(
        UUID classroomId,
        DepositAccountStatus status
    );

    List<DepositAccount> findAllByClassroomIdAndClassroomStudentIdOrderByOpenedAtDesc(
        UUID classroomId,
        UUID classroomStudentId
    );

    List<DepositAccount>
        findTop100ByClassroomIdAndStatusAndMaturesAtLessThanEqualOrderByMaturesAt(
            UUID classroomId,
            DepositAccountStatus status,
            Instant maturesAt
        );

    @Query("""
        select distinct account.classroomId
          from DepositAccount account
         where account.status = :status
           and account.maturesAt <= :maturesAt
        """)
    List<UUID> findDueClassroomIds(
        @Param("status") DepositAccountStatus status,
        @Param("maturesAt") Instant maturesAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select account
          from DepositAccount account
         where account.classroomId = :classroomId
           and account.id = :accountId
        """)
    Optional<DepositAccount> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("accountId") UUID accountId
    );
}
