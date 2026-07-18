package com.b201.bibim.domain.fund;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(VoteEligibleStudentEntity.Key.class)
@Table(name = "vote_eligible_students")
public class VoteEligibleStudentEntity {

    @Id
    @Column(name = "vote_session_id")
    private UUID voteSessionId;

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "eligible_at", nullable = false)
    private Instant eligibleAt;

    protected VoteEligibleStudentEntity() {
    }

    public static VoteEligibleStudentEntity create(UUID voteSessionId, UUID studentId) {
        VoteEligibleStudentEntity entity = new VoteEligibleStudentEntity();
        entity.voteSessionId = voteSessionId;
        entity.studentId = studentId;
        entity.eligibleAt = Instant.now();
        return entity;
    }

    public UUID getStudentId() { return studentId; }

    public static class Key implements Serializable {
        private UUID voteSessionId;
        private UUID studentId;

        public Key() {
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Key key)) return false;
            return Objects.equals(voteSessionId, key.voteSessionId)
                    && Objects.equals(studentId, key.studentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(voteSessionId, studentId);
        }
    }
}
