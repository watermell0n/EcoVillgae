package com.b201.ecovillage.quiz.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QuizRewardBudgetService {

    private static final BigDecimal WARN_SHARE = new BigDecimal("0.25");
    private static final BigDecimal MAX_SHARE = new BigDecimal("0.30");

    private final JdbcTemplate jdbcTemplate;

    public QuizRewardBudgetService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QuizRewardBudget evaluate(UUID classroomId, long candidateRewardIssuance) {
        if (candidateRewardIssuance < 0) {
            throw new IllegalArgumentException("candidateRewardIssuance must not be negative");
        }
        List<GroupFlow> flows = jdbcTemplate.query(
            """
                select coalesce(root.transaction_type, tg.transaction_type) as root_type,
                       sum(le.amount) as wallet_delta
                  from transaction_groups tg
                  join ledger_entries le
                    on le.classroom_id = tg.classroom_id
                   and le.transaction_group_id = tg.id
                  join ledger_accounts la
                    on la.classroom_id = le.classroom_id
                   and la.id = le.ledger_account_id
                  left join transaction_groups root
                    on root.classroom_id = tg.classroom_id
                   and root.id = tg.root_transaction_group_id
                 where tg.classroom_id = ?
                   and tg.status = 'COMMITTED'
                   and la.account_type = 'STUDENT_WALLET'
                 group by coalesce(root.id, tg.id),
                          coalesce(root.transaction_type, tg.transaction_type)
                """,
            (resultSet, rowNumber) -> new GroupFlow(
                resultSet.getString("root_type"),
                resultSet.getLong("wallet_delta")
            ),
            classroomId
        );

        long nonReward = 0L;
        long existingReward = 0L;
        for (GroupFlow flow : flows) {
            if (flow.walletDelta() <= 0L) {
                continue;
            }
            if (isReward(flow.transactionType())) {
                existingReward = Math.addExact(existingReward, flow.walletDelta());
            } else {
                nonReward = Math.addExact(nonReward, flow.walletDelta());
            }
        }
        long totalReward = Math.addExact(existingReward, candidateRewardIssuance);
        long totalIssuance = Math.addExact(nonReward, totalReward);
        BigDecimal share = totalIssuance == 0L
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(totalReward).divide(
                BigDecimal.valueOf(totalIssuance),
                8,
                RoundingMode.HALF_UP
            );
        int basisPoints = share.multiply(BigDecimal.valueOf(10_000L))
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact();
        return new QuizRewardBudget(
            nonReward,
            existingReward,
            candidateRewardIssuance,
            basisPoints,
            share.compareTo(WARN_SHARE) > 0 && share.compareTo(MAX_SHARE) <= 0,
            share.compareTo(MAX_SHARE) > 0
        );
    }

    private static boolean isReward(String transactionType) {
        return transactionType != null
            && transactionType.toUpperCase(Locale.ROOT).contains("REWARD");
    }

    private record GroupFlow(String transactionType, long walletDelta) {
    }
}
