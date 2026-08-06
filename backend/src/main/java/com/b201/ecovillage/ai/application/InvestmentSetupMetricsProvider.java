package com.b201.ecovillage.ai.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;

/**
 * 투자 조건 추천에 쓰는 학급 지표를 모은다.
 *
 * <p>교사 화면은 학생 지갑을 모아 볼 수 없으므로 화면에서 받지 않고 여기서 직접 읽는다.
 */
@Component
class InvestmentSetupMetricsProvider {

    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";

    private final LedgerAccountRepository accountRepository;

    InvestmentSetupMetricsProvider(LedgerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    InvestmentSetupMetrics of(UUID classroomId) {
        List<Long> balances = accountRepository.findActiveStudentWalletBalances(classroomId);
        return new InvestmentSetupMetrics(moneyIssuance(classroomId), average(balances), median(balances));
    }

    /**
     * 지금까지 발행한 화폐의 총액이다. 발행 계정은 돈을 내보낼 때마다 음수로 내려가므로
     * 부호를 뒤집어 읽는다. 회수로 다시 올라간 경우에는 0 아래로 내려가지 않게 막는다.
     */
    private long moneyIssuance(UUID classroomId) {
        return accountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                MONEY_ISSUANCE_KEY,
                LedgerAccountType.MONEY_ISSUANCE,
                LedgerAccountStatus.ACTIVE
            )
            .map(LedgerAccount::getCurrentBalance)
            .map(balance -> Math.max(0L, -balance))
            .orElse(0L);
    }

    private static long average(List<Long> balances) {
        if (balances.isEmpty()) {
            return 0L;
        }
        return balances.stream().mapToLong(Long::longValue).sum() / balances.size();
    }

    /** 목록은 저장소가 오름차순으로 준다. 짝수면 가운데 두 값의 평균을 쓴다. */
    private static long median(List<Long> balances) {
        if (balances.isEmpty()) {
            return 0L;
        }
        int middle = balances.size() / 2;
        if (balances.size() % 2 == 1) {
            return balances.get(middle);
        }
        return (balances.get(middle - 1) + balances.get(middle)) / 2;
    }

    record InvestmentSetupMetrics(long moneyIssuance, long averageBalance, long medianBalance) {
    }
}
