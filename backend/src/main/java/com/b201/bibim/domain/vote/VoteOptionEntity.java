package com.b201.bibim.domain.vote;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vote_options")
public class VoteOptionEntity {

    @Id
    private UUID id;

    @Column(name = "vote_session_id", nullable = false)
    private UUID voteSessionId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "required_amount", nullable = false)
    private long requiredAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected VoteOptionEntity() {
    }

    public static VoteOptionEntity create(
            UUID sessionId,
            String title,
            String description,
            long requiredAmount,
            int displayOrder
    ) {
        VoteOptionEntity option = new VoteOptionEntity();
        option.id = UUID.randomUUID();
        option.voteSessionId = sessionId;
        option.title = title;
        option.description = description;
        option.requiredAmount = requiredAmount;
        option.displayOrder = displayOrder;
        return option;
    }

    public UUID getId() { return id; }
    public UUID getVoteSessionId() { return voteSessionId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getRequiredAmount() { return requiredAmount; }
    public int getDisplayOrder() { return displayOrder; }
}

