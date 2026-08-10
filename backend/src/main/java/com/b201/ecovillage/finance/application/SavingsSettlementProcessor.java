package com.b201.ecovillage.finance.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.SavingsAccount;
import com.b201.ecovillage.finance.domain.SavingsAccountStatus;
import com.b201.ecovillage.finance.domain.SavingsInstallment;
import com.b201.ecovillage.finance.repository.SavingsAccountRepository;
import com.b201.ecovillage.finance.repository.SavingsInstallmentRepository;
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
public class SavingsSettlementProcessor {
    private static final String LIABILITY = "SAVINGS_LIABILITY";
    private static final String ISSUANCE = "MONEY_ISSUANCE";
    private final SavingsAccountRepository accounts;
    private final SavingsInstallmentRepository installments;
    private final LedgerAccountRepository ledger;
    private final AtomicLedgerTransactionService transactions;
    private final RealtimeEventPublisher events;
    private final UuidV7Generator uuids;

    public SavingsSettlementProcessor(SavingsAccountRepository accounts, SavingsInstallmentRepository installments, LedgerAccountRepository ledger, AtomicLedgerTransactionService transactions, RealtimeEventPublisher events, UuidV7Generator uuids) {
        this.accounts = accounts; this.installments = installments; this.ledger = ledger; this.transactions = transactions; this.events = events; this.uuids = uuids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settle(UUID classroomId, UUID accountId, Instant now) {
        SavingsAccount account = accounts.findForUpdate(classroomId, accountId).orElseThrow();
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) return false;
        LedgerAccount wallet = wallet(account, now);
        LedgerAccount liability = system(classroomId, LIABILITY, LedgerAccountType.SAVINGS_LIABILITY, now);
        for (SavingsInstallment installment : installments.findAllByClassroomIdAndSavingsAccountIdOrderByDueAt(classroomId, accountId)) {
            if (!"SCHEDULED".equals(installment.getStatus()) || now.isBefore(installment.getDueAt())) continue;
            if (wallet.getCurrentBalance() >= installment.getScheduledAmount()) pay(account, installment, wallet, liability, now);
            else if (!now.isBefore(account.getMaturesAt()) || !now.isBefore(installment.getRetryableUntil())) installment.miss(now);
        }
        if (!now.isBefore(account.getMaturesAt())) mature(account, wallet, liability, now);
        accounts.save(account);
        return true;
    }

    private void pay(SavingsAccount account, SavingsInstallment installment, LedgerAccount wallet, LedgerAccount liability, Instant now) {
        UUID groupId = uuids.generate();
        transactions.execute(groupId, LedgerTransactionCommand.original(account.getClassroomId(), "SAVINGS_INSTALLMENT", LedgerActorType.SYSTEM, account.getClassroomId(), null, "/system/savings-accounts/{account_id}/installments", installment.getId().toString(), installment.getId().toString(), uuids.generate().toString(), "적금 납입", List.of(new LedgerTransactionPosting(wallet.getId(), -installment.getScheduledAmount()), new LedgerTransactionPosting(liability.getId(), installment.getScheduledAmount())), now), List.of(new LedgerDomainMutation() {
            @Override public void validate() { if (wallet.getCurrentBalance() < installment.getScheduledAmount()) throw new IllegalStateException("wallet balance changed"); }
            @Override public void apply() { installment.pay(groupId, now); account.recordInstallment(now); }
        }));
    }

    private void mature(SavingsAccount account, LedgerAccount wallet, LedgerAccount liability, Instant now) {
        long interest = account.isFullyPaid() ? account.getExpectedInterestAmount() : 0;
        LedgerAccount issuance = interest == 0 ? null : system(account.getClassroomId(), ISSUANCE, LedgerAccountType.MONEY_ISSUANCE, now);
        UUID groupId = uuids.generate();
        java.util.ArrayList<LedgerTransactionPosting> postings = new java.util.ArrayList<>(List.of(new LedgerTransactionPosting(liability.getId(), -account.getPrincipalBalance()), new LedgerTransactionPosting(wallet.getId(), account.maturityAmount())));
        if (issuance != null) postings.add(new LedgerTransactionPosting(issuance.getId(), -interest));
        transactions.execute(groupId, LedgerTransactionCommand.original(account.getClassroomId(), "SAVINGS_MATURITY", LedgerActorType.SYSTEM, account.getClassroomId(), null, "/system/savings-accounts/{account_id}/maturity", account.getId().toString(), account.getId().toString(), uuids.generate().toString(), "적금 만기 지급", postings, now), List.of(new LedgerDomainMutation() {
            @Override public void validate() { if (liability.getCurrentBalance() < account.getPrincipalBalance()) throw new IllegalStateException("savings liability cannot cover principal"); }
            @Override public void apply() { account.mature(groupId, now); }
        }));
        events.publish(account.getClassroomId(), "STUDENT_PRIVATE", account.getClassroomStudentId(), "savings.account.matured", "SAVINGS_ACCOUNT", account.getId(), account.getStateVersion(), Map.of("account_id", account.getId().toString(), "status", account.getStatus().name()), false);
    }

    private LedgerAccount wallet(SavingsAccount a, Instant n) { return ledger.findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(a.getClassroomId(), a.getClassroomStudentId(), LedgerAccountType.STUDENT_WALLET, LedgerAccountStatus.ACTIVE).orElseGet(() -> ledger.save(LedgerAccount.studentWallet(uuids.generate(), a.getClassroomId(), a.getClassroomStudentId(), n))); }
    private LedgerAccount system(UUID c, String key, LedgerAccountType type, Instant n) { return ledger.findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(c, key, type, LedgerAccountStatus.ACTIVE).orElseGet(() -> ledger.save(LedgerAccount.systemAccount(uuids.generate(), c, key, type, n))); }
}
