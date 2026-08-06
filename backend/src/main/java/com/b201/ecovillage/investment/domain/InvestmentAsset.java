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

    /** 하루에 내려갈 수 있는 최저 등락률(bps)이다. 손해 쪽이므로 보통 음수다. */
    @Column(name = "min_return_bps", nullable = false)
    private int minReturnBps;

    /** 하루에 올라갈 수 있는 최고 등락률(bps)이다. */
    @Column(name = "max_return_bps", nullable = false)
    private int maxReturnBps;

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
        return active(
            id, classroomId, scenarioVersionId, symbol, name, fictionalCompanyProfile,
            displayOrder, 0, 0, now
        );
    }

    /**
     * 하루 변동 범위까지 정해 만든다. 학생에게 "이 기업은 하루에 어디까지 오르내릴 수
     * 있는지"를 보여 주려면 가격 단계와 별개로 종목 자체가 범위를 알고 있어야 한다.
     */
    public static InvestmentAsset active(
        UUID id,
        UUID classroomId,
        UUID scenarioVersionId,
        String symbol,
        String name,
        String fictionalCompanyProfile,
        int displayOrder,
        int minReturnBps,
        int maxReturnBps,
        Instant now
    ) {
        if (minReturnBps > maxReturnBps) {
            throw new IllegalArgumentException("min return must not exceed max return");
        }
        InvestmentAsset asset = new InvestmentAsset();
        asset.minReturnBps = minReturnBps;
        asset.maxReturnBps = maxReturnBps;
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

    /**
     * 교사가 학생 눈높이에 맞게 기업 이름과 소개 문장을 고친다.
     *
     * <p>기호(symbol)와 가격 흐름은 그대로 둔다. 교사가 정한 1주 시작가와 변동 범위가 기호로
     * 묶여 있고, 이미 만들어 둔 가격 단계도 종목에 붙어 있기 때문이다. 바뀌는 것은 교사와
     * 학생 화면에 보이는 글뿐이다.
     */
    public void rewrite(String name, String fictionalCompanyProfile, Instant now) {
        this.name = requireContractName(name);
        this.fictionalCompanyProfile = requireCompanyProfile(fictionalCompanyProfile);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** 하루 변동 범위를 다시 정한다. 아직 시작하지 않은 시장에서만 부른다. */
    public void reband(int minReturnBps, int maxReturnBps, Instant now) {
        if (minReturnBps > maxReturnBps) {
            throw new IllegalArgumentException("min return must not exceed max return");
        }
        this.minReturnBps = minReturnBps;
        this.maxReturnBps = maxReturnBps;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
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

    public int getMinReturnBps() {
        return minReturnBps;
    }

    public int getMaxReturnBps() {
        return maxReturnBps;
    }

    public String getStatus() {
        return status;
    }
}
