package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 선생님 수업 모듈에서 순서대로 넘겨 보여 주는 자료 한 장이다.
 *
 * <p>한 번 담은 자료는 수업 중에 바꾸지 않는다. 수업이 시작된 뒤 순서나 내용이 바뀌면
 * 교사 화면과 학생 화면이 서로 다른 장을 보게 되므로, 넘기기는 커서만 움직인다.
 */
@Entity
@Table(name = "activity_presentation_slides")
public class ActivityPresentationSlide {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "activity_session_id", nullable = false, updatable = false)
    private UUID activitySessionId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Column(nullable = false, length = 120, updatable = false)
    private String title;

    @Column(name = "image_url", nullable = false, columnDefinition = "text", updatable = false)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityPresentationSlide() {
    }

    private ActivityPresentationSlide(
        UUID id,
        UUID activitySessionId,
        int sequenceNumber,
        String title,
        String imageUrl,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.activitySessionId = Objects.requireNonNull(
            activitySessionId,
            "activitySessionId must not be null"
        );
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be 1 or greater");
        }
        this.sequenceNumber = sequenceNumber;
        this.title = required(title, "title");
        if (this.title.length() > 120) {
            throw new IllegalArgumentException("title must not exceed 120 characters");
        }
        this.imageUrl = required(imageUrl, "imageUrl");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static ActivityPresentationSlide of(
        UUID id,
        UUID activitySessionId,
        int sequenceNumber,
        String title,
        String imageUrl,
        Instant createdAt
    ) {
        return new ActivityPresentationSlide(
            id,
            activitySessionId,
            sequenceNumber,
            title,
            imageUrl,
            createdAt
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
