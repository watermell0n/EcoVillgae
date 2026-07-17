package com.b201.bibim.domain.room;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "module_states")
public class ModuleStateEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "current_page_id")
    private UUID currentPageId;

    @Column(name = "module_type", nullable = false, length = 40)
    private String moduleType;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "current_page_order")
    private Integer currentPageOrder;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModuleStateEntity() {
    }

    public static ModuleStateEntity guided(
            UUID sessionId,
            UUID pageId,
            int pageOrder,
            UUID teacherId
    ) {
        ModuleStateEntity moduleState = new ModuleStateEntity();
        moduleState.id = UUID.randomUUID();
        moduleState.sessionId = sessionId;
        moduleState.moduleType = "KNOWLEDGE";
        moduleState.changePage(pageId, pageOrder, teacherId);
        return moduleState;
    }

    public void changePage(UUID pageId, int pageOrder, UUID teacherId) {
        this.currentPageId = pageId;
        this.currentPageOrder = pageOrder;
        this.updatedBy = teacherId;
        this.state = "GUIDED";
        this.updatedAt = Instant.now();
    }

    public UUID getCurrentPageId() {
        return currentPageId;
    }
}
