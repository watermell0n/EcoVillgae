package com.b201.bibim.domain.fund;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteEligibleStudentRepository
        extends JpaRepository<VoteEligibleStudentEntity, VoteEligibleStudentEntity.Key> {

    List<VoteEligibleStudentEntity> findAllByVoteSessionId(UUID voteSessionId);

    boolean existsByVoteSessionIdAndStudentId(UUID voteSessionId, UUID studentId);

    long countByVoteSessionId(UUID voteSessionId);
}
