package com.b201.ecovillage.finance.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.b201.ecovillage.finance.domain.SavingsAccountStatus;
import com.b201.ecovillage.finance.repository.SavingsAccountRepository;

@Service
public class SavingsSettlementService {
    private final SavingsAccountRepository accounts; private final SavingsSettlementProcessor processor; private final Clock clock;
    public SavingsSettlementService(SavingsAccountRepository accounts, SavingsSettlementProcessor processor, Clock clock) { this.accounts = accounts; this.processor = processor; this.clock = clock; }
    @Transactional(readOnly = true)
    public int settleActiveAccounts(UUID classroomId) { var now = clock.instant(); int count = 0; for (UUID id : accounts.findAllByClassroomIdAndStatus(classroomId, SavingsAccountStatus.ACTIVE).stream().map(account -> account.getId()).toList()) if (processor.settle(classroomId, id, now)) count++; return count; }
}
