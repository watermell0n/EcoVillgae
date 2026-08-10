package com.b201.ecovillage.finance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.LoanCreditPolicy;
import com.b201.ecovillage.finance.repository.LoanPolicyVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
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

    private static final String REPAYMENT_ROUTE =
        "/classrooms/{classroom_id}/loans/{resource_id}/repayment-commands";

    private final JdbcTemplate jdbc;
    private final ClassroomAuthorizationService authorization;
    private final LedgerAccountRepository ledger;
    private final LoanPolicyVersionRepository loanPolicies;
    private final AtomicLedgerTransactionService transactions;
    private final UuidV7Generator uuids;
    private final Clock clock;
    private final RealtimeEventPublisher events;

    public LoanRepaymentProcessor(
        JdbcTemplate jdbc,
        ClassroomAuthorizationService authorization,
        LedgerAccountRepository ledger,
        LoanPolicyVersionRepository loanPolicies,
        AtomicLedgerTransactionService transactions,
        UuidV7Generator uuids,
        Clock clock,
        RealtimeEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.ledger = ledger;
        this.loanPolicies = loanPolicies;
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
                   a.loan_policy_version_id
              from loan_installments i
              join loans l on l.id = i.loan_id
              join loan_applications a on a.id = l.loan_application_id
              join loan_policy_versions p on p.id = a.loan_policy_version_id
             where i.classroom_id = ?
               and i.status in ('SCHEDULED', 'MISSED')
               and i.due_at <= ?
             order by i.due_at
             for update of i skip locked
            """, (rs, rowNum) -> installment(rs), classroomId, now);

        for (DueInstallment installment : dueInstallments) {
            process(classroomId, installment, now);
        }
    }

    @Transactional
    public LoanRepaymentResult repay(
        String subject,
        UUID classroomId,
        UUID loanId,
        String command,
        Long expectedStateVersion,
        UUID requestId,
        UUID correlationId
    ) {
        UUID studentId = student(subject);
        authorization.authorize(subject, classroomId, ClassroomPermission.REQUEST_LOAN, studentId);
        Replay replay = replay(classroomId, loanId, studentId, command, requestId);
        if (replay != null) {
            if (!replay.matches()) {
                throw new IdempotencyKeyReusedException();
            }
            return result(classroomId, loanId, studentId, true);
        }
        OwnedLoan loan = jdbc.query("""
            select l.state_version, l.status
              from loans l
              join loan_applications a on a.id = l.loan_application_id
             where l.classroom_id = ? and l.id = ? and a.classroom_student_id = ?
             for update of l
            """, rs -> rs.next() ? new OwnedLoan(rs.getLong(1), rs.getString(2)) : null,
            classroomId, loanId, studentId);
        if (loan == null) throw operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "대출을 찾을 수 없습니다.");
        if (expectedStateVersion != null && expectedStateVersion != loan.stateVersion()) {
            throw operation(HttpStatus.CONFLICT, "STATE_VERSION_CONFLICT", "대출 상태가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        if (!List.of("ACTIVE", "OVERDUE").contains(loan.status())) {
            throw operation(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "상환 중이거나 연체된 대출만 상환할 수 있습니다.");
        }
        Instant now = clock.instant();
        boolean replayed;
        if ("RETRY_DUE_PAYMENT".equals(command)) {
            DueInstallment installment = dueInstallment(classroomId, loanId, studentId, now);
            if (installment == null) {
                throw operation(HttpStatus.CONFLICT, "DUE_INSTALLMENT_NOT_FOUND", "현재 다시 납부할 회차가 없습니다.");
            }
            if ("MISSED".equals(installment.status()) && now.isAfter(installment.graceUntil())) {
                throw operation(HttpStatus.CONFLICT, "GRACE_WINDOW_EXPIRED", "유예 기간이 지나 전액 상환으로 정리해야 합니다.");
            }
            replayed = process(
                classroomId,
                installment,
                now,
                LedgerActorType.STUDENT,
                studentId,
                requestId.toString(),
                correlationId.toString(),
                REPAYMENT_ROUTE,
                true
            );
        } else if ("PREPAY_ALL".equals(command)) {
            replayed = prepayAll(classroomId, loanId, studentId, requestId, correlationId, now);
        } else {
            throw operation(HttpStatus.BAD_REQUEST, "INVALID_LOAN_COMMAND", "상환 명령은 RETRY_DUE_PAYMENT 또는 PREPAY_ALL이어야 합니다.");
        }
        return result(classroomId, loanId, studentId, replayed);
    }

    private boolean process(UUID classroomId, DueInstallment installment, Instant now) {
        return process(
            classroomId,
            installment,
            now,
            LedgerActorType.SYSTEM,
            classroomId,
            installment.installmentId().toString(),
            uuids.generate().toString(),
            "/system/loan-installments/{id}/repayment",
            false
        );
    }

    private boolean process(
        UUID classroomId,
        DueInstallment installment,
        Instant now,
        LedgerActorType actorType,
        UUID actorId,
        String idempotencyKey,
        String correlationId,
        String route,
        boolean failOnInsufficientBalance
    ) {
        if ("MISSED".equals(installment.status()) && now.isAfter(installment.graceUntil())) {
            return false;
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
            if (failOnInsufficientBalance) {
                throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE", "지갑 잔액이 이번 상환 금액보다 부족합니다.");
            }
            markMissed(classroomId, installment, now);
            return false;
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
        var transaction = transactions.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                classroomId,
                "LOAN_REPAYMENT",
                actorType,
                actorId,
                null,
                route,
                idempotencyKey,
                installment.installmentId().toString(),
                correlationId,
                "대출 상환",
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
        return transaction.replayed();
    }

    private DueInstallment dueInstallment(UUID classroomId, UUID loanId, UUID studentId, Instant now) {
        return jdbc.query("""
            select i.id, i.loan_id, a.classroom_student_id,
                   i.scheduled_principal, i.scheduled_interest, i.grace_until, i.status,
                   a.loan_policy_version_id
              from loan_installments i
              join loans l on l.id = i.loan_id
              join loan_applications a on a.id = l.loan_application_id
              join loan_policy_versions p on p.id = a.loan_policy_version_id
             where i.classroom_id = ? and i.loan_id = ? and a.classroom_student_id = ?
               and i.status in ('SCHEDULED', 'MISSED') and i.due_at <= ?
             order by i.due_at limit 1
             for update of i
            """, rs -> rs.next() ? installment(rs) : null, classroomId, loanId, studentId, now);
    }

    private boolean prepayAll(
        UUID classroomId,
        UUID loanId,
        UUID studentId,
        UUID requestId,
        UUID correlationId,
        Instant now
    ) {
        List<DueInstallment> remaining = jdbc.query("""
            select i.id, i.loan_id, a.classroom_student_id,
                   i.scheduled_principal, i.scheduled_interest, i.grace_until, i.status,
                   a.loan_policy_version_id
              from loan_installments i
              join loans l on l.id = i.loan_id
              join loan_applications a on a.id = l.loan_application_id
              join loan_policy_versions p on p.id = a.loan_policy_version_id
             where i.classroom_id = ? and i.loan_id = ? and a.classroom_student_id = ?
               and i.status in ('SCHEDULED', 'MISSED')
             order by i.installment_number
             for update of i
            """, (rs, rowNum) -> installment(rs), classroomId, loanId, studentId);
        if (remaining.isEmpty()) {
            throw operation(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "이미 모든 상환이 완료되었습니다.");
        }
        long principal = remaining.stream().mapToLong(DueInstallment::principal).sum();
        long interest = remaining.stream().mapToLong(DueInstallment::interest).sum();
        long total = principal + interest;
        LedgerAccount wallet = ledger
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId, studentId, LedgerAccountType.STUDENT_WALLET, LedgerAccountStatus.ACTIVE
            )
            .orElse(null);
        if (wallet == null || wallet.getCurrentBalance() < total) {
            throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE", "지갑 잔액이 전액 상환 금액보다 부족합니다.");
        }
        LedgerAccount receivable = ledger
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId, "LOAN_RECEIVABLE", LedgerAccountType.LOAN_RECEIVABLE, LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledger.save(LedgerAccount.systemAccount(
                uuids.generate(), classroomId, "LOAN_RECEIVABLE", LedgerAccountType.LOAN_RECEIVABLE, now
            )));
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(wallet.getId(), -total));
        postings.add(new LedgerTransactionPosting(receivable.getId(), principal));
        if (interest > 0) {
            LedgerAccount issuance = ledger
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId, "MONEY_ISSUANCE", LedgerAccountType.MONEY_ISSUANCE, LedgerAccountStatus.ACTIVE
                )
                .orElseGet(() -> ledger.save(LedgerAccount.systemAccount(
                    uuids.generate(), classroomId, "MONEY_ISSUANCE", LedgerAccountType.MONEY_ISSUANCE, now
                )));
            postings.add(new LedgerTransactionPosting(issuance.getId(), interest));
        }
        UUID groupId = uuids.generate();
        var transaction = transactions.execute(
            groupId,
            LedgerTransactionCommand.original(
                classroomId,
                "LOAN_PREPAYMENT",
                LedgerActorType.STUDENT,
                studentId,
                null,
                REPAYMENT_ROUTE,
                requestId.toString(),
                "PREPAY_ALL|" + loanId,
                correlationId.toString(),
                "대출 중도 상환",
                postings,
                now
            ),
            List.of(new LedgerDomainMutation() {
                @Override public void validate() { }
                @Override public void apply() {
                    jdbc.update("update loan_installments set paid_principal=scheduled_principal,paid_interest=scheduled_interest,paid_at=?,transaction_group_id=?,status='PAID',state_version=state_version+1,status_changed_at=?,updated_at=? where classroom_id=? and loan_id=? and status in ('SCHEDULED','MISSED')", now, groupId, now, now, classroomId, loanId);
                    jdbc.update("update loans set outstanding_principal=0,outstanding_interest=0,status='REPAID',closed_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where classroom_id=? and id=?", now, now, now, classroomId, loanId);
                    changeCredit(classroomId, studentId, remaining.get(0).repaidCredit(),
                        "LOAN_REPAID", "대출을 전액 상환했어요.", loanId, now);
                    publishRepayment(classroomId, studentId, loanId, "REPAID");
                }
            })
        );
        return transaction.replayed();
    }

    private LoanRepaymentResult result(UUID classroomId, UUID loanId, UUID studentId, boolean replayed) {
        return jdbc.query("""
            select l.status,l.state_version,l.outstanding_principal,l.outstanding_interest,
                   cp.score,cp.level
              from loans l
              join loan_applications a on a.id=l.loan_application_id
              join credit_profiles cp on cp.classroom_id=l.classroom_id
               and cp.classroom_student_id=a.classroom_student_id
             where l.classroom_id=? and l.id=? and a.classroom_student_id=?
            """, rs -> {
                if (!rs.next()) throw operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "대출 결과를 찾을 수 없습니다.");
                return new LoanRepaymentResult(loanId, rs.getString(1), rs.getLong(2), rs.getLong(3),
                    rs.getLong(4), rs.getInt(5), rs.getString(6), replayed);
            }, classroomId, loanId, studentId);
    }

    private Replay replay(
        UUID classroomId,
        UUID loanId,
        UUID studentId,
        String command,
        UUID requestId
    ) {
        return jdbc.query("""
            select r.request_hash, g.transaction_type
              from idempotency_records r
              join transaction_groups g on g.id = r.transaction_group_id
             where r.classroom_id = ? and r.actor_id = ?
               and r.route_key = ? and r.idempotency_key = ?
            """, rs -> {
                if (!rs.next()) return null;
                String requestHash = rs.getString(1);
                String transactionType = rs.getString(2);
                boolean matches;
                if ("PREPAY_ALL".equals(command)) {
                    matches = "LOAN_PREPAYMENT".equals(transactionType)
                        && ("PREPAY_ALL|" + loanId).equals(requestHash);
                } else if ("RETRY_DUE_PAYMENT".equals(command)
                    && "LOAN_REPAYMENT".equals(transactionType)) {
                    try {
                        UUID installmentId = UUID.fromString(requestHash);
                        Integer count = jdbc.queryForObject(
                            "select count(*) from loan_installments where classroom_id=? and loan_id=? and id=?",
                            Integer.class,
                            classroomId,
                            loanId,
                            installmentId
                        );
                        matches = count != null && count == 1;
                    } catch (IllegalArgumentException exception) {
                        matches = false;
                    }
                } else {
                    matches = false;
                }
                return new Replay(matches);
            }, classroomId, studentId, REPAYMENT_ROUTE, requestId.toString());
    }

    private DueInstallment installment(java.sql.ResultSet rs) throws java.sql.SQLException {
        var policy = loanPolicies.findById(rs.getObject(8, UUID.class)).orElseThrow();
        var adjustments = policy.getCreditAdjustments();
        return new DueInstallment(
            rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
            rs.getLong(4), rs.getLong(5), rs.getTimestamp(6).toInstant(), rs.getString(7),
            adjustment(adjustments.get("on_time"), 10),
            adjustment(adjustments.get("overdue"), -30),
            adjustment(adjustments.get("repaid"), 20)
        );
    }

    private static int adjustment(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
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

    private record OwnedLoan(long stateVersion, String status) { }

    private record Replay(boolean matches) { }

    public record LoanRepaymentResult(
        UUID loanId,
        String status,
        long stateVersion,
        long remainingPrincipal,
        long remainingInterest,
        int creditScore,
        String creditLevel,
        boolean replayed
    ) { }

    private static UUID student(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (Exception exception) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "학생 로그인이 필요합니다.");
        }
    }

    private static ClassroomOperationException operation(HttpStatus status, String code, String message) {
        return new ClassroomOperationException(status, code, message);
    }
}
