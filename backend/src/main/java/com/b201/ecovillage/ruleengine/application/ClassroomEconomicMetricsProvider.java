package com.b201.ecovillage.ruleengine.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerFlowClassifier;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ruleengine.domain.ClassroomEconomicMetrics;

/**
 * 규칙 엔진이 금리·세율·급여를 정할 때 쓰는 학급 지표를 한 번에 모은다.
 *
 * <p>교사 화면은 학급 전체를 볼 수 없으므로 화면에서 받지 않고 여기서 직접 읽는다. 학급을 막
 * 만든 시점에는 거래가 하나도 없어 대부분 0으로 나오는데, 그때는 규칙 엔진이 급여 계획으로
 * 계산한 예정 발행액을 대신 쓴다.
 *
 * <p>지표 조회가 실패해도 모듈 활성화 자체가 막히면 안 된다. 그래서 모든 조회는 실패를 삼키고
 * "모름"에 해당하는 값을 돌려준다.
 */
@Component
public class ClassroomEconomicMetricsProvider {

    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";

    private final JdbcTemplate jdbcTemplate;
    private final LedgerAccountRepository accountRepository;

    public ClassroomEconomicMetricsProvider(
        JdbcTemplate jdbcTemplate,
        LedgerAccountRepository accountRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountRepository = accountRepository;
    }

    public ClassroomEconomicMetrics of(UUID classroomId, Instant asOf) {
        List<Long> balances = walletBalances(classroomId);
        Flows flows = flows(classroomId);
        Store store = store(classroomId, asOf);
        return new ClassroomEconomicMetrics(
            moneyIssuance(classroomId),
            flows.issuance(),
            flows.recovery(),
            flows.salaryIssuance(),
            average(balances),
            median(balances),
            store.averagePrice(),
            store.inventoryValue(),
            paymentMissBps(classroomId),
            financialParticipationBps(classroomId, asOf),
            balances.size()
        );
    }

    /**
     * 지금까지 발행한 화폐의 총액이다. 발행 계정은 돈을 내보낼 때마다 음수로 내려가므로 부호를
     * 뒤집어 읽는다. 회수로 다시 올라간 경우에는 0 아래로 내려가지 않게 막는다.
     */
    private long moneyIssuance(UUID classroomId) {
        try {
            return accountRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    MONEY_ISSUANCE_KEY,
                    LedgerAccountType.MONEY_ISSUANCE,
                    LedgerAccountStatus.ACTIVE
                )
                .map(LedgerAccount::getCurrentBalance)
                .map(balance -> Math.max(0L, -balance))
                .orElse(0L);
        } catch (DataAccessException exception) {
            return 0L;
        }
    }

    private List<Long> walletBalances(UUID classroomId) {
        try {
            return accountRepository.findActiveStudentWalletBalances(classroomId);
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    /**
     * 학생 지갑으로 들어오고 나간 돈을 거래 유형별로 합쳐 발행·회수로 나눈다.
     *
     * <p>유형별로 묶어 한 번에 읽는다. 거래 하나하나를 보지 않아도 발행 대비 회수 비율을 내는
     * 데는 충분하고, 모듈 활성화 트랜잭션 안에서 도는 조회라 가벼워야 한다.
     */
    private Flows flows(UUID classroomId) {
        try {
            List<TypeFlow> rows = jdbcTemplate.query(
                """
                    select coalesce(root.transaction_type, tg.transaction_type) as flow_type,
                           sum(le.amount) as wallet_delta
                      from ledger_entries le
                      join ledger_accounts la on la.id = le.ledger_account_id
                      join transaction_groups tg on tg.id = le.transaction_group_id
                      left join transaction_groups root on root.id = tg.root_transaction_group_id
                     where tg.classroom_id = ?
                       and tg.status = 'COMMITTED'
                       and la.account_type = 'STUDENT_WALLET'
                     group by coalesce(root.transaction_type, tg.transaction_type)
                    """,
                (rs, row) -> new TypeFlow(rs.getString("flow_type"), rs.getLong("wallet_delta")),
                classroomId
            );
            long issuance = 0L;
            long recovery = 0L;
            long salary = 0L;
            for (TypeFlow row : rows) {
                LedgerFlowClassifier.Flow flow =
                    LedgerFlowClassifier.classify(row.type(), row.walletDelta());
                if (flow == LedgerFlowClassifier.Flow.ISSUANCE && row.walletDelta() > 0) {
                    issuance += row.walletDelta();
                    if (LedgerFlowClassifier.isSalary(row.type())) {
                        salary += row.walletDelta();
                    }
                } else if (flow == LedgerFlowClassifier.Flow.RECOVERY && row.walletDelta() < 0) {
                    recovery += -row.walletDelta();
                }
            }
            return new Flows(issuance, recovery, salary);
        } catch (DataAccessException exception) {
            return new Flows(0L, 0L, 0L);
        }
    }

    /**
     * 게시된 상품의 평균 단가와 재고 총액이다. 급여가 "물건 몇 개를 살 수 있는 돈"인지 정하는
     * 기준이자, 발행액이 상점이 받아낼 수 있는 양을 넘었는지 보는 기준이다.
     */
    private Store store(UUID classroomId, Instant asOf) {
        try {
            List<StoreRow> rows = jdbcTemplate.query(
                """
                    select v.unit_price, coalesce(s.available_quantity, 0) as available_quantity
                      from store_products p
                      join store_product_versions v on v.store_product_id = p.id
                      left join store_stocks s on s.store_product_id = p.id
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
                    """,
                (rs, row) -> new StoreRow(
                    rs.getLong("unit_price"),
                    rs.getLong("available_quantity")
                ),
                classroomId,
                ts(asOf),
                ts(asOf),
                ts(asOf),
                ts(asOf)
            );
            // 무료 상품은 물가를 대표하지 못하므로 평균에서 뺀다.
            List<StoreRow> priced = rows.stream().filter(row -> row.unitPrice() > 0).toList();
            if (priced.isEmpty()) {
                return new Store(0L, 0L);
            }
            long total = priced.stream().mapToLong(StoreRow::unitPrice).sum();
            long inventory = priced.stream()
                .mapToLong(row -> row.unitPrice() * Math.max(0L, row.availableQuantity()))
                .sum();
            return new Store(total / priced.size(), inventory);
        } catch (DataAccessException exception) {
            return new Store(0L, 0L);
        }
    }

    /** 적금·대출 납입 가운데 밀린 비율이다. 대출 금리의 신용위험 가산에 쓴다. */
    private Integer paymentMissBps(UUID classroomId) {
        long due = 0L;
        long missed = 0L;
        boolean available = false;
        for (String table : List.of("savings_installments", "loan_installments")) {
            String missedStates = "loan_installments".equals(table)
                ? "('MISSED', 'OVERDUE')"
                : "('MISSED')";
            try {
                List<long[]> rows = jdbcTemplate.query(
                    "select count(*) as due_count,"
                        + " sum(case when status in " + missedStates + " then 1 else 0 end) as miss_count"
                        + " from " + table + " where classroom_id = ?",
                    (rs, row) -> new long[] {rs.getLong("due_count"), rs.getLong("miss_count")},
                    classroomId
                );
                if (!rows.isEmpty()) {
                    due += rows.getFirst()[0];
                    missed += rows.getFirst()[1];
                    available = true;
                }
            } catch (DataAccessException ignored) {
                // 해당 모듈을 쓰지 않는 학급이면 표가 비어 있을 수 있다.
            }
        }
        if (!available || due <= 0L) {
            return null;
        }
        return (int) bps(missed, due);
    }

    /** 예금·적금·대출 가운데 하나라도 이용 중인 학생의 비율이다. 예치 규모 추정에 쓴다. */
    private Integer financialParticipationBps(UUID classroomId, Instant asOf) {
        try {
            Integer participants = jdbcTemplate.queryForObject(
                """
                    select count(distinct student_id) from (
                        select classroom_student_id as student_id
                          from deposit_accounts
                         where classroom_id = ? and (closed_at is null or closed_at >= ?)
                        union
                        select classroom_student_id as student_id
                          from savings_accounts
                         where classroom_id = ? and (closed_at is null or closed_at >= ?)
                    ) as holders
                    """,
                Integer.class,
                classroomId,
                ts(asOf),
                classroomId,
                ts(asOf)
            );
            Integer students = jdbcTemplate.queryForObject(
                "select count(*) from classroom_students"
                    + " where classroom_id = ? and (retired_at is null or retired_at > ?)",
                Integer.class,
                classroomId,
                ts(asOf)
            );
            if (participants == null || students == null || students <= 0) {
                return null;
            }
            return (int) bps(participants, students);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    private static long average(List<Long> balances) {
        if (balances.isEmpty()) {
            return 0L;
        }
        return balances.stream().mapToLong(Long::longValue).sum() / balances.size();
    }

    /** 목록은 저장소가 오름차순으로 준다. 짝수면 가운데 두 값의 평균을 쓴다. */
    private static long median(List<Long> balances) {
        if (balances.isEmpty()) {
            return 0L;
        }
        int middle = balances.size() / 2;
        if (balances.size() % 2 == 1) {
            return balances.get(middle);
        }
        return (balances.get(middle - 1) + balances.get(middle)) / 2;
    }

    private static long bps(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(10_000L))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
            .longValue();
    }

    /**
     * timestamptz 파라미터는 Timestamp로 넘긴다. PostgreSQL 드라이버는 Instant의 SQL 타입을
     * 추론하지 못해 "Can't infer the SQL type" 오류를 낸다.
     */
    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private record TypeFlow(String type, long walletDelta) {
    }

    private record Flows(long issuance, long recovery, long salaryIssuance) {
    }

    private record StoreRow(long unitPrice, long availableQuantity) {
    }

    private record Store(long averagePrice, long inventoryValue) {
    }
}
