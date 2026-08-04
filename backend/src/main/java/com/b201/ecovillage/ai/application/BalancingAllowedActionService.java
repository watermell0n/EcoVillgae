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

    private final JdbcTemplate jdbcTemplate;

    public BalancingAllowedActionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            result.addAll(financialActions(classroomId, dataAsOf));
            result.addAll(loanActions(classroomId, dataAsOf));
        }
        return result.stream().limit(100).toList();
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

    private List<AllowedAction> financialActions(UUID classroomId, Instant dataAsOf) {
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
                    TargetType targetType = "SAVINGS".equals(type)
                        ? TargetType.SAVINGS_RATE
                        : TargetType.DEPOSIT_RATE;
                    return new AllowedAction(
                        type.toLowerCase() + "-rate-" + id,
                        targetType,
                        id,
                        rate,
                        Math.max(0, rate - 100),
                        Math.min(10_000, rate + 100),
                        ValueUnit.RATE_BPS,
                        label(rs.getString("name")) + " 금리"
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

    private List<AllowedAction> loanActions(UUID classroomId, Instant dataAsOf) {
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
                    return new AllowedAction(
                        "loan-rate-" + id,
                        TargetType.LOAN_RATE,
                        id,
                        rate,
                        Math.max(0, rate - 100),
                        Math.min(10_000, rate + 100),
                        ValueUnit.RATE_BPS,
                        "대출 기준 금리"
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
