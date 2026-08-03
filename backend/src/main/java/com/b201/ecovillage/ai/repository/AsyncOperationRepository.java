package com.b201.ecovillage.ai.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ai.domain.AsyncOperation;

public interface AsyncOperationRepository extends JpaRepository<AsyncOperation, UUID> {
}
