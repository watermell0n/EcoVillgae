package com.b201.ecovillage.ai.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.contract.BalancingContract.AllowedAction;
import com.b201.ecovillage.ai.contract.BalancingContract.MetricCode;
import com.b201.ecovillage.ai.contract.BalancingContract.TargetType;
import com.b201.ecovillage.ai.contract.BalancingContract.ValueUnit;
import com.b201.ecovillage.ruleengine.application.ClassroomRuleRecommendationService;

@Service
public class BalancingAllowedActionService {

    private static final Set<MetricCode> PRODUCT_RELATED = Set.of(
        MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS,
        MetricCode.ISSUANCE_RECOVERY_RATE_BPS,
        MetricCode.CONSUMPTION_RATE_BPS,
        MetricCode.PRODUCT_SELL_THROUGH_RATE_BPS,
        MetricCode.PRODUCT_STOCKOUT_SHARE_BPS
    );
    private static final Set<MetricCode> FINANCE_RELATED = Set.of(
        MetricCode.FINANCIAL_PARTICIPATION_RATE_BPS,
        MetricCode.PAYMENT_MISS_RATE_BPS
    );

    /** 한 번의 조정으로 움직일 수 있는 최대 폭이다. 금리가 갑자기 뛰면 학생이 이해하지 못한다. */
    private static final long MAX_RATE_STEP_BPS = 200L;
    /** 목표를 알 수 없을 때 쓰는 폭이다. 예전과 같게 두어 목표가 없으면 동작이 달라지지 않는다. */
    private static final long DEFAULT_RATE_STEP_BPS = 100L;

    private final JdbcTemplate jdbcTemplate;
    private final ClassroomRuleRecommendationService recommendationService;

    public BalancingAllowedActionService(
        JdbcTemplate jdbcTemplate,
        ClassroomRuleRecommendationService recommendationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.recommendationService = recommendationService;
    }

    public List<AllowedAction> find(
        UUID classroomId,
        Instant dataAsOf,
        Set<MetricCode> requestedCodes
    ) {
        List<AllowedAction> result = new ArrayList<>();
        if (requestedCodes.stream().anyMatch(PRODUCT_RELATED::contains)) {
            result.addAll(productActions(classroomId, dataAsOf));
        }
        if (requestedCodes.stream().anyMatch(FINANCE_RELATED::contains)) {
            // 권장값은 여기서 한 번만 구한다. 결과를 훑는 도중에 다시 조회하면 열어 둔 ResultSet과
            // 같은 커넥션을 쓰게 되어 트랜잭션 안에서 깨질 수 있다.
            RateTargets targets = rateTargets(classroomId, dataAsOf);
            result.addAll(financialActions(classroomId, dataAsOf, targets));
            result.addAll(loanActions(classroomId, dataAsOf, targets));
        }
        return result.stream().limit(100).toList();
    }

    private RateTargets rateTargets(UUID classroomId, Instant dataAsOf) {
        return new RateTargets(
            recommendationService.recommendedRateBps(classroomId, "DEPOSIT", dataAsOf).orElse(null),
            recommendationService.recommendedRateBps(classroomId, "SAVINGS", dataAsOf).orElse(null),
            recommendationService.recommendedRateBps(classroomId, "LOAN", dataAsOf).orElse(null)
        );
    }

    private record RateTargets(Long deposit, Long savings, Long loan) {
    }

    /**
     * 지금 값에서 규칙 엔진이 계산한 목표 쪽으로만 움직일 수 있는 범위를 만든다.
     *
     * <p>예전에는 목표를 모른 채 지금 값 ±1%p를 열어 두어, AI가 올려야 할 때 내리는 제안도 낼 수
     * 있었다. 이제는 목표가 위에 있으면 위쪽만, 아래에 있으면 아래쪽만 열린다. 한 번에 너무 크게
     * 움직이지 않도록 폭은 {@value #MAX_RATE_STEP_BPS} bps에서 끊는다. 목표를 계산할 수 없으면
     * 예전과 같은 양쪽 ±{@value #DEFAULT_RATE_STEP_BPS} bps를 쓴다.
     */
    private static RateWindow rateWindow(long current, Long target) {
        if (target == null) {
            return new RateWindow(
                Math.max(0L, current - DEFAULT_RATE_STEP_BPS),
                Math.min(10_000L, current + DEFAULT_RATE_STEP_BPS),
                null
            );
        }
        long bounded = current <= target
            ? Math.min(target, current + MAX_RATE_STEP_BPS)
            : Math.max(target, current - MAX_RATE_STEP_BPS);
        long min = Math.max(0L, Math.min(current, bounded));
        long max = Math.min(10_000L, Math.max(current, bounded));
        return new RateWindow(min, max, target);
    }

    /** 교사가 조정안을 볼 때 "어디로 가는 중인지"를 함께 보도록 권장값을 이름에 붙인다. */
    private static String withTarget(String label, Long target) {
        if (target == null) {
            return label;
        }
        String suffix = " (권장 " + percentText(target) + ")";
        String base = label.length() + suffix.length() <= 90
            ? label
            : label.substring(0, 90 - suffix.length());
        return base + suffix;
    }

    private static String percentText(long bps) {
        return java.math.BigDecimal.valueOf(bps)
            .divide(java.math.BigDecimal.valueOf(100L), 2, java.math.RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    private record RateWindow(long min, long max, Long target) {
    }

    private List<AllowedAction> productActions(UUID classroomId, Instant dataAsOf) {
        try {
            return jdbcTemplate.query(
                """
                    select p.id, p.name, v.unit_price, s.available_quantity
                      from store_products p
                      join store_product_versions v on v.store_product_id = p.id
                      join store_stocks s on s.store_product_id = p.id
                     where p.classroom_id = ?
                       and p.status = 'PUBLISHED'
                       and v.published_at <= ?
                       and (v.retired_at is null or v.retired_at > ?)
                       and v.version = (
                           select max(v2.version)
                             from store_product_versions v2
                            where v2.store_product_id = p.id
                              and v2.published_at <= ?
                              and (v2.retired_at is null or v2.retired_at > ?)
                       )
                     order by p.name
                    """,
                (rs, row) -> productActions(rs),
                classroomId,
                ts(dataAsOf),
                ts(dataAsOf),
                ts(dataAsOf),
                ts(dataAsOf)
            ).stream().flatMap(List::stream).toList();
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    private List<AllowedAction> productActions(ResultSet rs) throws SQLException {
        UUID id = uuid(rs, "id");
        String label = label(rs.getString("name"));
        long price = rs.getLong("unit_price");
        long stock = rs.getLong("available_quantity");
        List<AllowedAction> result = new ArrayList<>();
        if (price > 0) {
            result.add(new AllowedAction(
                "product-price-" + id,
                TargetType.PRODUCT_PRICE,
                id,
                price,
                percentageMinimum(price, 90),
                percentageMaximum(price, 110),
                ValueUnit.AMOUNT,
                label + " 가격"
            ));
        }
        if (stock > 0) {
            result.add(new AllowedAction(
                "product-stock-" + id,
                TargetType.PRODUCT_STOCK,
                id,
                stock,
                percentageMinimum(stock, 80),
                percentageMaximum(stock, 120),
                ValueUnit.COUNT,
                label + " 재고"
            ));
        }
        return result;
    }

    private List<AllowedAction> financialActions(
        UUID classroomId,
        Instant dataAsOf,
        RateTargets targets
    ) {
        try {
            return jdbcTemplate.query(
                """
                    select p.id, p.name, p.product_type, v.interest_rate_bps
                      from financial_products p
                      join financial_product_versions v on v.financial_product_id = p.id
                     where p.classroom_id = ?
                       and p.status = 'PUBLISHED'
                       and v.status = 'PUBLISHED'
                       and v.published_at <= ?
                       and v.version = (
                           select max(v2.version)
                             from financial_product_versions v2
                            where v2.financial_product_id = p.id
                              and v2.status = 'PUBLISHED'
                              and v2.published_at <= ?
                       )
                     order by p.name
                    """,
                (rs, row) -> {
                    UUID id = uuid(rs, "id");
                    long rate = rs.getLong("interest_rate_bps");
                    String type = rs.getString("product_type");
                    boolean savings = "SAVINGS".equals(type);
                    RateWindow window = rateWindow(
                        rate,
                        savings ? targets.savings() : targets.deposit()
                    );
                    return new AllowedAction(
                        type.toLowerCase() + "-rate-" + id,
                        savings ? TargetType.SAVINGS_RATE : TargetType.DEPOSIT_RATE,
                        id,
                        rate,
                        window.min(),
                        window.max(),
                        ValueUnit.RATE_BPS,
                        withTarget(label(rs.getString("name")) + " 금리", window.target())
                    );
                },
                classroomId,
                ts(dataAsOf),
                ts(dataAsOf)
            );
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    private List<AllowedAction> loanActions(
        UUID classroomId,
        Instant dataAsOf,
        RateTargets targets
    ) {
        try {
            return jdbcTemplate.query(
                """
                    select id, interest_rate_bps
                      from loan_policy_versions
                     where classroom_id = ?
                       and status = 'ACTIVE'
                       and effective_from <= ?
                       and (superseded_at is null or superseded_at > ?)
                     order by version desc
                     fetch first 1 row only
                    """,
                (rs, row) -> {
                    UUID id = uuid(rs, "id");
                    long rate = rs.getLong("interest_rate_bps");
                    RateWindow window = rateWindow(rate, targets.loan());
                    return new AllowedAction(
                        "loan-rate-" + id,
                        TargetType.LOAN_RATE,
                        id,
                        rate,
                        window.min(),
                        window.max(),
                        ValueUnit.RATE_BPS,
                        withTarget("대출 기준 금리", window.target())
                    );
                },
                classroomId,
                ts(dataAsOf),
                ts(dataAsOf)
            );
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    private static long percentageMinimum(long value, int percent) {
        return Math.max(0, value * percent / 100);
    }

    private static long percentageMaximum(long value, int percent) {
        return Math.max(value, (value * percent + 99) / 100);
    }

    private static String label(String value) {
        String normalized = value == null || value.isBlank() ? "대상" : value.trim();
        return normalized.substring(0, Math.min(normalized.length(), 90));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    /**
     * timestamptz 파라미터는 Timestamp로 넘긴다. PostgreSQL 드라이버는 Instant의 SQL 타입을
     * 추론하지 못해 "Can't infer the SQL type" 오류를 낸다.
     */
    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
