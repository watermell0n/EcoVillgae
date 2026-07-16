package com.b201.bibim.domain.quiz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quizzes")
public class QuizEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "day_number", nullable = false)
    private short dayNumber;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected QuizEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public int getQuestionCount() {
        return questionCount;
    }
}
