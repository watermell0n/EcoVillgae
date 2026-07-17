package com.b201.bibim.domain.vote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<VoteEntity, UUID> {

    List<VoteEntity> findAllByVoteSessionId(UUID voteSessionId);

    Optional<VoteEntity> findByVoteSessionIdAndStudentId(UUID voteSessionId, UUID studentId);

    boolean existsByVoteSessionIdAndStudentId(UUID voteSessionId, UUID studentId);

    long countByVoteSessionId(UUID voteSessionId);
}

