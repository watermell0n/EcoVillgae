package com.b201.ecovillage.classroom.domain;

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

@Entity
@Table(name = "classroom_modules")
public class ClassroomModule {

    // 학생 앱(features/classroom/api.ts)이 진행 중인 퀴즈를 찾을 때 읽는 키입니다.
    public static final String ACTIVE_QUIZ_SESSION_KEY = "active_quiz_session_id";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "module_code", nullable = false, updatable = false, length = 40)
    private String moduleCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false)
    private Map<String, Object> config;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

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

    protected ClassroomModule() {
    }

    private ClassroomModule(
        UUID id,
        UUID classroomId,
        String moduleCode,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.moduleCode = ModuleCatalog.require(moduleCode).code();
        this.config = Map.of();
        this.status = "DISABLED";
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static ClassroomModule disabled(
        UUID id,
        UUID classroomId,
        String moduleCode,
        Instant createdAt
    ) {
        return new ClassroomModule(id, classroomId, moduleCode, createdAt);
    }

    /** A teacher selected this module at classroom creation, but policy input is still required. */
    public static ClassroomModule pendingConfiguration(
        UUID id, UUID classroomId, String moduleCode, Instant createdAt
    ) {
        ClassroomModule module = new ClassroomModule(id, classroomId, moduleCode, createdAt);
        module.status = "PENDING_CONFIGURATION";
        return module;
    }

    public void activate(Map<String, Object> config, Instant changedAt) {
        if (!"DISABLED".equals(status) && !"PENDING_CONFIGURATION".equals(status)) {
            throw new IllegalStateException("Only a disabled or pending module can be activated");
        }
        this.config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
        this.enabledAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.stoppedAt = null;
        this.status = "ACTIVE";
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    /**
     * 학생 앱과 교사 화면은 활성 모듈 config의 active_quiz_session_id로 진행 중인 퀴즈를 찾는다.
     * 세션 행만 만들고 이 값을 갱신하지 않으면 학생은 퀴즈가 열린 사실을 알 수 없다.
     */
    public void trackActiveQuizSession(UUID quizSessionId, Instant changedAt) {
        requireActiveForConfigChange();
        Map<String, Object> next = new LinkedHashMap<>(config);
        next.put(
            ACTIVE_QUIZ_SESSION_KEY,
            Objects.requireNonNull(quizSessionId, "quizSessionId must not be null").toString()
        );
        replaceConfig(next, changedAt);
    }

    public void clearActiveQuizSession(Instant changedAt) {
        requireActiveForConfigChange();
        if (!config.containsKey(ACTIVE_QUIZ_SESSION_KEY)) {
            return;
        }
        Map<String, Object> next = new LinkedHashMap<>(config);
        next.remove(ACTIVE_QUIZ_SESSION_KEY);
        replaceConfig(next, changedAt);
    }

    private void requireActiveForConfigChange() {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an active module can change its config");
        }
    }

    private void replaceConfig(Map<String, Object> next, Instant changedAt) {
        this.config = Map.copyOf(next);
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void disable(Instant changedAt) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an active module can be disabled");
        }
        this.status = "DISABLED";
        this.stoppedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public void beginStopping(Map<String, Object> config, Instant changedAt) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an active module can begin stopping");
        }
        this.config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
        this.status = "STOPPING";
        this.statusChangedAt = Objects.requireNonNull(
            changedAt,
            "changedAt must not be null"
        );
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getConfig() {
        return Map.copyOf(config);
    }

    public Instant getEnabledAt() {
        return enabledAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
