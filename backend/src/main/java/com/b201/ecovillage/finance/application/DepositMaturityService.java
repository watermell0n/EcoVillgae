package com.b201.ecovillage.finance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;

@Service
public class DepositMaturityService {

    private final DepositAccountRepository accountRepository;
    private final DepositMaturityProcessor processor;
    private final Clock clock;

    public DepositMaturityService(
        DepositAccountRepository accountRepository,
        DepositMaturityProcessor processor,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.processor = processor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int matureDueAccounts(UUID classroomId) {
        Instant now = clock.instant();
        List<UUID> accountIds = accountRepository
            .findTop100ByClassroomIdAndStatusAndMaturesAtLessThanEqualOrderByMaturesAt(
                classroomId,
                DepositAccountStatus.ACTIVE,
                now
            )
            .stream()
            .map(DepositAccount::getId)
            .toList();
        int matured = 0;
        for (UUID accountId : accountIds) {
            if (processor.mature(classroomId, accountId, now)) {
                matured++;
            }
        }
        return matured;
    }
}
