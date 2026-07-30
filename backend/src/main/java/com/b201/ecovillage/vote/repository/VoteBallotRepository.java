package com.b201.ecovillage.vote.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.vote.domain.VoteBallot;

public interface VoteBallotRepository extends JpaRepository<VoteBallot, UUID> {

    List<VoteBallot> findAllByClassroomIdAndVoteId(UUID classroomId, UUID voteId);

    Optional<VoteBallot> findByClassroomIdAndVoteIdAndEligibilityTokenHash(
        UUID classroomId,
        UUID voteId,
        String eligibilityTokenHash
    );

    long countByClassroomIdAndVoteId(UUID classroomId, UUID voteId);
}
