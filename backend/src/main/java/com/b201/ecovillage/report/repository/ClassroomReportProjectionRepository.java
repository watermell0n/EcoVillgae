package com.b201.ecovillage.report.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.b201.ecovillage.report.application.ClassroomReportAggregate;

@Repository
public class ClassroomReportProjectionRepository {

    private final JdbcTemplate jdbc;

    public ClassroomReportProjectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ClassroomReportAggregate aggregate(
        UUID classroomId,
        Instant from,
        Instant to
    ) {
        return new ClassroomReportAggregate(
            walletBalanceAt(classroomId, to),
            spendingBetween(classroomId, from, to),
            savingsPrincipalAt(classroomId, to),
            publicFundBalanceAt(classroomId, to),
            activeStudentCount(classroomId),
            quizParticipantCount(classroomId, from, to),
            quizSubmissionCount(classroomId, from, to)
        );
    }

    private long walletBalanceAt(UUID classroomId, Instant to) {
        return value("""
            SELECT COALESCE(SUM(entry.balance_after), 0)
              FROM ledger_accounts account
              JOIN ledger_entries entry
                ON entry.ledger_account_id = account.id
               AND entry.account_sequence = (
                    SELECT MAX(previous.account_sequence)
                      FROM ledger_entries previous
                     WHERE previous.ledger_account_id = account.id
                       AND previous.created_at < ?
               )
             WHERE account.classroom_id = ?
               AND account.account_type = 'STUDENT_WALLET'
            """, Timestamp.from(to), classroomId);
    }

    private long spendingBetween(UUID classroomId, Instant from, Instant to) {
        return value("""
            SELECT COALESCE(SUM(-entry.amount), 0)
              FROM ledger_entries entry
              JOIN ledger_accounts account
                ON account.classroom_id = entry.classroom_id
               AND account.id = entry.ledger_account_id
              JOIN transaction_groups transaction_group
                ON transaction_group.classroom_id = entry.classroom_id
               AND transaction_group.id = entry.transaction_group_id
             WHERE entry.classroom_id = ?
               AND account.account_type = 'STUDENT_WALLET'
               AND entry.amount < 0
               AND transaction_group.transaction_type = 'STORE_PURCHASE'
               AND transaction_group.status = 'COMMITTED'
               AND transaction_group.committed_at >= ?
               AND transaction_group.committed_at < ?
            """, classroomId, Timestamp.from(from), Timestamp.from(to));
    }

    private long savingsPrincipalAt(UUID classroomId, Instant to) {
        long deposits = value("""
            SELECT COALESCE(SUM(principal_amount), 0)
              FROM deposit_accounts
             WHERE classroom_id = ?
               AND opened_at < ?
               AND (closed_at IS NULL OR closed_at >= ?)
            """, classroomId, Timestamp.from(to), Timestamp.from(to));
        long savings = value("""
            SELECT COALESCE(SUM(principal_balance), 0)
              FROM savings_accounts
             WHERE classroom_id = ?
               AND opened_at < ?
               AND (closed_at IS NULL OR closed_at >= ?)
            """, classroomId, Timestamp.from(to), Timestamp.from(to));
        return Math.addExact(deposits, savings);
    }

    private long publicFundBalanceAt(UUID classroomId, Instant to) {
        return value("""
            SELECT COALESCE((
                SELECT entry.balance_after
                  FROM ledger_accounts account
                  JOIN ledger_entries entry
                    ON entry.classroom_id = account.classroom_id
                   AND entry.ledger_account_id = account.id
                 WHERE account.classroom_id = ?
                   AND account.account_type = 'CLASSROOM_FUND'
                   AND account.status = 'ACTIVE'
                   AND entry.created_at < ?
                 ORDER BY entry.account_sequence DESC
                 LIMIT 1
            ), 0)
            """, classroomId, Timestamp.from(to));
    }

    private long activeStudentCount(UUID classroomId) {
        return value("""
            SELECT COUNT(*)
              FROM classroom_students
             WHERE classroom_id = ?
               AND status = 'ACTIVE'
               AND retired_at IS NULL
            """, classroomId);
    }

    private long quizParticipantCount(
        UUID classroomId,
        Instant from,
        Instant to
    ) {
        return value("""
            SELECT COUNT(DISTINCT classroom_student_id)
              FROM quiz_submissions
             WHERE classroom_id = ?
               AND created_at >= ?
               AND created_at < ?
            """, classroomId, Timestamp.from(from), Timestamp.from(to));
    }

    private long quizSubmissionCount(
        UUID classroomId,
        Instant from,
        Instant to
    ) {
        return value("""
            SELECT COUNT(*)
              FROM quiz_submissions
             WHERE classroom_id = ?
               AND created_at >= ?
               AND created_at < ?
            """, classroomId, Timestamp.from(from), Timestamp.from(to));
    }

    private long value(String sql, Object... arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }
}
