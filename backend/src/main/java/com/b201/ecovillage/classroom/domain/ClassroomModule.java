package com.b201.ecovillage.classroom.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 교사가 이 모듈을 학생에게 잠갔는지 나타냅니다. 모듈은 계속 ACTIVE이므로 설정과
     * 데이터는 그대로 남고, 학생 화면에서만 회색으로 바뀌어 들어갈 수 없습니다.
     * 학급에서 모듈을 아예 걷어내는 것은 stop-commands(STOPPING)입니다.
     */
    public static final String STUDENT_ACCESS_SUSPENDED_KEY = "student_access_suspended";

    /**
     * INVESTMENT 모듈에서 교사가 쓰겠다고 고른 투자 방식의 목록입니다. 학급은 단기와 장기를
     * 함께 운영할 수 있으므로 값이 두 개일 수 있습니다.
     */
    public static final String INVESTMENT_MODES_KEY = "investment_modes";

    /** 목록이 생기기 전에 쓰던 단일 값입니다. 이전 화면과 이전 데이터를 위해 함께 유지합니다. */
    public static final String INVESTMENT_MODE_KEY = "investment_mode";

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

    /**
     * STOPPING도 다시 켤 수 있게 둔다. 정리 대기 상태에서 되돌릴 방법이 없으면 교사가
     * 실수로 중지한 모듈을 그 학급에서 영구히 못 쓰게 된다.
     */
    public static boolean isActivatable(String status) {
        return "DISABLED".equals(status)
            || "PENDING_CONFIGURATION".equals(status)
            || "STOPPING".equals(status);
    }

    public void activate(Map<String, Object> config, Instant changedAt) {
        if (!isActivatable(status)) {
            throw new IllegalStateException("Only a disabled, pending, or stopping module can be activated");
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

    /**
     * 학생 이용만 잠그거나 다시 엽니다. 상태는 ACTIVE로 유지하므로 예금 계좌·퀴즈 세션처럼
     * 이미 만들어진 데이터를 정리하지 않고 되돌릴 수 있습니다.
     */
    public void changeStudentAccess(boolean suspended, Instant changedAt) {
        requireActiveForConfigChange();
        Map<String, Object> next = new LinkedHashMap<>(config);
        if (suspended) {
            next.put(STUDENT_ACCESS_SUSPENDED_KEY, true);
        } else {
            next.remove(STUDENT_ACCESS_SUSPENDED_KEY);
        }
        replaceConfig(next, changedAt);
    }

    public boolean isStudentAccessSuspended() {
        return Boolean.TRUE.equals(config.get(STUDENT_ACCESS_SUSPENDED_KEY));
    }

    /**
     * 교사가 쓰겠다고 고른 투자 방식을 바꿉니다. 단기만 켜 두었다가 장기를 나중에 더하는
     * 경우가 있어 활성화 이후에도 바꿀 수 있어야 합니다.
     *
     * <p>이미 시나리오를 만든 방식을 목록에서 빼도 그 시장과 학생 보유 내역은 지우지 않습니다.
     * 되돌릴 수 없는 정리는 stop-commands가 담당합니다.
     */
    public void changeInvestmentModes(List<String> investmentModes, Instant changedAt) {
        requireActiveForConfigChange();
        List<String> modes = List.copyOf(
            Objects.requireNonNull(investmentModes, "investmentModes must not be null")
        );
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("At least one investment mode must remain selected");
        }
        Map<String, Object> next = new LinkedHashMap<>(config);
        next.put(INVESTMENT_MODES_KEY, modes);
        next.put(INVESTMENT_MODE_KEY, modes.get(0));
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
