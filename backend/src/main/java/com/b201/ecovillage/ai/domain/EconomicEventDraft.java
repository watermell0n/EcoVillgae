package com.b201.ecovillage.ai.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 교사가 검토할 경제 이벤트 초안이다. 템플릿과 효과는 규칙 엔진이 정하고
 * 제목·이야기·선택지는 AI가 만든다. 초안은 항상 DRAFT로 저장하며 게시하지 않는다.
 */
@Entity
@Table(name = "economic_event_drafts")
public class EconomicEventDraft {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "ai_run_id", updatable = false)
    private UUID aiRunId;

    @Column(name = "activity_session_id", updatable = false)
    private UUID activitySessionId;

    @Column(name = "source_type", nullable = false, updatable = false, length = 40)
    private String sourceType;

    @Column(name = "source_proposal_id", updatable = false)
    private UUID sourceProposalId;

    @Column(name = "investment_price_step_id", updatable = false)
    private UUID investmentPriceStepId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "economic_effect", nullable = false)
    private Map<String, Object> economicEffect;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "revealed_at")
    private Instant revealedAt;

    @Column(nullable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EconomicEventDraft() {
    }

    public static EconomicEventDraft draft(
        UUID id,
        UUID classroomId,
        UUID aiRunId,
        String sourceType,
        UUID sourceProposalId,
        String title,
        String content,
        Map<String, Object> economicEffect,
        Instant now
    ) {
        EconomicEventDraft draft = new EconomicEventDraft();
        draft.id = Objects.requireNonNull(id);
        draft.classroomId = Objects.requireNonNull(classroomId);
        draft.aiRunId = aiRunId;
        draft.sourceType = Objects.requireNonNull(sourceType);
        draft.sourceProposalId = sourceProposalId;
        draft.title = required(title, 160);
        draft.content = Objects.requireNonNull(content);
        draft.economicEffect = new LinkedHashMap<>(economicEffect);
        draft.status = "DRAFT";
        draft.statusChangedAt = now;
        draft.createdAt = now;
        draft.updatedAt = now;
        return draft;
    }

    /**
     * 교사가 승인했고 정책 효과가 없는 이벤트다. 학생에게 바로 게시한다.
     */
    public void approve(Instant now) {
        transition("APPROVED", now);
        revealedAt = now;
    }

    /**
     * 교사가 승인하고 정책 효과까지 반영한 이벤트다. 학생에게 바로 게시한다.
     */
    public void applyEffect(Instant now) {
        transition("APPLIED", now);
        revealedAt = now;
    }

    /**
     * 교사가 거절한 이벤트다. 게시하지 않으며 도메인 변경도 남기지 않는다.
     */
    public void reject(Instant now) {
        transition("CANCELED", now);
    }

    public boolean isUnderReview() {
        return "DRAFT".equals(status);
    }

    private void transition(String nextStatus, Instant now) {
        if (!isUnderReview()) {
            throw new IllegalStateException(
                "The economic event draft is no longer under review: " + status
            );
        }
        status = nextStatus;
        statusChangedAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Instant getRevealedAt() {
        return revealedAt;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getAiRunId() {
        return aiRunId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceProposalId() {
        return sourceProposalId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getEconomicEffect() {
        return economicEffect;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static String required(String value, int maximumLength) {
        Objects.requireNonNull(value);
        return value.substring(0, Math.min(value.length(), maximumLength));
    }
}
