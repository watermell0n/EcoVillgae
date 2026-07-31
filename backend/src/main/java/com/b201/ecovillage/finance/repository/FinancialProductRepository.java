package com.b201.ecovillage.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.finance.domain.FinancialProduct;

public interface FinancialProductRepository extends JpaRepository<FinancialProduct, UUID> {

    Optional<FinancialProduct> findByClassroomIdAndId(UUID classroomId, UUID id);

    List<FinancialProduct> findAllByClassroomIdOrderByName(UUID classroomId);
}
