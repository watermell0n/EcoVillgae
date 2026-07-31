package com.b201.ecovillage.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.b201.ecovillage.ledger.domain.PolicySnapshot;

public interface PolicySnapshotRepository extends Repository<PolicySnapshot, UUID> {

    PolicySnapshot save(PolicySnapshot snapshot);

    Optional<PolicySnapshot> findByClassroomIdAndSnapshotHash(
        UUID classroomId,
        String snapshotHash
    );
}
