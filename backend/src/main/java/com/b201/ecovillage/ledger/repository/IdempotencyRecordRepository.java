package com.b201.ecovillage.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.ledger.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends Repository<IdempotencyRecord, UUID> {

    IdempotencyRecord save(IdempotencyRecord record);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select record
          from IdempotencyRecord record
         where record.classroomId = :classroomId
           and record.actorId = :actorId
           and record.routeKey = :routeKey
           and record.idempotencyKey = :idempotencyKey
        """)
    Optional<IdempotencyRecord> findForReplay(
        @Param("classroomId") UUID classroomId,
        @Param("actorId") UUID actorId,
        @Param("routeKey") String routeKey,
        @Param("idempotencyKey") String idempotencyKey
    );
}
