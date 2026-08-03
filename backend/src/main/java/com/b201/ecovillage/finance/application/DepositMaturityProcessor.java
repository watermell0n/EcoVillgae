package com.b201.ecovillage.finance.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
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
public class DepositMaturityProcessor {

    private static final String DEPOSIT_LIABILITY_KEY = "DEPOSIT_LIABILITY";
    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";

    private final DepositAccountRepository accountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;

    public DepositMaturityProcessor(
        DepositAccountRepository accountRepository,
        LedgerAccountRepository ledgerAccountRepository,
        AtomicLedgerTransactionService transactionService,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator
    ) {
        this.accountRepository = accountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mature(UUID classroomId, UUID accountId, Instant now) {
        DepositAccount account = accountRepository.findForUpdate(classroomId, accountId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The deposit account could not be found."
            ));
        if (
            account.getStatus() != DepositAccountStatus.ACTIVE
                || now.isBefore(account.getMaturesAt())
        ) {
            return false;
        }

        LedgerAccount wallet = wallet(account, now);
        LedgerAccount liability = systemAccount(
            classroomId,
            DEPOSIT_LIABILITY_KEY,
            LedgerAccountType.DEPOSIT_LIABILITY,
            now
        );
        LedgerAccount issuance = account.getExpectedInterestAmount() == 0
            ? null
            : systemAccount(
                classroomId,
                MONEY_ISSUANCE_KEY,
                LedgerAccountType.MONEY_ISSUANCE,
                now
            );
        UUID groupId = uuidGenerator.generate();
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(
            new LedgerTransactionPosting(
                liability.getId(),
                -account.getPrincipalAmount()
            )
        );
        postings.add(
            new LedgerTransactionPosting(wallet.getId(), account.maturityAmount())
        );
        if (issuance != null) {
            postings.add(
                new LedgerTransactionPosting(
                    issuance.getId(),
                    -account.getExpectedInterestAmount()
                )
            );
        }
        LedgerDomainMutation maturity = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (
                    account.getStatus() != DepositAccountStatus.ACTIVE
                        || now.isBefore(account.getMaturesAt())
                ) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "INVALID_STATE_TRANSITION",
                        "The deposit is not eligible for maturity."
                    );
                }
                if (liability.getCurrentBalance() < account.getPrincipalAmount()) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "LIABILITY_BALANCE_MISMATCH",
                        "The deposit liability cannot cover the principal."
                    );
                }
            }

            @Override
            public void apply() {
                account.mature(groupId, now);
                accountRepository.save(account);
            }
        };
        transactionService.execute(
            groupId,
            LedgerTransactionCommand.original(
                classroomId,
                "DEPOSIT_MATURITY",
                LedgerActorType.SYSTEM,
                classroomId,
                null,
                "/system/deposit-accounts/{account_id}/maturity",
                accountId.toString(),
                accountId.toString(),
                uuidGenerator.generate().toString(),
                "Settle fixed-term deposit maturity",
                postings,
                now
            ),
            List.of(maturity)
        );
        accountRepository.flush();
        eventPublisher.publish(
            classroomId,
            "STUDENT_PRIVATE",
            account.getClassroomStudentId(),
            "deposit.account.matured",
            "DEPOSIT_ACCOUNT",
            account.getId(),
            account.getStateVersion(),
            Map.of(
                "account_id", account.getId().toString(),
                "status", account.getStatus().name()
            ),
            false
        );
        return true;
    }

    private LedgerAccount wallet(DepositAccount account, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                account.getClassroomId(),
                account.getClassroomStudentId(),
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.studentWallet(
                    uuidGenerator.generate(),
                    account.getClassroomId(),
                    account.getClassroomStudentId(),
                    now
                )
            ));
    }

    private LedgerAccount systemAccount(
        UUID classroomId,
        String ownerKey,
        LedgerAccountType type,
        Instant now
    ) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                ownerKey,
                type,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    ownerKey,
                    type,
                    now
                )
            ));
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
