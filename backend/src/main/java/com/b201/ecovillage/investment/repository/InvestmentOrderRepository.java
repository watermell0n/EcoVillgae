package com.b201.ecovillage.investment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.investment.domain.InvestmentOrder;

public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrder, UUID> {
    Optional<InvestmentOrder> findByClassroomIdAndTransactionGroupId(
        UUID classroomId,
        UUID transactionGroupId
    );

    List<InvestmentOrder> findAllByClassroomIdAndClassroomStudentIdAndInvestmentAssetIdOrderBySubmittedAtAsc(
        UUID classroomId,
        UUID classroomStudentId,
        UUID investmentAssetId
    );
}
