package com.b201.ecovillage.ai.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.contract.BalancingContract.MetricCode;
import com.b201.ecovillage.ai.contract.BalancingContract.ValueUnit;
import com.b201.ecovillage.ledger.domain.LedgerFlowClassifier;
import com.b201.ecovillage.ledger.domain.LedgerFlowClassifier.Flow;

@Service
public class EconomicMetricAggregationService {

    private static final Set<MetricCode> LEDGER_METRICS = Set.of(
        MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS,
        MetricCode.ISSUANCE_AMOUNT,
        MetricCode.RECOVERY_AMOUNT,
        MetricCode.ISSUANCE_RECOVERY_RATE_BPS,
        MetricCode.WALLET_AVERAGE_CHANGE_RATE_BPS,
        MetricCode.WALLET_MEDIAN_CHANGE_RATE_BPS,
        MetricCode.ASSET_CONCENTRATION_TOP20_BPS,
        MetricCode.CONSUMPTION_RATE_BPS,
        MetricCode.REWARD_ISSUANCE_SHARE_BPS
    );
    private static final Set<MetricCode> PRODUCT_METRICS = Set.of(
        MetricCode.PRODUCT_SELL_THROUGH_RATE_BPS,
        MetricCode.PRODUCT_STOCKOUT_SHARE_BPS
    );
    private static final Set<MetricCode> FINANCIAL_METRICS = Set.of(
        MetricCode.FINANCIAL_PARTICIPATION_RATE_BPS,
        MetricCode.PAYMENT_MISS_RATE_BPS
    );
    private static final Set<MetricCode> INVESTMENT_METRICS = Set.of(
        MetricCode.INVESTMENT_PARTICIPATION_RATE_BPS,
        MetricCode.INVESTMENT_MEDIAN_RETURN_BPS,
        MetricCode.INVESTMENT_LOSS_PARTICIPANT_RATE_BPS
    );

    private final JdbcTemplate jdbcTemplate;

    public EconomicMetricAggregationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EconomicMetricSnapshot aggregate(
        UUID classroomId,
        Instant from,
        Instant to,
        Instant dataAsOf,
        Set<MetricCode> requestedCodes
    ) {
        List<UUID> activeStudents = activeStudents(classroomId, dataAsOf);
        List<WalletEntry> entries = walletEntries(classroomId, dataAsOf, activeStudents);
        LedgerAggregate ledger = ledger(entries, activeStudents, from, to, requestedCodes);
        EnumMap<MetricCode, EconomicMetricSnapshot.MetricValue> metrics =
            new EnumMap<>(MetricCode.class);
        addLedgerMetrics(metrics, ledger, activeStudents.size());
        addProductMetrics(metrics, classroomId, from, to, dataAsOf);
        addFinancialMetrics(metrics, classroomId, from, to, activeStudents.size());
        addInvestmentMetrics(metrics, classroomId, from, to, dataAsOf, activeStudents.size());
        for (MetricCode code : requestedCodes) {
            metrics.putIfAbsent(
                code,
                EconomicMetricSnapshot.MetricValue.unavailable(
                    unit(code),
                    "METRIC_SOURCE_UNAVAILABLE"
                )
            );
        }
        int related = relatedTransactions(
            ledger.groups(),
            classroomId,
            from,
            to,
            requestedCodes
        );
        return new EconomicMetricSnapshot(activeStudents.size(), related, metrics);
    }

    private List<UUID> activeStudents(UUID classroomId, Instant dataAsOf) {
        return jdbcTemplate.query(
            """
                select id
                  from classroom_students
                 where classroom_id = ?
                   and joined_at <= ?
                   and (retired_at is null or retired_at > ?)
                """,
            (rs, row) -> uuid(rs, "id"),
            classroomId,
            ts(dataAsOf),
            ts(dataAsOf)
        );
    }

    private List<WalletEntry> walletEntries(
        UUID classroomId,
        Instant dataAsOf,
        List<UUID> activeStudents
    ) {
        if (activeStudents.isEmpty()) {
            return List.of();
        }
        Set<UUID> active = Set.copyOf(activeStudents);
        return jdbcTemplate.query(
            """
                select la.classroom_student_id,
                       tg.id as transaction_group_id,
                       tg.transaction_type,
                       coalesce(root.transaction_type, tg.transaction_type) as root_transaction_type,
                       tg.committed_at,
                       le.amount
                  from ledger_entries le
                  join ledger_accounts la on la.id = le.ledger_account_id
                  join transaction_groups tg on tg.id = le.transaction_group_id
                  left join transaction_groups root on root.id = tg.root_transaction_group_id
                 where tg.classroom_id = ?
                   and tg.status = 'COMMITTED'
                   and tg.committed_at < ?
                   and la.account_type = 'STUDENT_WALLET'
                 order by tg.committed_at, le.account_sequence
                """,
            (rs, row) -> walletEntry(rs),
            classroomId,
            ts(dataAsOf)
        ).stream().filter(entry -> active.contains(entry.studentId())).toList();
    }

    private WalletEntry walletEntry(ResultSet rs) throws SQLException {
        return new WalletEntry(
            uuid(rs, "classroom_student_id"),
            uuid(rs, "transaction_group_id"),
            rs.getString("transaction_type"),
            rs.getString("root_transaction_type"),
            instant(rs, "committed_at"),
            rs.getLong("amount")
        );
    }

    private LedgerAggregate ledger(
        List<WalletEntry> entries,
        List<UUID> activeStudents,
        Instant from,
        Instant to,
        Set<MetricCode> requestedCodes
    ) {
        Map<UUID, Long> startBalances = zeroBalances(activeStudents);
        Map<UUID, Long> endBalances = zeroBalances(activeStudents);
        Map<UUID, GroupFlow> groups = new LinkedHashMap<>();
        for (WalletEntry entry : entries) {
            if (entry.committedAt().isBefore(from)) {
                startBalances.merge(entry.studentId(), entry.amount(), Math::addExact);
            }
            if (entry.committedAt().isBefore(to)) {
                endBalances.merge(entry.studentId(), entry.amount(), Math::addExact);
            }
            if (!entry.committedAt().isBefore(from) && entry.committedAt().isBefore(to)) {
                groups.computeIfAbsent(
                    entry.groupId(),
                    ignored -> new GroupFlow(entry.rootTransactionType(), 0L)
                ).add(entry.amount());
            }
        }
        long issuance = 0;
        long recovery = 0;
        long consumption = 0;
        long rewards = 0;
        for (GroupFlow group : groups.values()) {
            Flow flowClass = classify(group.transactionType(), group.walletDelta());
            if (flowClass == Flow.ISSUANCE) {
                issuance = Math.addExact(issuance, group.walletDelta());
                if (isReward(group.transactionType())) {
                    rewards = Math.addExact(rewards, group.walletDelta());
                }
            } else if (flowClass == Flow.RECOVERY) {
                long recovered = Math.negateExact(group.walletDelta());
                recovery = Math.addExact(recovery, recovered);
                if (isConsumption(group.transactionType())) {
                    consumption = Math.addExact(consumption, recovered);
                }
            }
        }
        return new LedgerAggregate(
            startBalances,
            endBalances,
            Math.max(issuance, 0),
            Math.max(recovery, 0),
            Math.max(consumption, 0),
            Math.max(rewards, 0),
            groups
        );
    }

    private void addLedgerMetrics(
        Map<MetricCode, EconomicMetricSnapshot.MetricValue> target,
        LedgerAggregate ledger,
        int activeStudents
    ) {
        long startTotal = sum(ledger.startBalances().values());
        long endTotal = sum(ledger.endBalances().values());
        target.put(
            MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS,
            rate(endTotal - startTotal, Math.max(Math.abs(startTotal), 1))
        );
        target.put(
            MetricCode.ISSUANCE_AMOUNT,
            amount(ledger.issuance())
        );
        target.put(
            MetricCode.RECOVERY_AMOUNT,
            amount(ledger.recovery())
        );
        target.put(
            MetricCode.ISSUANCE_RECOVERY_RATE_BPS,
            rateOrUnavailable(ledger.recovery(), ledger.issuance(), "ISSUANCE_AMOUNT_ZERO")
        );
        target.put(
            MetricCode.WALLET_AVERAGE_CHANGE_RATE_BPS,
            rate(endTotal - startTotal, Math.max(Math.abs(startTotal), 1))
        );
        long startMedian = median(ledger.startBalances().values());
        long endMedian = median(ledger.endBalances().values());
        target.put(
            MetricCode.WALLET_MEDIAN_CHANGE_RATE_BPS,
            rate(endMedian - startMedian, Math.max(Math.abs(startMedian), 1))
        );
        long positiveTotal = ledger.endBalances().values().stream()
            .filter(value -> value > 0)
            .mapToLong(Long::longValue)
            .sum();
        int topCount = Math.max(1, (int) Math.ceil(activeStudents * 0.2d));
        long topTotal = ledger.endBalances().values().stream()
            .filter(value -> value > 0)
            .sorted(Comparator.reverseOrder())
            .limit(topCount)
            .mapToLong(Long::longValue)
            .sum();
        target.put(
            MetricCode.ASSET_CONCENTRATION_TOP20_BPS,
            rateOrUnavailable(topTotal, positiveTotal, "POSITIVE_WALLET_BALANCE_ZERO")
        );
        target.put(
            MetricCode.CONSUMPTION_RATE_BPS,
            rateOrUnavailable(
                ledger.consumption(),
                Math.addExact(Math.max(startTotal, 0), ledger.issuance()),
                "AVAILABLE_FUNDS_ZERO"
            )
        );
        target.put(
            MetricCode.REWARD_ISSUANCE_SHARE_BPS,
            rateOrUnavailable(ledger.rewards(), ledger.issuance(), "ISSUANCE_AMOUNT_ZERO")
        );
    }

    private void addProductMetrics(
        Map<MetricCode, EconomicMetricSnapshot.MetricValue> target,
        UUID classroomId,
        Instant from,
        Instant to,
        Instant dataAsOf
    ) {
        target.put(
            MetricCode.PRODUCT_SELL_THROUGH_RATE_BPS,
            EconomicMetricSnapshot.MetricValue.unavailable(
                ValueUnit.RATE_BPS,
                "STOCK_HISTORY_UNAVAILABLE"
            )
        );
        try {
            Integer products = jdbcTemplate.queryForObject(
                """
                    select count(*)
                      from store_products p
                     where p.classroom_id = ?
                       and p.status = 'PUBLISHED'
                    """,
                Integer.class,
                classroomId
            );
            Integer stockouts = jdbcTemplate.queryForObject(
                """
                    select count(*)
                      from store_products p
                      join store_stocks s on s.store_product_id = p.id
                     where p.classroom_id = ?
                       and p.status = 'PUBLISHED'
                       and s.available_quantity = 0
                    """,
                Integer.class,
                classroomId
            );
            target.put(
                MetricCode.PRODUCT_STOCKOUT_SHARE_BPS,
                rateOrUnavailable(
                    stockouts == null ? 0 : stockouts,
                    products == null ? 0 : products,
                    "PUBLISHED_PRODUCTS_EMPTY"
                )
            );
        } catch (DataAccessException exception) {
            target.put(
                MetricCode.PRODUCT_STOCKOUT_SHARE_BPS,
                unavailable("PRODUCT_DATA_UNAVAILABLE")
            );
        }
    }

    private void addFinancialMetrics(
        Map<MetricCode, EconomicMetricSnapshot.MetricValue> target,
        UUID classroomId,
        Instant from,
        Instant to,
        int activeStudents
    ) {
        try {
            Set<UUID> participants = new HashSet<>(jdbcTemplate.query(
                """
                    select distinct classroom_student_id
                      from deposit_accounts
                     where classroom_id = ?
                       and opened_at < ?
                       and (closed_at is null or closed_at >= ?)
                    """,
                (rs, row) -> uuid(rs, "classroom_student_id"),
                classroomId,
                ts(to),
                ts(to)
            ));
            safeStudentIds(
                """
                    select distinct classroom_student_id
                      from savings_accounts
                     where classroom_id = ?
                       and opened_at < ?
                       and (closed_at is null or closed_at >= ?)
                    """,
                classroomId,
                ts(to),
                ts(to)
            ).forEach(participants::add);
            safeStudentIds(
                """
                    select distinct la.classroom_student_id
                      from loans l
                      join loan_applications la on la.id = l.loan_application_id
                     where l.classroom_id = ?
                       and l.started_at < ?
                       and (l.closed_at is null or l.closed_at >= ?)
                    """,
                classroomId,
                ts(to),
                ts(to)
            ).forEach(participants::add);
            target.put(
                MetricCode.FINANCIAL_PARTICIPATION_RATE_BPS,
                rateOrUnavailable(
                    participants.size(),
                    activeStudents,
                    "ACTIVE_STUDENTS_ZERO"
                )
            );
        } catch (DataAccessException exception) {
            target.put(
                MetricCode.FINANCIAL_PARTICIPATION_RATE_BPS,
                unavailable("FINANCIAL_DATA_UNAVAILABLE")
            );
        }
        long[] due = {0L, 0L};
        boolean sourceAvailable = false;
        try {
            Map<String, Object> savings = jdbcTemplate.queryForMap(
                """
                    select count(*) as due_count,
                           sum(case when status = 'MISSED' then 1 else 0 end) as miss_count
                      from savings_installments
                     where classroom_id = ? and due_at >= ? and due_at < ?
                    """,
                classroomId,
                ts(from),
                ts(to)
            );
            due[0] += number(savings.get("due_count"));
            due[1] += number(savings.get("miss_count"));
            sourceAvailable = true;
        } catch (DataAccessException ignored) {
        }
        try {
            Map<String, Object> loans = jdbcTemplate.queryForMap(
                """
                    select count(*) as due_count,
                           sum(case when status in ('MISSED', 'OVERDUE') then 1 else 0 end) as miss_count
                      from loan_installments
                     where classroom_id = ? and due_at >= ? and due_at < ?
                    """,
                classroomId,
                ts(from),
                ts(to)
            );
            due[0] += number(loans.get("due_count"));
            due[1] += number(loans.get("miss_count"));
            sourceAvailable = true;
        } catch (DataAccessException ignored) {
        }
        target.put(
            MetricCode.PAYMENT_MISS_RATE_BPS,
            !sourceAvailable
                ? unavailable("PAYMENT_SCHEDULE_UNAVAILABLE")
                : rateOrUnavailable(due[1], due[0], "PAYMENT_SCHEDULE_EMPTY")
        );
    }

    private void addInvestmentMetrics(
        Map<MetricCode, EconomicMetricSnapshot.MetricValue> target,
        UUID classroomId,
        Instant from,
        Instant to,
        Instant dataAsOf,
        int activeStudents
    ) {
        try {
            Integer participants = jdbcTemplate.queryForObject(
                """
                    select count(distinct classroom_student_id)
                      from investment_orders
                     where classroom_id = ?
                       and status = 'EXECUTED'
                       and executed_at >= ? and executed_at < ?
                    """,
                Integer.class,
                classroomId,
                ts(from),
                ts(to)
            );
            target.put(
                MetricCode.INVESTMENT_PARTICIPATION_RATE_BPS,
                rateOrUnavailable(
                    participants == null ? 0 : participants,
                    activeStudents,
                    "ACTIVE_STUDENTS_ZERO"
                )
            );
        } catch (DataAccessException exception) {
            target.put(
                MetricCode.INVESTMENT_PARTICIPATION_RATE_BPS,
                unavailable("INVESTMENT_DATA_UNAVAILABLE")
            );
        }
        try {
            List<InvestmentOrder> orders = jdbcTemplate.query(
                """
                    select classroom_student_id, investment_asset_id, side, quantity, executed_price
                      from investment_orders
                     where classroom_id = ?
                       and status = 'EXECUTED'
                       and executed_at < ?
                     order by executed_at, id
                    """,
                (rs, row) -> new InvestmentOrder(
                    uuid(rs, "classroom_student_id"),
                    uuid(rs, "investment_asset_id"),
                    rs.getString("side"),
                    rs.getLong("quantity"),
                    rs.getLong("executed_price")
                ),
                classroomId,
                ts(dataAsOf)
            );
            Map<UUID, Long> prices = new HashMap<>();
            List<InvestmentPrice> priceRows = jdbcTemplate.query(
                """
                    select investment_asset_id, price
                      from investment_price_steps
                     where classroom_id = ?
                       and revealed_at is not null
                       and revealed_at <= ?
                     order by investment_asset_id, step_number
                    """,
                (rs, row) -> new InvestmentPrice(
                    uuid(rs, "investment_asset_id"),
                    rs.getLong("price")
                ),
                classroomId,
                ts(dataAsOf)
            );
            priceRows.forEach(price -> prices.put(price.assetId(), price.price()));
            Map<UUID, Portfolio> portfolios = new LinkedHashMap<>();
            for (InvestmentOrder order : orders) {
                Portfolio portfolio = portfolios.computeIfAbsent(
                    order.studentId(),
                    ignored -> new Portfolio()
                );
                portfolio.apply(order);
            }
            List<Long> returns = new ArrayList<>();
            boolean missingPrice = false;
            for (Portfolio portfolio : portfolios.values()) {
                if (portfolio.buyCost == 0) {
                    continue;
                }
                Long marketValue = portfolio.marketValue(prices);
                if (marketValue == null) {
                    missingPrice = true;
                    continue;
                }
                returns.add(bps(
                    Math.addExact(portfolio.sellProceeds, marketValue) - portfolio.buyCost,
                    portfolio.buyCost
                ));
            }
            if (missingPrice || returns.isEmpty()) {
                String reason = missingPrice
                    ? "INVESTMENT_PRICE_HISTORY_UNAVAILABLE"
                    : "INVESTMENT_COST_BASIS_EMPTY";
                target.put(MetricCode.INVESTMENT_MEDIAN_RETURN_BPS, unavailable(reason));
                target.put(MetricCode.INVESTMENT_LOSS_PARTICIPANT_RATE_BPS, unavailable(reason));
            } else {
                target.put(
                    MetricCode.INVESTMENT_MEDIAN_RETURN_BPS,
                    EconomicMetricSnapshot.MetricValue.available(
                        median(returns),
                        ValueUnit.RATE_BPS
                    )
                );
                long losses = returns.stream().filter(value -> value < 0).count();
                target.put(
                    MetricCode.INVESTMENT_LOSS_PARTICIPANT_RATE_BPS,
                    rateOrUnavailable(losses, returns.size(), "INVESTMENT_COST_BASIS_EMPTY")
                );
            }
        } catch (DataAccessException exception) {
            target.put(
                MetricCode.INVESTMENT_MEDIAN_RETURN_BPS,
                unavailable("INVESTMENT_DATA_UNAVAILABLE")
            );
            target.put(
                MetricCode.INVESTMENT_LOSS_PARTICIPANT_RATE_BPS,
                unavailable("INVESTMENT_DATA_UNAVAILABLE")
            );
        }
    }

    private int relatedTransactions(
        Map<UUID, GroupFlow> groups,
        UUID classroomId,
        Instant from,
        Instant to,
        Set<MetricCode> requestedCodes
    ) {
        Set<UUID> related = new HashSet<>();
        boolean ledger = intersects(requestedCodes, LEDGER_METRICS);
        boolean product = intersects(requestedCodes, PRODUCT_METRICS);
        boolean finance = intersects(requestedCodes, FINANCIAL_METRICS);
        boolean investment = intersects(requestedCodes, INVESTMENT_METRICS);
        for (Map.Entry<UUID, GroupFlow> entry : groups.entrySet()) {
            String type = normalized(entry.getValue().transactionType());
            Flow flow = classify(type, entry.getValue().walletDelta());
            if (
                (ledger && flow != Flow.RECLASSIFICATION)
                    || (product && type.contains("STORE"))
                    || (finance && (type.contains("DEPOSIT") || type.contains("SAVINGS") || type.contains("LOAN")))
                    || (investment && type.contains("INVESTMENT"))
            ) {
                related.add(entry.getKey());
            }
        }
        return related.size();
    }

    /**
     * timestamptz 파라미터는 Timestamp로 넘긴다. PostgreSQL 드라이버는 Instant의 SQL 타입을
     * 추론하지 못해 "Can't infer the SQL type" 오류를 낸다.
     */
    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private List<UUID> safeStudentIds(String sql, Object... arguments) {
        try {
            return jdbcTemplate.query(sql, (rs, row) -> uuid(rs, "classroom_student_id"), arguments);
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    private static boolean intersects(Set<MetricCode> left, Set<MetricCode> right) {
        return left.stream().anyMatch(right::contains);
    }

    private static Flow classify(String transactionType, long walletDelta) {
        return LedgerFlowClassifier.classify(transactionType, walletDelta);
    }

    private static boolean isReward(String transactionType) {
        return LedgerFlowClassifier.isReward(transactionType);
    }

    private static boolean isConsumption(String transactionType) {
        return LedgerFlowClassifier.isConsumption(transactionType);
    }

    private static String normalized(String value) {
        return LedgerFlowClassifier.normalized(value);
    }

    private static Map<UUID, Long> zeroBalances(List<UUID> students) {
        Map<UUID, Long> result = new HashMap<>();
        students.forEach(student -> result.put(student, 0L));
        return result;
    }

    private static long sum(Iterable<Long> values) {
        long result = 0;
        for (Long value : values) {
            result = Math.addExact(result, value);
        }
        return result;
    }

    private static long median(Iterable<Long> values) {
        List<Long> sorted = new ArrayList<>();
        values.forEach(sorted::add);
        sorted.sort(Long::compareTo);
        if (sorted.isEmpty()) {
            return 0;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return BigDecimal.valueOf(sorted.get(middle - 1))
            .add(BigDecimal.valueOf(sorted.get(middle)))
            .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
            .longValueExact();
    }

    private static EconomicMetricSnapshot.MetricValue amount(long value) {
        return EconomicMetricSnapshot.MetricValue.available(value, ValueUnit.AMOUNT);
    }

    private static EconomicMetricSnapshot.MetricValue rate(long numerator, long denominator) {
        return EconomicMetricSnapshot.MetricValue.available(
            bps(numerator, denominator),
            ValueUnit.RATE_BPS
        );
    }

    private static long bps(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(10_000))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
            .longValueExact();
    }

    private static EconomicMetricSnapshot.MetricValue rateOrUnavailable(
        long numerator,
        long denominator,
        String reason
    ) {
        return denominator <= 0 ? unavailable(reason) : rate(numerator, denominator);
    }

    private static EconomicMetricSnapshot.MetricValue unavailable(String reason) {
        return EconomicMetricSnapshot.MetricValue.unavailable(ValueUnit.RATE_BPS, reason);
    }

    private static ValueUnit unit(MetricCode code) {
        return switch (code) {
            case ISSUANCE_AMOUNT, RECOVERY_AMOUNT -> ValueUnit.AMOUNT;
            default -> ValueUnit.RATE_BPS;
        };
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private record WalletEntry(
        UUID studentId,
        UUID groupId,
        String transactionType,
        String rootTransactionType,
        Instant committedAt,
        long amount
    ) {
    }

    private static final class GroupFlow {
        private final String transactionType;
        private long walletDelta;

        private GroupFlow(String transactionType, long walletDelta) {
            this.transactionType = transactionType;
            this.walletDelta = walletDelta;
        }

        private void add(long amount) {
            walletDelta = Math.addExact(walletDelta, amount);
        }

        private String transactionType() {
            return transactionType;
        }

        private long walletDelta() {
            return walletDelta;
        }
    }

    private record LedgerAggregate(
        Map<UUID, Long> startBalances,
        Map<UUID, Long> endBalances,
        long issuance,
        long recovery,
        long consumption,
        long rewards,
        Map<UUID, GroupFlow> groups
    ) {
    }

    private record InvestmentOrder(
        UUID studentId,
        UUID assetId,
        String side,
        long quantity,
        long executedPrice
    ) {
    }

    private record InvestmentPrice(UUID assetId, long price) {
    }

    private static final class Portfolio {
        private long buyCost;
        private long sellProceeds;
        private final Map<UUID, Long> quantities = new HashMap<>();

        private void apply(InvestmentOrder order) {
            long amount = Math.multiplyExact(order.quantity(), order.executedPrice());
            if ("BUY".equalsIgnoreCase(order.side())) {
                buyCost = Math.addExact(buyCost, amount);
                quantities.merge(order.assetId(), order.quantity(), Math::addExact);
            } else if ("SELL".equalsIgnoreCase(order.side())) {
                sellProceeds = Math.addExact(sellProceeds, amount);
                quantities.merge(order.assetId(), -order.quantity(), Math::addExact);
            }
        }

        private Long marketValue(Map<UUID, Long> prices) {
            long result = 0;
            for (Map.Entry<UUID, Long> position : quantities.entrySet()) {
                if (position.getValue() <= 0) {
                    continue;
                }
                Long price = prices.get(position.getKey());
                if (price == null) {
                    return null;
                }
                result = Math.addExact(
                    result,
                    Math.multiplyExact(position.getValue(), price)
                );
            }
            return result;
        }
    }

}
