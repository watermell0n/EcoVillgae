package com.b201.ecovillage.finance.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.finance.domain.SavingsInstallment;

public interface SavingsInstallmentRepository extends JpaRepository<SavingsInstallment, UUID> { }
