package com.b201.bibim.domain.vote;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "votes")
public class VoteEntity {

    @Id
    private UUID id;

    @Column(name = "vote_session_id", nullable = false)
    private UUID voteSessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VoteEntity() {
    }

    public static VoteEntity cast(UUID sessionId, UUID studentId, UUID optionId) {
        VoteEntity vote = new VoteEntity();
        vote.id = UUID.randomUUID();
        vote.voteSessionId = sessionId;
        vote.studentId = studentId;
        vote.optionId = optionId;
        vote.createdAt = Instant.now();
        return vote;
    }

    public UUID getStudentId() { return studentId; }
    public UUID getOptionId() { return optionId; }
}

