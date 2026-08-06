package com.b201.ecovillage.ai.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.ruleengine.domain.ProductKind;
import com.b201.ecovillage.ruleengine.domain.QuizCategory;
import com.b201.ecovillage.ruleengine.domain.QuizMode;
import com.b201.ecovillage.ruleengine.domain.RuleContextType;

public record SetupPrecheckInput(
    RuleContextType contextType,
    Integer activeStudentCount,
    Long systemAmountCap,
    Long nonRewardIssuance,
    Long existingRewardIssuance,
    Long expectedRecovery,
    Long otherPeriodicIssuancePerStudent,
    Integer effectiveTaxBps,
    ModuleActivation moduleActivation,
    SalaryPolicy salaryPolicy,
    ProductDraft productDraft,
    QuizDraft quizDraft,
    InvestmentDraft investmentDraft
) {
    public record ModuleActivation(
        String moduleCode,
        UUID targetId,
        Long confirmedValue,
        Integer currentDepositRateBps
    ) {
    }

    public record SalaryPolicy(
        List<SalaryRole> roles,
        Integer payIntervalDays,
        LocalDate firstPayOn,
        Long defaultRoleSalary
    ) {
        public SalaryPolicy {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record SalaryRole(
        String roleCode,
        String name,
        BigDecimal weight,
        Long confirmedSalary,
        Integer assignedStudentCount,
        Boolean active
    ) {
    }

    public record ProductDraft(
        UUID targetId,
        ProductKind kind,
        Integer stock,
        Integer expectedDemand,
        BigDecimal expectedPurchaseFrequency,
        Integer vatBps,
        Boolean free,
        Long confirmedPrice
    ) {
    }

    public record QuizDraft(
        UUID targetId,
        QuizMode mode,
        QuizCategory category,
        Integer questionCount,
        Integer expectedParticipants,
        Integer expectedRuns,
        Long confirmedRewardPerPerson
    ) {
    }

    /**
     * 투자 조건 추천 요청이다. 종목 목록과 학급 지표(발행액·학생 잔액)는 서버가 채우므로
     * 화면은 어떤 방식을 여는지와 지금 화면에 적혀 있는 값만 보낸다.
     */
    public record InvestmentDraft(
        String investmentMode,
        List<AssetCondition> confirmedAssetConditions
    ) {
        public InvestmentDraft {
            confirmedAssetConditions = confirmedAssetConditions == null
                ? List.of()
                : List.copyOf(confirmedAssetConditions);
        }
    }

    /** bandBps는 하루 변동 범위(±bps)다. 화면에는 -3.00% ~ +3.00%처럼 양쪽으로 보여 준다. */
    public record AssetCondition(String symbol, Long price, Long bandBps) {
    }
}
