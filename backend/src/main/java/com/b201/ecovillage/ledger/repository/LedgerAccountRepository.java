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

public interface LedgerAccountRepository extends Repository<LedgerAccount, UUID> {

    LedgerAccount save(LedgerAccount account);

    Optional<LedgerAccount> findById(UUID id);

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
