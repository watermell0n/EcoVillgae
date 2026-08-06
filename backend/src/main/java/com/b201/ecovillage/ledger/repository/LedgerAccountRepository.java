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

    /**
     * 살아 있는 학생 지갑의 잔액을 작은 값부터 준다. 평균과 중앙값을 함께 뽑아야 하므로
     * 합계가 아니라 목록으로 받는다. 한 학급의 학생 수만큼만 나오므로 크지 않다.
     */
    @Query("""
        select account.currentBalance
          from LedgerAccount account
         where account.classroomId = :classroomId
           and account.accountType = com.b201.ecovillage.ledger.domain.LedgerAccountType.STUDENT_WALLET
           and account.status = com.b201.ecovillage.ledger.domain.LedgerAccountStatus.ACTIVE
         order by account.currentBalance
        """)
    List<Long> findActiveStudentWalletBalances(@Param("classroomId") UUID classroomId);

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
