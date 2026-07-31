package com.b201.ecovillage.vote.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.vote.domain.VoteOption;

public interface VoteOptionRepository extends JpaRepository<VoteOption, UUID> {

    List<VoteOption> findAllByClassroomIdAndVoteIdOrderBySortOrder(
        UUID classroomId,
        UUID voteId
    );

    Optional<VoteOption> findByClassroomIdAndVoteIdAndId(
        UUID classroomId,
        UUID voteId,
        UUID id
    );
}
