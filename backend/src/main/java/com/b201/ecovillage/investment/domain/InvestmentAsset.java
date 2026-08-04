package com.b201.ecovillage.investment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 시나리오 판본에 속한 가상 종목이다.
 *
 * <p>이름과 기호는 AI 뉴스 입력으로 그대로 전달되고 출력에서도 바뀌지 않아야 한다. 그래서 컬럼
 * 길이보다 좁은 AI 계약 제약을 저장 시점에 함께 검사한다. 컬럼은 name varchar(120)이지만
 * 계약은 100자까지이고, symbol은 varchar(20)이지만 계약은 대문자로 시작하는 2~10자다.
 */
@Entity
@Table(name = "investment_assets")
public class InvestmentAsset {

    public static final String ACTIVE = "ACTIVE";
    public static final String DELISTED = "DELISTED";

    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "scenario_version_id", nullable = false, updatable = false)
    private UUID scenarioVersionId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "fictional_company_profile", nullable = false, length = 1000)
    private String fictionalCompanyProfile;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

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

    protected InvestmentAsset() {
    }

    public static InvestmentAsset active(
        UUID id,
        UUID classroomId,
        UUID scenarioVersionId,
        String symbol,
        String name,
        int displayOrder,
        Instant now
    ) {
        return active(
            id,
            classroomId,
            scenarioVersionId,
            symbol,
            name,
            name + "은 학급 투자 활동을 위해 만든 가상 기업입니다.",
            displayOrder,
            now
        );
    }

    public static InvestmentAsset active(
        UUID id,
        UUID classroomId,
        UUID scenarioVersionId,
        String symbol,
        String name,
        String fictionalCompanyProfile,
        int displayOrder,
        Instant now
    ) {
        InvestmentAsset asset = new InvestmentAsset();
        asset.id = Objects.requireNonNull(id);
        asset.classroomId = Objects.requireNonNull(classroomId);
        asset.scenarioVersionId = Objects.requireNonNull(scenarioVersionId);
        asset.symbol = requireContractSymbol(symbol);
        asset.name = requireContractName(name);
        asset.fictionalCompanyProfile = requireCompanyProfile(fictionalCompanyProfile);
        asset.displayOrder = displayOrder;
        asset.status = ACTIVE;
        asset.statusChangedAt = now;
        asset.createdAt = now;
        asset.updatedAt = now;
        return asset;
    }

    private static String requireCompanyProfile(String value) {
        Objects.requireNonNull(value, "fictionalCompanyProfile must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new IllegalArgumentException(
                "fictional company profile must be 1 to 1000 characters"
            );
        }
        return normalized;
    }

    private static String requireContractName(String value) {
        Objects.requireNonNull(value, "name must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "asset name must be 1 to " + MAX_NAME_LENGTH + " characters for the AI contract"
            );
        }
        return normalized;
    }

    private static String requireContractSymbol(String value) {
        Objects.requireNonNull(value, "symbol must not be null");
        String normalized = value.trim();
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "asset symbol must match " + SYMBOL_PATTERN.pattern() + " for the AI contract"
            );
        }
        return normalized;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getScenarioVersionId() {
        return scenarioVersionId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getFictionalCompanyProfile() {
        return fictionalCompanyProfile;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getStatus() {
        return status;
    }
}
