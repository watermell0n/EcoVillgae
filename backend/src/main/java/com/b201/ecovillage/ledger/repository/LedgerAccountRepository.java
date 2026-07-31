package com.b201.ecovillage.ledger.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;

public interface LedgerAccountRepository extends Repository<LedgerAccount, UUID> {

    LedgerAccount save(LedgerAccount account);

    Optional<LedgerAccount> findById(UUID id);

    Optional<LedgerAccount> findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
        UUID classroomId,
        UUID classroomStudentId,
        LedgerAccountType accountType,
        LedgerAccountStatus status
    );

    Optional<LedgerAccount> findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
        UUID classroomId,
        String systemOwnerKey,
        LedgerAccountType accountType,
        LedgerAccountStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select account
          from LedgerAccount account
         where account.classroomId = :classroomId
           and account.id in :accountIds
         order by account.id
        """)
    List<LedgerAccount> findAllForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("accountIds") Collection<UUID> accountIds
    );
}
