package com.b201.ecovillage.ruleengine.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.b201.ecovillage.ruleengine.domain.PaymentSchedule;
import com.b201.ecovillage.ruleengine.domain.ProductKind;
import com.b201.ecovillage.ruleengine.domain.QuizCategory;
import com.b201.ecovillage.ruleengine.domain.QuizMode;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleContextType;
import com.b201.ecovillage.ruleengine.domain.RuleEngineRequest;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;
import com.b201.ecovillage.ruleengine.domain.RuleSeverity;
import com.b201.ecovillage.ruleengine.domain.RuleTargetType;

class RuleEngineServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 30);
    private static final UUID TARGET_ID = UUID.fromString(
        "44444444-4444-4444-8444-444444444444"
    );

    private final RuleEngineService service = new RuleEngineService();

    @Test
    void classroomSetupAllowsNoModulesAndCreatesNoNumericCandidate() {
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.CLASSROOM_SETUP).build()
        );

        assertEquals(RuleSeverity.PASS, result.overallSeverity());
        assertEquals("rule-engine-v2", result.ruleVersion());
        assertTrue(result.candidates().isEmpty());
        assertReason(result, "NO_ECONOMIC_MODULES_SELECTED");
        assertEquals(4, result.metrics().paymentSchedule().paymentCount());
    }

    @Test
    void createsSalaryCandidatesPerRoleUsingGradeBandAndOneUnitRounding() {
        RuleEngineRequest.SalaryPolicy salaryPolicy = new RuleEngineRequest.SalaryPolicy(
            List.of(
                new RuleEngineRequest.SalaryRole(
                    "LEADER",
                    new BigDecimal("1.2"),
                    null,
                    0,
                    true
                ),
                new RuleEngineRequest.SalaryRole(
                    "HELPER",
                    new BigDecimal("0.8"),
                    801L,
                    0,
                    true
                )
            ),
            7,
            START.plusDays(6),
            1_000L
        );
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .moduleActivation(module("ROLE_SALARY", null, null))
                .salaryPolicy(salaryPolicy)
                .build()
        );

        assertEquals(2, result.candidates().size());
        RuleCandidate leader = result.candidates().getFirst();
        assertEquals(1_200L, leader.proposedValue());
        assertEquals(1_020L, leader.recommendedMinValue());
        assertEquals(1_380L, leader.recommendedMaxValue());
        assertEquals(100L, leader.allowedMinValue());
        assertEquals(1_400L, leader.allowedMaxValue());
        RuleCandidate helper = result.candidates().get(1);
        assertEquals(800L, helper.proposedValue());
        assertEquals(680L, helper.recommendedMinValue());
        assertEquals(920L, helper.recommendedMaxValue());
    }

    @Test
    void createsTaxDepositAndLoanCandidatesOnlyInTheirActivationContext() {
        RuleEngineResult tax = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeModules(Set.of("ROLE_SALARY"))
                .moduleActivation(module("INCOME_TAX", 1_000L, null))
                .build()
        );
        RuleEngineResult deposit = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .moduleActivation(module("DEPOSIT", 300L, null))
                .build()
        );
        RuleEngineResult loan = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeModules(Set.of("DEPOSIT"))
                .moduleActivation(module("LOAN", 700L, 300))
                .build()
        );
        RuleEngineResult store = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .moduleActivation(module("STORE", null, null))
                .build()
        );

        assertCandidate(tax, RuleTargetType.INCOME_TAX_RATE, 500L, 400L, 600L, 300L, 800L);
        assertCandidate(deposit, RuleTargetType.DEPOSIT_RATE, 300L, 200L, 400L, 100L, 600L);
        assertCandidate(loan, RuleTargetType.LOAN_RATE, 700L, 600L, 800L, 500L, 1_200L);
        assertTrue(store.candidates().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "4, 300, 200, 400, 200, 500",
        "5, 500, 400, 600, 300, 800",
        "6, 1000, 900, 1100, 500, 1500"
    })
    void appliesTaxRecommendationProfileForEverySupportedGrade(
        int grade,
        long proposed,
        long recommendedMin,
        long recommendedMax,
        long allowedMin,
        long allowedMax
    ) {
        RuleEngineResult result = service.evaluate(
            RuleEngineRequest.builder(
                RuleContextType.MODULE_ACTIVATION,
                grade,
                START,
                END
            )
                .activeModules(Set.of("ROLE_SALARY"))
                .moduleActivation(module("INCOME_TAX", null, null))
                .build()
        );

        assertCandidate(
            result,
            RuleTargetType.INCOME_TAX_RATE,
            proposed,
            recommendedMin,
            recommendedMax,
            allowedMin,
            allowedMax
        );
    }

    @Test
    void calculatesProductPriceFromCurrentSalaryTaxVatAndScarcity() {
        RuleEngineRequest.SalaryPolicy salaryPolicy = new RuleEngineRequest.SalaryPolicy(
            List.of(
                new RuleEngineRequest.SalaryRole(
                    "DEFAULT",
                    BigDecimal.ONE,
                    1_000L,
                    20,
                    true
                )
            ),
            7,
            START.plusDays(6),
            1_000L
        );
        RuleEngineRequest.ProductDraft product = new RuleEngineRequest.ProductDraft(
            TARGET_ID,
            ProductKind.NORMAL,
            100,
            100,
            BigDecimal.ONE,
            1_000,
            false,
            259L
        );
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.PRODUCT_DRAFT)
                .activeStudentCount(20)
                .activeModules(Set.of("ROLE_SALARY", "INCOME_TAX"))
                .effectiveTaxBps(500)
                .salaryPolicy(salaryPolicy)
                .systemAmountCap(500L)
                .productDraft(product)
                .build()
        );

        RuleCandidate candidate = onlyCandidate(result);
        assertEquals(1_000L, result.metrics().referenceSalary());
        assertEquals(950L, result.metrics().netReferenceSalary());
        assertEquals(259L, candidate.proposedValue());
        assertEquals(220L, candidate.recommendedMinValue());
        assertEquals(298L, candidate.recommendedMaxValue());
        assertEquals(RuleSeverity.PASS, result.overallSeverity());
    }

    @Test
    void freeProductCreatesNoPriceCandidate() {
        RuleEngineRequest.ProductDraft product = new RuleEngineRequest.ProductDraft(
            TARGET_ID,
            ProductKind.NORMAL,
            1,
            1,
            BigDecimal.ONE,
            0,
            true,
            0L
        );
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.PRODUCT_DRAFT)
                .otherPeriodicIssuancePerStudent(1_000L)
                .productDraft(product)
                .build()
        );

        assertTrue(result.candidates().isEmpty());
        assertFalse(hasReason(result, "VALUE_OUT_OF_RANGE"));
    }

    @Test
    void increasesProductPriceWhenExpectedDemandExceedsStock() {
        RuleEngineRequest.ProductDraft product = new RuleEngineRequest.ProductDraft(
            TARGET_ID,
            ProductKind.NORMAL,
            50,
            100,
            BigDecimal.ONE,
            0,
            false,
            null
        );
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.PRODUCT_DRAFT)
                .otherPeriodicIssuancePerStudent(1_000L)
                .productDraft(product)
                .build()
        );

        assertEquals(330L, onlyCandidate(result).proposedValue());
    }

    @Test
    void economyAndNonsenseRewardsUseFivePercentAndThirtyPercentAdjustment() {
        RuleEngineResult economy = rewardResult(QuizCategory.ECONOMY, null, 7_000L, 0L, 7_000L);
        RuleEngineResult nonsense = rewardResult(
            QuizCategory.NONSENSE,
            null,
            7_000L,
            0L,
            7_000L
        );

        assertEquals(50L, onlyCandidate(economy).proposedValue());
        assertEquals(15L, onlyCandidate(nonsense).proposedValue());
        assertEquals(500L, economy.metrics().candidateRewardIssuance());
        assertEquals(150L, nonsense.metrics().candidateRewardIssuance());
    }

    @Test
    void floorsNonsenseRewardSoItNeverExceedsThirtyPercentOfEconomyReward() {
        RuleEngineResult economy = rewardResult(
            QuizCategory.ECONOMY,
            null,
            20_000L,
            0L,
            20_000L,
            1,
            1,
            1_170L
        );
        RuleEngineResult nonsense = rewardResult(
            QuizCategory.NONSENSE,
            null,
            20_000L,
            0L,
            20_000L,
            1,
            1,
            1_170L
        );

        assertEquals(59L, onlyCandidate(economy).proposedValue());
        assertEquals(17L, onlyCandidate(nonsense).proposedValue());
    }

    @Test
    void rewardShareAtThirtyPercentWarnsAndAboveThirtyPercentBlocks() {
        RuleEngineResult atLimit = rewardResult(
            QuizCategory.ECONOMY,
            300L,
            700L,
            0L,
            1_000L,
            1,
            1
        );
        RuleEngineResult aboveLimit = rewardResult(
            QuizCategory.ECONOMY,
            301L,
            700L,
            0L,
            1_001L,
            1,
            1
        );

        assertEquals(new BigDecimal("0.3"), atLimit.metrics().rewardIssuanceShare());
        assertReason(atLimit, "REWARD_SHARE_HIGH");
        assertFalse(hasReason(atLimit, "REWARD_ISSUANCE_SHARE_EXCEEDED"));
        assertEquals(RuleSeverity.WARN, atLimit.overallSeverity());
        assertReason(aboveLimit, "REWARD_ISSUANCE_SHARE_EXCEEDED");
        assertEquals(RuleSeverity.BLOCK, aboveLimit.overallSeverity());
    }

    @Test
    void rewardShareAtTwentyFivePercentDoesNotWarn() {
        RuleEngineResult result = rewardResult(
            QuizCategory.ECONOMY,
            100L,
            300L,
            0L,
            400L,
            1,
            1
        );

        assertEquals(new BigDecimal("0.25"), result.metrics().rewardIssuanceShare());
        assertFalse(hasReason(result, "REWARD_SHARE_HIGH"));
        assertFalse(hasReason(result, "REWARD_ISSUANCE_SHARE_EXCEEDED"));
    }

    @Test
    void noNonRewardIssuanceLeavesNoRewardCapacityAndBlocksExistingRewards() {
        RuleEngineResult result = rewardResult(
            QuizCategory.ECONOMY,
            null,
            0L,
            10L,
            10L,
            10,
            1
        );

        RuleCandidate candidate = onlyCandidate(result);
        assertEquals(0L, candidate.proposedValue());
        assertEquals(0L, candidate.allowedMaxValue());
        assertReason(result, "REWARD_ISSUANCE_SHARE_EXCEEDED");
        assertEquals(RuleSeverity.BLOCK, result.overallSeverity());
    }

    @Test
    void testModeCreatesNoCandidateAndRejectsMoneyReward() {
        RuleEngineRequest.QuizDraft quiz = new RuleEngineRequest.QuizDraft(
            TARGET_ID,
            QuizMode.TEST,
            QuizCategory.ECONOMY,
            10,
            20,
            1,
            1L
        );
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.QUIZ_SESSION_DRAFT)
                .otherPeriodicIssuancePerStudent(1_000L)
                .quizDraft(quiz)
                .build()
        );

        assertTrue(result.candidates().isEmpty());
        assertReason(result, "FORBIDDEN_REWARD_CONTEXT");
        assertEquals(RuleSeverity.BLOCK, result.overallSeverity());
    }

    @Test
    void blocksMissingModulePrerequisitesAndLoanSpreadBelowTwoHundredBps() {
        RuleEngineResult tax = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .moduleActivation(module("INCOME_TAX", 300L, null))
                .build()
        );
        RuleEngineResult loan = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeModules(Set.of("DEPOSIT"))
                .moduleActivation(module("LOAN", 499L, 300))
                .build()
        );

        assertReason(tax, "MODULE_PREREQUISITE_MISSING");
        assertReason(loan, "LOAN_DEPOSIT_SPREAD_VIOLATION");
        assertEquals(RuleSeverity.BLOCK, tax.overallSeverity());
        assertEquals(RuleSeverity.BLOCK, loan.overallSeverity());
    }

    @ParameterizedTest
    @CsvSource({
        "400, PASS, false, false",
        "500, WARN, true, false",
        "601, BLOCK, false, true"
    })
    void distinguishesRecommendedAndAllowedDepositRateRanges(
        long confirmedRate,
        RuleSeverity severity,
        boolean recommendedWarning,
        boolean rangeBlock
    ) {
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .moduleActivation(module("DEPOSIT", confirmedRate, null))
                .build()
        );

        assertEquals(severity, result.overallSeverity());
        assertEquals(recommendedWarning, hasReason(result, "OUT_OF_RECOMMENDED_BAND"));
        assertEquals(rangeBlock, hasReason(result, "VALUE_OUT_OF_RANGE"));
    }

    @Test
    void usesAssignedSalaryWeightedAverageAndWarnsForUnassignedStudents() {
        RuleEngineRequest.SalaryPolicy salaryPolicy = new RuleEngineRequest.SalaryPolicy(
            List.of(
                new RuleEngineRequest.SalaryRole(
                    "WORKER",
                    BigDecimal.ONE,
                    800L,
                    2,
                    true
                )
            ),
            null,
            null,
            1_000L
        );
        RuleEngineRequest.ProductDraft product = new RuleEngineRequest.ProductDraft(
            TARGET_ID,
            ProductKind.NORMAL,
            10,
            10,
            BigDecimal.ONE,
            0,
            false,
            null
        );
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.PRODUCT_DRAFT)
                .activeStudentCount(4)
                .activeModules(Set.of("ROLE_SALARY"))
                .salaryPolicy(salaryPolicy)
                .productDraft(product)
                .build()
        );

        assertEquals(900L, result.metrics().referenceSalary());
        assertReason(result, "UNASSIGNED_SALARY_ROLE");
        assertEquals(RuleSeverity.WARN, result.overallSeverity());
    }

    @ParameterizedTest
    @CsvSource({"7, 1", "30, 4", "112, 16"})
    void calculatesPaymentCountFromInclusiveDates(int operationDays, int expectedPayments) {
        LocalDate end = START.plusDays(operationDays - 1L);

        PaymentSchedule schedule = service.calculatePaymentSchedule(
            START,
            end,
            7,
            START.plusDays(6)
        );

        assertEquals(operationDays, schedule.operationDays());
        assertEquals(expectedPayments, schedule.paymentCount());
    }

    @Test
    void firstPaymentOutsideOperationPeriodProducesZeroPayments() {
        PaymentSchedule schedule = service.calculatePaymentSchedule(
            START,
            END,
            7,
            END.plusDays(1)
        );

        assertEquals(0, schedule.paymentCount());
    }

    @Test
    void sameInputProducesExactlyTheSameOrderedOutput() {
        RuleEngineRequest request = base(RuleContextType.MODULE_ACTIVATION)
            .moduleActivation(module("DEPOSIT", 500L, null))
            .build();

        RuleEngineResult first = service.evaluate(request);
        RuleEngineResult second = service.evaluate(request);

        assertEquals(first, second);
        assertNotNull(first.metrics().paymentSchedule());
    }

    private RuleEngineRequest.Builder base(RuleContextType contextType) {
        return RuleEngineRequest.builder(contextType, 5, START, END);
    }

    private RuleEngineRequest.ModuleActivation module(
        String code,
        Long confirmed,
        Integer depositRate
    ) {
        return new RuleEngineRequest.ModuleActivation(
            code,
            TARGET_ID,
            confirmed,
            depositRate
        );
    }

    private RuleEngineResult rewardResult(
        QuizCategory category,
        Long confirmed,
        long nonReward,
        long existingReward,
        long recovery
    ) {
        return rewardResult(category, confirmed, nonReward, existingReward, recovery, 10, 1);
    }

    private RuleEngineResult rewardResult(
        QuizCategory category,
        Long confirmed,
        long nonReward,
        long existingReward,
        long recovery,
        int participants,
        int runs
    ) {
        return rewardResult(
            category,
            confirmed,
            nonReward,
            existingReward,
            recovery,
            participants,
            runs,
            1_000L
        );
    }

    private RuleEngineResult rewardResult(
        QuizCategory category,
        Long confirmed,
        long nonReward,
        long existingReward,
        long recovery,
        int participants,
        int runs,
        long periodicIssuancePerStudent
    ) {
        RuleEngineRequest.QuizDraft quiz = new RuleEngineRequest.QuizDraft(
            TARGET_ID,
            QuizMode.GAME,
            category,
            10,
            participants,
            runs,
            confirmed
        );
        return service.evaluate(
            base(RuleContextType.QUIZ_SESSION_DRAFT)
                .otherPeriodicIssuancePerStudent(periodicIssuancePerStudent)
                .budget(nonReward, existingReward, recovery)
                .quizDraft(quiz)
                .build()
        );
    }

    private static RuleCandidate onlyCandidate(RuleEngineResult result) {
        assertEquals(1, result.candidates().size());
        return result.candidates().getFirst();
    }

    private static void assertCandidate(
        RuleEngineResult result,
        RuleTargetType targetType,
        long proposed,
        long recommendedMin,
        long recommendedMax,
        long allowedMin,
        long allowedMax
    ) {
        RuleCandidate candidate = onlyCandidate(result);
        assertEquals(targetType, candidate.targetType());
        assertEquals(proposed, candidate.proposedValue());
        assertEquals(recommendedMin, candidate.recommendedMinValue());
        assertEquals(recommendedMax, candidate.recommendedMaxValue());
        assertEquals(allowedMin, candidate.allowedMinValue());
        assertEquals(allowedMax, candidate.allowedMaxValue());
    }

    private static void assertReason(RuleEngineResult result, String reasonCode) {
        assertTrue(
            hasReason(result, reasonCode),
            () -> "missing reason " + reasonCode + " in " + result.checks()
        );
    }

    private static boolean hasReason(RuleEngineResult result, String reasonCode) {
        return result.checks().stream()
            .anyMatch(check -> check.reasonCode().equals(reasonCode));
    }
}
