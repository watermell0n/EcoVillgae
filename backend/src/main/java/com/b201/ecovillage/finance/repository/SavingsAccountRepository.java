package com.b201.ecovillage.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.b201.ecovillage.finance.domain.SavingsAccountStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.finance.domain.SavingsAccount;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, UUID> {
    Optional<SavingsAccount> findByClassroomIdAndOpeningTransactionGroupId(UUID classroomId, UUID openingTransactionGroupId);
    List<SavingsAccount> findAllByClassroomIdAndClassroomStudentIdOrderByOpenedAtDesc(UUID classroomId, UUID classroomStudentId);
    List<SavingsAccount> findAllByClassroomIdAndStatus(UUID classroomId, SavingsAccountStatus status);
    @org.springframework.data.jpa.repository.Query("select distinct account.classroomId from SavingsAccount account where account.status = :status")
    List<UUID> findActiveClassroomIds(@org.springframework.data.repository.query.Param("status") SavingsAccountStatus status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select account from SavingsAccount account where account.classroomId = :classroomId and account.id = :accountId")
    Optional<SavingsAccount> findForUpdate(@org.springframework.data.repository.query.Param("classroomId") UUID classroomId, @org.springframework.data.repository.query.Param("accountId") UUID accountId);
}
