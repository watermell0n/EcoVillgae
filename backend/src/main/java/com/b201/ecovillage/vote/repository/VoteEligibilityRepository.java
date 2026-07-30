package com.b201.ecovillage.vote.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.b201.ecovillage.vote.domain.VoteEligibility;

public interface VoteEligibilityRepository
    extends JpaRepository<VoteEligibility, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoteEligibility>
        findForUpdateByClassroomIdAndVoteIdAndClassroomStudentId(
            UUID classroomId,
            UUID voteId,
            UUID classroomStudentId
        );

    Optional<VoteEligibility> findByClassroomIdAndVoteIdAndClassroomStudentId(
        UUID classroomId,
        UUID voteId,
        UUID classroomStudentId
    );

    List<VoteEligibility> findAllByClassroomIdAndVoteId(
        UUID classroomId,
        UUID voteId
    );

    long countByClassroomIdAndVoteId(UUID classroomId, UUID voteId);
}
