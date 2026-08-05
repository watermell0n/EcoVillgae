package com.b201.ecovillage.finance.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.b201.ecovillage.finance.domain.SavingsAccountStatus;
import com.b201.ecovillage.finance.repository.SavingsAccountRepository;

@Component
public class SavingsSettlementScheduler {
    private static final Logger log = LoggerFactory.getLogger(SavingsSettlementScheduler.class);
    private final SavingsAccountRepository accounts; private final SavingsSettlementService service;
    public SavingsSettlementScheduler(SavingsAccountRepository accounts, SavingsSettlementService service) { this.accounts = accounts; this.service = service; }
    @Scheduled(fixedDelayString = "${ecovillage.finance.savings-settlement-delay-ms:60000}")
    public void settleDueSavings() { for (var classroomId : accounts.findActiveClassroomIds(SavingsAccountStatus.ACTIVE)) try { service.settleActiveAccounts(classroomId); } catch (RuntimeException exception) { log.error("Failed to settle savings for classroom {}", classroomId, exception); } }
}
