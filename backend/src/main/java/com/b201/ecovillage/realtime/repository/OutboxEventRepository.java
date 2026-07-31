package com.b201.ecovillage.realtime.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.realtime.domain.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
