package com.b201.ecovillage.finance.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;

class DepositMaturitySchedulerTest {

    @Test
    void settlesEveryClassroomWithDueDeposits() {
        Instant now = Instant.parse("2026-08-03T03:00:00Z");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DepositAccountRepository repository = mock(DepositAccountRepository.class);
        DepositMaturityService service = mock(DepositMaturityService.class);
        when(repository.findDueClassroomIds(DepositAccountStatus.ACTIVE, now))
            .thenReturn(List.of(first, second));
        DepositMaturityScheduler scheduler = new DepositMaturityScheduler(
            repository,
            service,
            Clock.fixed(now, ZoneOffset.UTC)
        );

        scheduler.settleDueDeposits();

        verify(service).matureDueAccounts(first);
        verify(service).matureDueAccounts(second);
    }
}
