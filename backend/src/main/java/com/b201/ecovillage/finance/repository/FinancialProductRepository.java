package com.b201.ecovillage.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.finance.domain.FinancialProduct;

public interface FinancialProductRepository extends JpaRepository<FinancialProduct, UUID> {

    Optional<FinancialProduct> findByClassroomIdAndId(UUID classroomId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FinancialProduct> findForUpdateByClassroomIdAndId(UUID classroomId, UUID id);

    List<FinancialProduct> findAllByClassroomIdOrderByName(UUID classroomId);
}
