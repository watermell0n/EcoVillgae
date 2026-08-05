package com.b201.ecovillage.finance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.LoanCreditPolicy;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class LoanRepaymentProcessor {

    private final JdbcTemplate jdbc;
    private final LedgerAccountRepository ledger;
    private final AtomicLedgerTransactionService transactions;
    private final UuidV7Generator uuids;
    private final Clock clock;
    private final RealtimeEventPublisher events;

    public LoanRepaymentProcessor(
        JdbcTemplate jdbc,
        LedgerAccountRepository ledger,
        AtomicLedgerTransactionService transactions,
        UuidV7Generator uuids,
        Clock clock,
        RealtimeEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.transactions = transactions;
        this.uuids = uuids;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void processClassroom(UUID classroomId) {
        Instant now = clock.instant();
        List<DueInstallment> dueInstallments = jdbc.query("""
            select i.id,
                   i.loan_id,
                   a.classroom_student_id,
                   i.scheduled_principal,
                   i.scheduled_interest,
                   i.grace_until,
                   i.status,
                   coalesce((p.credit_adjustments->>'on_time')::integer, 10),
                   coalesce((p.credit_adjustments->>'overdue')::integer, -30),
                   coalesce((p.credit_adjustments->>'repaid')::integer, 20)
              from loan_installments i
              join loans l on l.id = i.loan_id
              join loan_applications a on a.id = l.loan_application_id
              join loan_policy_versions p on p.id = a.loan_policy_version_id
             where i.classroom_id = ?
               and i.status in ('SCHEDULED', 'MISSED')
               and i.due_at <= ?
             order by i.due_at
             for update of i skip locked
            """, (rs, rowNum) -> new DueInstallment(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class),
                rs.getLong(4),
                rs.getLong(5),
                rs.getTimestamp(6).toInstant(),
                rs.getString(7),
                rs.getInt(8),
                rs.getInt(9),
                rs.getInt(10)
            ), classroomId, now);

        for (DueInstallment installment : dueInstallments) {
            process(classroomId, installment, now);
        }
    }

    private void process(UUID classroomId, DueInstallment installment, Instant now) {
        if ("MISSED".equals(installment.status()) && now.isAfter(installment.graceUntil())) {
            return;
        }

        long total = installment.principal() + installment.interest();
        LedgerAccount wallet = ledger
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                installment.studentId(),
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElse(null);
        if (wallet == null || wallet.getCurrentBalance() < total) {
            markMissed(classroomId, installment, now);
            return;
        }

        LedgerAccount receivable = ledger
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                "LOAN_RECEIVABLE",
                LedgerAccountType.LOAN_RECEIVABLE,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledger.save(LedgerAccount.systemAccount(
                uuids.generate(),
                classroomId,
                "LOAN_RECEIVABLE",
                LedgerAccountType.LOAN_RECEIVABLE,
                now
            )));

        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(wallet.getId(), -total));
        postings.add(new LedgerTransactionPosting(receivable.getId(), installment.principal()));
        if (installment.interest() > 0) {
            LedgerAccount issuance = ledger
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    "MONEY_ISSUANCE",
                    LedgerAccountType.MONEY_ISSUANCE,
                    LedgerAccountStatus.ACTIVE
                )
                .orElseGet(() -> ledger.save(LedgerAccount.systemAccount(
                    uuids.generate(),
                    classroomId,
                    "MONEY_ISSUANCE",
                    LedgerAccountType.MONEY_ISSUANCE,
                    now
                )));
            postings.add(new LedgerTransactionPosting(issuance.getId(), installment.interest()));
        }

        UUID transactionGroupId = uuids.generate();
        transactions.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                classroomId,
                "LOAN_REPAYMENT",
                LedgerActorType.SYSTEM,
                classroomId,
                null,
                "/system/loan-installments/{id}/repayment",
                installment.installmentId().toString(),
                installment.installmentId().toString(),
                uuids.generate().toString(),
                "Collect scheduled loan repayment",
                postings,
                now
            ),
            List.of(new LedgerDomainMutation() {
                @Override
                public void validate() {
                }

                @Override
                public void apply() {
                    completeInstallment(classroomId, installment, transactionGroupId, now);
                }
            })
        );
    }

    private void completeInstallment(
        UUID classroomId,
        DueInstallment installment,
        UUID transactionGroupId,
        Instant now
    ) {
        jdbc.update("""
            update loan_installments
               set paid_principal = ?,
                   paid_interest = ?,
                   paid_at = ?,
                   transaction_group_id = ?,
                   status = 'PAID',
                   state_version = state_version + 1,
                   status_changed_at = ?,
                   updated_at = ?
             where id = ?
               and status in ('SCHEDULED', 'MISSED')
            """, installment.principal(), installment.interest(), now, transactionGroupId,
            now, now, installment.installmentId());

        Integer remaining = jdbc.queryForObject(
            "select count(*) from loan_installments where loan_id = ? and status <> 'PAID'",
            Integer.class,
            installment.loanId()
        );
        Integer missed = jdbc.queryForObject(
            "select count(*) from loan_installments where loan_id = ? and status = 'MISSED'",
            Integer.class,
            installment.loanId()
        );
        boolean repaid = remaining != null && remaining == 0;
        String nextStatus = repaid ? "REPAID" : missed != null && missed > 0 ? "OVERDUE" : "ACTIVE";
        jdbc.update("""
            update loans
               set outstanding_principal = outstanding_principal - ?,
                   outstanding_interest = outstanding_interest - ?,
                   status = ?,
                   state_version = state_version + 1,
                   status_changed_at = ?,
                   closed_at = case when ? then ? else closed_at end,
                   updated_at = ?
             where id = ?
            """, installment.principal(), installment.interest(), nextStatus, now,
            repaid, now, now, installment.loanId());

        if ("SCHEDULED".equals(installment.status())) {
            changeCredit(classroomId, installment.studentId(), installment.onTimeCredit(),
                "ON_TIME_REPAYMENT", "정해진 날짜에 대출을 상환했어요.", installment.loanId(), now);
        }
        if (repaid) {
            changeCredit(classroomId, installment.studentId(), installment.repaidCredit(),
                "LOAN_REPAID", "대출을 모두 상환했어요.", installment.loanId(), now);
        }
        publishRepayment(classroomId, installment.studentId(), installment.loanId(), nextStatus);
    }

    private void markMissed(UUID classroomId, DueInstallment installment, Instant now) {
        int changed = jdbc.update("""
            update loan_installments
               set status = 'MISSED', state_version = state_version + 1, status_changed_at = ?, updated_at = ?
             where id = ? and status = 'SCHEDULED'
            """, now, now, installment.installmentId());
        if (changed == 1) {
            jdbc.update("""
                update loans
                   set status = 'OVERDUE', state_version = state_version + 1, status_changed_at = ?, updated_at = ?
                 where id = ?
                """, now, now, installment.loanId());
            changeCredit(classroomId, installment.studentId(), installment.overdueCredit(),
                "LOAN_OVERDUE", "대출 상환일을 지키지 못했어요.", installment.loanId(), now);
            publishRepayment(classroomId, installment.studentId(), installment.loanId(), "OVERDUE");
        }
    }

    private void changeCredit(
        UUID classroomId,
        UUID studentId,
        int delta,
        String type,
        String reason,
        UUID relatedId,
        Instant now
    ) {
        if (delta == 0) {
            return;
        }
        Credit before = jdbc.query("""
            select score, level
              from credit_profiles
             where classroom_id = ? and classroom_student_id = ?
            """, rs -> rs.next()
                ? new Credit(rs.getInt(1), rs.getString(2))
                : new Credit(600, "NORMAL"), classroomId, studentId);
        int afterScore = Math.max(0, Math.min(1_000, before.score() + delta));
        String afterLevel = LoanCreditPolicy.level(afterScore);
        jdbc.update("""
            update credit_profiles
               set score = ?, level = ?, state_version = state_version + 1, evaluated_at = ?, updated_at = ?
             where classroom_id = ? and classroom_student_id = ?
            """, afterScore, afterLevel, now, now, classroomId, studentId);
        jdbc.update("""
            insert into credit_events(
                id, classroom_id, classroom_student_id, event_type,
                score_delta, score_before, score_after, level_before, level_after,
                reason, related_type, related_id, occurred_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'LOAN', ?, ?, ?)
            """, uuids.generate(), classroomId, studentId, type,
            afterScore - before.score(), before.score(), afterScore, before.level(), afterLevel,
            reason, relatedId, now, now);
    }

    private void publishRepayment(UUID classroomId, UUID studentId, UUID loanId, String status) {
        LoanState loan = jdbc.query("""
            select outstanding_principal + outstanding_interest, state_version
              from loans
             where id = ?
            """, rs -> rs.next() ? new LoanState(rs.getLong(1), rs.getLong(2)) : null, loanId);
        if (loan == null) {
            return;
        }
        String creditLevel = jdbc.queryForObject("""
            select level from credit_profiles
             where classroom_id = ? and classroom_student_id = ?
            """, String.class, classroomId, studentId);
        events.publish(
            classroomId,
            "STUDENT_PRIVATE",
            studentId,
            "loan.repayment.status_changed",
            "LOAN",
            loanId,
            loan.stateVersion(),
            java.util.Map.of(
                "loan_id", loanId.toString(),
                "status", status,
                "remaining_amount", loan.remainingAmount(),
                "credit_status", creditLevel == null ? "NORMAL" : creditLevel
            ),
            false
        );
    }

    private record DueInstallment(
        UUID installmentId,
        UUID loanId,
        UUID studentId,
        long principal,
        long interest,
        Instant graceUntil,
        String status,
        int onTimeCredit,
        int overdueCredit,
        int repaidCredit
    ) {
    }

    private record Credit(int score, String level) {
    }

    private record LoanState(long remainingAmount, long stateVersion) {
    }
}
