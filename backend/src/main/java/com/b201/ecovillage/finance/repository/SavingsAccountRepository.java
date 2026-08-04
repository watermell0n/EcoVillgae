package com.b201.ecovillage.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.finance.domain.SavingsAccount;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, UUID> {
    Optional<SavingsAccount> findByClassroomIdAndOpeningTransactionGroupId(UUID classroomId, UUID openingTransactionGroupId);
    List<SavingsAccount> findAllByClassroomIdAndClassroomStudentIdOrderByOpenedAtDesc(UUID classroomId, UUID classroomStudentId);
}
