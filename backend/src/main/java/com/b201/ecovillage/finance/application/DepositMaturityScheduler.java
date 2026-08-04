package com.b201.ecovillage.finance.application;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;

@Component
public class DepositMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(
        DepositMaturityScheduler.class
    );

    private final DepositAccountRepository accountRepository;
    private final DepositMaturityService maturityService;
    private final Clock clock;

    public DepositMaturityScheduler(
        DepositAccountRepository accountRepository,
        DepositMaturityService maturityService,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.maturityService = maturityService;
        this.clock = clock;
    }

    @Scheduled(
        fixedDelayString = "${ecovillage.finance.deposit-maturity-delay-ms:60000}"
    )
    public void settleDueDeposits() {
        for (UUID classroomId : accountRepository.findDueClassroomIds(
            DepositAccountStatus.ACTIVE,
            clock.instant()
        )) {
            try {
                maturityService.matureDueAccounts(classroomId);
            } catch (RuntimeException exception) {
                log.error(
                    "Failed to settle due deposits for classroom {}",
                    classroomId,
                    exception
                );
            }
        }
    }
}
