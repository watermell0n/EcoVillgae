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
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.b201.ecovillage.ruleengine.domain.ClassroomEconomicMetrics;
import com.b201.ecovillage.ruleengine.domain.PaymentSchedule;
import com.b201.ecovillage.ruleengine.domain.ProductKind;
import com.b201.ecovillage.ruleengine.domain.QuizCategory;
import com.b201.ecovillage.ruleengine.domain.QuizMode;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleCheck;
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
        assertEquals("rule-engine-v3", result.ruleVersion());
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

        assertCandidate(tax, RuleTargetType.INCOME_TAX_RATE, 500L, 400L, 600L, 200L, 1_200L);
        assertCandidate(deposit, RuleTargetType.DEPOSIT_RATE, 300L, 200L, 400L, 100L, 600L);
        assertCandidate(loan, RuleTargetType.LOAN_RATE, 700L, 600L, 800L, 500L, 1_200L);
        assertTrue(store.candidates().isEmpty());
    }

    /**
     * 학년은 세율의 값을 정하지 않는다. 발행 계획도 상점 물가도 알 수 없는 학급에서는 어느
     * 학년이든 같은 기본 세율에서 시작하고, 학년은 교사가 손으로 정할 수 있는 폭만 넓힌다.
     */
    @ParameterizedTest
    @CsvSource({
        "4, 200, 800",
        "5, 200, 1200",
        "6, 200, 2000"
    })
    void gradeSetsOnlyTheAllowedTaxRangeNotTheRateItself(
        int grade,
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
            500L,
            400L,
            600L,
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

    // --- 예금 기준금리: 운영 기간에 따라 달라지고 학년은 쓰지 않는다 ---

    @ParameterizedTest
    @CsvSource({
        // 운영 일수, 제안값, 추천 하한, 추천 상한
        "1, 500, 400, 600",
        "7, 500, 400, 600",
        "8, 300, 200, 400",
        "30, 300, 200, 400",
        "31, 300, 200, 400",
        "32, 200, 100, 300",
        "90, 200, 100, 300",
        "91, 150, 100, 250",
        "180, 150, 100, 250"
    })
    void depositRateFollowsTheOperationPeriod(
        int operationDays,
        long proposed,
        long recommendedMin,
        long recommendedMax
    ) {
        RuleEngineResult result = depositResult(5, operationDays);

        assertCandidate(
            result,
            RuleTargetType.DEPOSIT_RATE,
            proposed,
            recommendedMin,
            recommendedMax,
            100L,
            600L
        );
        assertEquals(RuleSeverity.PASS, result.overallSeverity());
    }

    @ParameterizedTest
    @CsvSource({"4", "5", "6"})
    void depositRateIgnoresTheGrade(int grade) {
        assertEquals(500L, onlyCandidate(depositResult(grade, 5)).proposedValue());
        assertEquals(300L, onlyCandidate(depositResult(grade, 20)).proposedValue());
        assertEquals(200L, onlyCandidate(depositResult(grade, 60)).proposedValue());
        assertEquals(150L, onlyCandidate(depositResult(grade, 120)).proposedValue());
    }

    @Test
    void depositRateExplainsItsBasisToTheTeacher() {
        RuleEngineResult result = depositResult(5, 31);

        assertReason(result, "DEPOSIT_RATE_BASIS");
        RuleCheck basis = result.checks().stream()
            .filter(check -> check.reasonCode().equals("DEPOSIT_RATE_BASIS"))
            .findFirst()
            .orElseThrow();
        assertEquals(RuleSeverity.PASS, basis.severity());
        assertEquals(
            "운영 기간 31일 기준 3.00%에서 시작했습니다. 그래서 3.00%를 추천했습니다.",
            basis.message()
        );
        assertEquals(List.of("deposit-rate"), basis.subjectActionIds());
    }

    @Test
    void depositRateFallsBackToTheMonthlyBaseWhenThePeriodIsUnknown() {
        RuleEngineResult result = service.evaluate(
            RuleEngineRequest.builder(RuleContextType.MODULE_ACTIVATION, 5, null, null)
                .moduleActivation(module("DEPOSIT", null, null))
                .build()
        );

        assertEquals(300L, onlyCandidate(result).proposedValue());
        // 기간이 없으면 공통 검증이 이미 차단하므로 예금 규칙이 따로 막지는 않는다.
        assertEquals(RuleSeverity.BLOCK, result.overallSeverity());
        assertReason(result, "REQUIRED_VALUE_MISSING");
    }

    /**
     * 대출 허용 구간은 예금 금리에서 파생되므로, 예금이 만들 수 있는 모든 값에서
     * 대출 활성화가 BLOCK되지 않아야 한다.
     */
    @ParameterizedTest
    @CsvSource({"100", "150", "200", "300", "500", "600"})
    void everyDepositRateKeepsLoanActivationUnblocked(int depositRateBps) {
        RuleEngineResult loan = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeModules(Set.of("DEPOSIT"))
                .moduleActivation(module("LOAN", null, depositRateBps))
                .build()
        );

        assertEquals(RuleSeverity.PASS, loan.overallSeverity());
        RuleCandidate candidate = onlyCandidate(loan);
        assertEquals(depositRateBps + 200L, candidate.allowedMinValue());
        assertTrue(
            candidate.proposedValue() >= candidate.allowedMinValue()
                && candidate.proposedValue() <= candidate.allowedMaxValue(),
            () -> "loan proposal out of allowed range for deposit " + depositRateBps
        );
    }

    @ParameterizedTest
    @CsvSource({"1", "7", "8", "31", "32", "90", "91", "180"})
    void depositProposalStaysInsideTheRangeLoanRulesExpect(int operationDays) {
        RuleCandidate candidate = onlyCandidate(depositResult(5, operationDays));

        assertTrue(candidate.proposedValue() >= 100L, "deposit proposal below loan floor");
        assertTrue(candidate.proposedValue() <= 600L, "deposit proposal above loan ceiling");
        assertEquals(0L, candidate.proposedValue() % 50L, "deposit proposal must round to 50 bps");
    }

    private RuleEngineResult depositResult(int grade, int operationDays) {
        return service.evaluate(
            RuleEngineRequest.builder(
                    RuleContextType.MODULE_ACTIVATION,
                    grade,
                    START,
                    START.plusDays(operationDays - 1L)
                )
                .moduleActivation(module("DEPOSIT", null, null))
                .build()
        );
    }

    // --- 투자 조건: 종목별 하루 변동 범위와 1주 시작가를 한 번에 추천한다 ---

    @Test
    void recommendsPerAssetBandsAndPricesFromStudentBalances() {
        RuleEngineResult result = investmentResult("SHORT_TERM", 20, 9_000L, 0L);

        assertEquals(8, result.candidates().size());
        assertTrue(
            result.candidates().stream().allMatch(candidate ->
                candidate.targetType() == RuleTargetType.INVESTMENT_CONDITION
                    && candidate.targetId().equals(TARGET_ID)
            ),
            "투자 후보는 모두 투자 모듈 하나를 가리켜야 한다"
        );
        // 단기 기준 6.50%에 종목 위험 비중(1.00·1.40·0.60·1.20)을 곱한 값이다.
        RuleCandidate green = candidate(result, "investment-asset-band-green");
        assertEquals(650L, green.proposedValue());
        assertEquals(550L, green.recommendedMinValue());
        assertEquals(750L, green.recommendedMaxValue());
        assertEquals(200L, green.allowedMinValue());
        assertEquals(1_500L, green.allowedMaxValue());
        assertEquals(900L, candidate(result, "investment-asset-band-cloud").proposedValue());
        assertEquals(400L, candidate(result, "investment-asset-band-food").proposedValue());
        assertEquals(800L, candidate(result, "investment-asset-band-energy").proposedValue());
        // 대표 잔액 9,000의 30%(2,700)로 한 종목을 3주 살 수 있는 900이 기준가다.
        assertEquals(900L, candidate(result, "investment-asset-price-green").proposedValue());
        assertEquals(1_130L, candidate(result, "investment-asset-price-cloud").proposedValue());
        assertEquals(720L, candidate(result, "investment-asset-price-food").proposedValue());
        assertEquals(1_350L, candidate(result, "investment-asset-price-energy").proposedValue());
        // 1주가 대표 잔액보다 비싸면 아무도 살 수 없다.
        assertEquals(9_000L, candidate(result, "investment-asset-price-green").allowedMaxValue());
        assertEquals(RuleSeverity.PASS, result.overallSeverity());
        assertReason(result, "INVESTMENT_VOLATILITY_BASIS");
        assertReason(result, "INVESTMENT_PRICE_BASIS");
    }

    @ParameterizedTest
    @CsvSource({
        // 운영 일수, 위험 비중 1.00 종목의 제안 범위
        "7, 400",
        "14, 300",
        "30, 200",
        "90, 200"
    })
    void longTermBandFallsAsTheOperationPeriodGrows(int operationDays, long proposed) {
        RuleEngineResult result = service.evaluate(
            RuleEngineRequest.builder(
                    RuleContextType.MODULE_ACTIVATION, 5, START, START.plusDays(operationDays - 1L)
                )
                .activeStudentCount(20)
                .moduleActivation(module("INVESTMENT", null, null))
                .investmentDraft(investmentDraft("LONG_TERM", 9_000L, 0L, null))
                .build()
        );

        RuleCandidate band = candidate(result, "investment-asset-band-green");
        assertEquals(proposed, band.proposedValue());
        assertEquals(50L, band.allowedMinValue());
        assertEquals(600L, band.allowedMaxValue());
    }

    @Test
    void lowersTheBandCeilingForALargeClass() {
        assertEquals(
            1_400L,
            candidate(investmentResult("SHORT_TERM", 30, 9_000L, 0L), "investment-asset-band-green")
                .allowedMaxValue()
        );
    }

    /** 아직 아무도 돈을 받지 않은 학급에서는 발행액을 학생 수로 나눠 대표 잔액을 잡는다. */
    @Test
    void fallsBackToTheIssuancePerStudentWhenNoWalletHasMoneyYet() {
        RuleEngineResult result = investmentResult("SHORT_TERM", 20, 0L, 20_000L);

        assertEquals(100L, candidate(result, "investment-asset-price-green").proposedValue());
    }

    @Test
    void warnsWhenStudentsCannotAffordEvenOneShare() {
        RuleEngineResult result = investmentResult("SHORT_TERM", 20, 80L, 0L);

        assertEquals(RuleSeverity.WARN, result.overallSeverity());
        assertReason(result, "INVESTMENT_BALANCE_TOO_SMALL");
    }

    @Test
    void blocksAConfirmedBandOutsideTheAllowedRange() {
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeStudentCount(20)
                .moduleActivation(module("INVESTMENT", null, null))
                .investmentDraft(investmentDraft("SHORT_TERM", 9_000L, 0L, 5_000L))
                .build()
        );

        assertEquals(RuleSeverity.BLOCK, result.overallSeverity());
        assertReason(result, "VALUE_OUT_OF_RANGE");
    }

    /** 투자 모듈 활성화만으로는 정할 숫자가 없다. 조건 초안이 없으면 조용히 넘어가야 한다. */
    @Test
    void makesNoInvestmentCandidateWhenActivationCarriesNoConditionDraft() {
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .moduleActivation(module("INVESTMENT", null, null))
                .build()
        );

        assertTrue(result.candidates().isEmpty());
        assertEquals(RuleSeverity.PASS, result.overallSeverity());
    }

    @Test
    void asksForTheInvestmentModeWhenTheDraftDoesNotNameOne() {
        RuleEngineResult result = investmentResult("WHENEVER", 20, 9_000L, 0L);

        assertTrue(result.candidates().isEmpty());
        assertEquals(RuleSeverity.BLOCK, result.overallSeverity());
        assertReason(result, "REQUIRED_VALUE_MISSING");
    }

    private RuleEngineResult investmentResult(
        String investmentMode,
        int studentCount,
        long medianBalance,
        long moneyIssuance
    ) {
        return service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeStudentCount(studentCount)
                .moduleActivation(module("INVESTMENT", null, null))
                .investmentDraft(
                    investmentDraft(investmentMode, medianBalance, moneyIssuance, null)
                )
                .build()
        );
    }

    private RuleEngineRequest.InvestmentDraft investmentDraft(
        String investmentMode,
        long medianBalance,
        long moneyIssuance,
        Long confirmedBandBps
    ) {
        return new RuleEngineRequest.InvestmentDraft(
            TARGET_ID,
            investmentMode,
            moneyIssuance,
            medianBalance,
            medianBalance,
            List.of(
                asset("GREEN", "그린모빌리티", "1.00", "1.00", confirmedBandBps),
                asset("CLOUD", "클라우드교육", "1.25", "1.40", null),
                asset("FOOD", "마을푸드", "0.80", "0.60", null),
                asset("ENERGY", "새빛에너지", "1.50", "1.20", null)
            )
        );
    }

    private static RuleEngineRequest.InvestmentAsset asset(
        String symbol,
        String name,
        String priceWeight,
        String riskWeight,
        Long confirmedBandBps
    ) {
        return new RuleEngineRequest.InvestmentAsset(
            symbol, name, new BigDecimal(priceWeight), new BigDecimal(riskWeight),
            null, confirmedBandBps
        );
    }

    private static RuleCandidate candidate(RuleEngineResult result, String actionId) {
        return result.candidates().stream()
            .filter(item -> item.actionId().equals(actionId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing candidate " + actionId));
    }

    // --- 학급 규모와 발행액이 실제로 값을 바꾸는지 ---

    /**
     * 학생이 많고 지갑에 돈이 쌓여 있으면 같은 이율이라도 학급 전체가 받아 갈 이자가 커진다.
     * 이자도 새로 찍는 돈이므로 발행 예산 안에서만 줄 수 있고, 그래서 금리가 내려가야 한다.
     */
    @Test
    void depositRateFallsWhenInterestWouldOutgrowTheIssuanceBudget() {
        RuleEngineResult noBalances = depositWithEconomy(25, 0L);
        RuleEngineResult richClass = depositWithEconomy(25, 8_000L);

        assertEquals(300L, onlyCandidate(noBalances).proposedValue());
        assertEquals(150L, onlyCandidate(richClass).proposedValue());
        // 허용 범위는 그대로다. 이미 확정한 금리가 갑자기 범위를 벗어나 막히면 안 된다.
        assertEquals(600L, onlyCandidate(richClass).allowedMaxValue());
    }

    /**
     * 학생이 많을수록 거래 상대가 많아 같은 돈이 더 자주 돈다(교환방정식 MV=PY). 유통속도가
     * 오르면 통화량이 그대로여도 물가가 오르므로, 큰 학급은 금리를 조금 더 높게 잡아 돈을 묶는다.
     */
    @Test
    void depositRateRisesWithClassSizeThroughMoneyVelocity() {
        long small = onlyCandidate(depositWithEconomy(8, 0L)).proposedValue();
        long large = onlyCandidate(depositWithEconomy(32, 0L)).proposedValue();

        assertEquals(300L, small);
        assertEquals(350L, large);
    }

    /**
     * 반대로 이자 예산 상한은 학급 규모에 흔들리지 않아야 한다. 발행액과 예치액이 함께 학생 수에
     * 비례해 늘어 비율이 그대로이기 때문이며, 현실에서도 인구가 늘어 금리가 오르지는 않는다.
     */
    @Test
    void interestBudgetCapDoesNotDependOnClassSizeAlone() {
        assertEquals(
            onlyCandidate(depositWithEconomy(8, 8_000L)).proposedValue(),
            onlyCandidate(depositWithEconomy(32, 8_000L)).proposedValue()
        );
    }

    /** 되걷히는 돈이 적으면 학급에 돈이 쌓인다. 그때는 금리를 올려 돈을 묶어 둔다. */
    @Test
    void depositRateRisesWhenLittleMoneyIsRecovered() {
        RuleEngineResult tight = service.evaluate(
            economyBase(20)
                .moduleActivation(module("DEPOSIT", null, null))
                .metrics(metrics(builder -> builder.realizedIssuance(100_000L).realizedRecovery(10_000L)))
                .build()
        );

        // 기간 기준 3.00%에 회수 부족에 따른 1.25배를 곱해 3.75%가 되고, 50bps 단위로 4.00%가 된다.
        assertEquals(400L, onlyCandidate(tight).proposedValue());
        assertReason(tight, "DEPOSIT_RATE_BASIS");
    }

    /** 적금은 나눠 넣어 평균 예치 기간이 짧다. 납입이 잦을수록 표면 금리가 예금 대비 높아진다. */
    @ParameterizedTest
    @CsvSource({
        // 운영 일수, 납입 횟수, 추천 금리
        "30, 4, 500",
        "90, 12, 350"
    })
    void savingsRateComesFromTheAverageHoldingPeriodMultiplier(
        int operationDays,
        int installments,
        long proposed
    ) {
        RuleEngineResult result = service.evaluate(
            RuleEngineRequest.builder(
                    RuleContextType.MODULE_ACTIVATION,
                    5,
                    START,
                    START.plusDays(operationDays - 1L)
                )
                .activeStudentCount(20)
                .moduleActivation(module("SAVINGS", null, null))
                .build()
        );

        RuleCandidate candidate = onlyCandidate(result);
        assertEquals(proposed, candidate.proposedValue());
        assertEquals(100L, candidate.allowedMinValue());
        assertEquals(800L, candidate.allowedMaxValue());
        assertTrue(
            result.checks().stream().anyMatch(check ->
                check.reasonCode().equals("SAVINGS_RATE_BASIS")
                    && check.message().contains(installments + "번 납입")
            ),
            () -> "적금 근거에 납입 횟수가 없다: " + result.checks()
        );
    }

    /** 연체가 쌓인 학급에서는 떼일 위험이 커지므로 대출 금리가 올라간다. */
    @ParameterizedTest
    @CsvSource({
        // 연체율(bps), 추천 대출 금리. 위험 몫은 연체율의 2배이며 1~4%p에서 끊는다.
        "0, 550",
        "125, 700",
        "1000, 850",
        "3000, 850"
    })
    void loanRateAddsACreditRiskPremiumFromTheMissedPaymentRate(
        int paymentMissBps,
        long proposed
    ) {
        RuleEngineResult result = service.evaluate(
            base(RuleContextType.MODULE_ACTIVATION)
                .activeModules(Set.of("DEPOSIT"))
                .moduleActivation(module("LOAN", null, 300))
                .metrics(metrics(builder -> builder.paymentMissBps(paymentMissBps)))
                .build()
        );

        assertEquals(proposed, onlyCandidate(result).proposedValue());
        assertReason(result, "LOAN_RATE_BASIS");
    }

    /**
     * 세율은 "발행한 돈을 무엇으로 되걷을 것인가"에서 나온다. 상점이 열려 있으면 소비가 상당
     * 부분을 걷어 주므로 세율이 낮아지고, 세금뿐이면 높아진다.
     */
    @Test
    void taxRateComesFromTheFiscalBalanceAndFallsWhenAStoreShares() {
        RuleEngineResult withStore = service.evaluate(
            taxEconomy(Set.of("ROLE_SALARY", "STORE"), 5)
        );
        RuleEngineResult withoutStore = service.evaluate(
            taxEconomy(Set.of("ROLE_SALARY"), 5)
        );

        assertEquals(1_000L, onlyCandidate(withStore).proposedValue());
        assertFalse(hasReason(withStore, "TAX_IS_ONLY_RECOVERY_CHANNEL"));
        assertEquals(1_200L, onlyCandidate(withoutStore).proposedValue());
        assertReason(withoutStore, "TAX_IS_ONLY_RECOVERY_CHANNEL");
    }

    /** 같은 학급 재정이라도 학년이 다르면 허용 범위가 달라 최종 세율이 잘린다. */
    @ParameterizedTest
    @CsvSource({"4, 800", "5, 1000", "6, 1000"})
    void taxRateIsClampedByTheGradeSafetyBand(int grade, long proposed) {
        RuleEngineResult result = service.evaluate(
            taxEconomy(Set.of("ROLE_SALARY", "STORE"), grade)
        );

        assertEquals(proposed, onlyCandidate(result).proposedValue());
    }

    /** 세금을 떼고도 평균 상품 2개는 살 수 있어야 한다. 물가가 높으면 세율이 그만큼 눌린다. */
    @Test
    void taxRateIsCappedSoTakeHomePayStillBuysTheBasicBasket() {
        RuleEngineResult result = service.evaluate(
            economyBase(25)
                .activeModules(Set.of("ROLE_SALARY", "STORE"))
                .moduleActivation(module("INCOME_TAX", null, null))
                .salaryPolicy(salaryPolicy(1_000L, 25))
                .metrics(metrics(builder -> builder.storeAveragePrice(480L)))
                .build()
        );

        // 급여 1,000에서 평균 상품 2개(960)를 남겨야 하므로 세율은 4% 위로 갈 수 없다.
        assertEquals(400L, onlyCandidate(result).proposedValue());
    }

    /** 급여는 "얼마짜리 숫자"가 아니라 "무엇을 살 수 있는 돈"이어야 한다. */
    @Test
    void salaryFollowsTheStorePriceLevel() {
        RuleEngineResult result = service.evaluate(
            economyBase(25)
                .moduleActivation(module("ROLE_SALARY", null, null))
                .salaryPolicy(salaryPolicy(null, 25))
                .metrics(metrics(builder -> builder.storeAveragePrice(250L)))
                .build()
        );

        RuleCandidate candidate = onlyCandidate(result);
        // 평균 250원짜리 상품을 3개 살 수 있는 750원이 기준 급여다.
        assertEquals(750L, candidate.proposedValue());
        assertEquals(1_050L, candidate.allowedMaxValue());
        assertTrue(
            result.checks().stream().anyMatch(check ->
                check.reasonCode().equals("SALARY_BASIS") && check.message().contains("250")
            ),
            () -> "급여 근거에 상점 평균 가격이 없다: " + result.checks()
        );
    }

    /** 상점이 비어 있으면 물가를 알 수 없으므로 예전 기본 급여를 그대로 쓰고 그 사실을 알린다. */
    @Test
    void salaryFallsBackToTheDefaultWhenTheStoreIsEmpty() {
        RuleEngineResult result = service.evaluate(
            economyBase(25)
                .moduleActivation(module("ROLE_SALARY", null, null))
                .salaryPolicy(salaryPolicy(null, 25))
                .build()
        );

        assertEquals(1_000L, onlyCandidate(result).proposedValue());
        assertTrue(
            result.checks().stream().anyMatch(check ->
                check.reasonCode().equals("SALARY_BASIS") && check.message().contains("상점에 게시된 상품이 없어")
            ),
            () -> "상점이 비었다는 안내가 없다: " + result.checks()
        );
    }

    /** 나갈 급여가 상점이 받아낼 수 있는 양보다 훨씬 많으면 물가가 오른다고 알려야 한다. */
    @Test
    void warnsWhenPlannedSalaryOutgrowsTheStoreInventory() {
        RuleEngineResult result = service.evaluate(
            economyBase(25)
                .moduleActivation(module("ROLE_SALARY", null, null))
                .salaryPolicy(salaryPolicy(1_000L, 25))
                .metrics(metrics(builder -> builder.storeAveragePrice(250L).storeInventoryValue(5_000L)))
                .build()
        );

        assertReason(result, "SALARY_ISSUANCE_EXCEEDS_STORE_CAPACITY");
        assertEquals(RuleSeverity.WARN, result.overallSeverity());
    }

    private RuleEngineRequest.Builder economyBase(int studentCount) {
        return RuleEngineRequest.builder(
                RuleContextType.MODULE_ACTIVATION,
                5,
                START,
                START.plusDays(29)
            )
            .activeStudentCount(studentCount)
            .activeModules(Set.of("ROLE_SALARY"));
    }

    private RuleEngineRequest taxEconomy(Set<String> activeModules, int grade) {
        return RuleEngineRequest.builder(
                RuleContextType.MODULE_ACTIVATION,
                grade,
                START,
                START.plusDays(29)
            )
            .activeStudentCount(25)
            .activeModules(activeModules)
            .moduleActivation(module("INCOME_TAX", null, null))
            .salaryPolicy(salaryPolicy(1_000L, 25))
            .metrics(metrics(builder -> builder.storeAveragePrice(300L)))
            .build();
    }

    private RuleEngineResult depositWithEconomy(int studentCount, long medianBalance) {
        return service.evaluate(
            economyBase(studentCount)
                .moduleActivation(module("DEPOSIT", null, null))
                .salaryPolicy(salaryPolicy(1_000L, studentCount))
                .metrics(metrics(builder -> builder.medianStudentBalance(medianBalance)))
                .build()
        );
    }

    private static RuleEngineRequest.SalaryPolicy salaryPolicy(
        Long confirmedSalary,
        int assignedStudentCount
    ) {
        return new RuleEngineRequest.SalaryPolicy(
            List.of(new RuleEngineRequest.SalaryRole(
                "DEFAULT_ROLE",
                BigDecimal.ONE,
                confirmedSalary,
                assignedStudentCount,
                true
            )),
            7,
            START.plusDays(6),
            1_000L
        );
    }

    private static ClassroomEconomicMetrics metrics(UnaryOperator<MetricsBuilder> customizer) {
        return customizer.apply(new MetricsBuilder()).build();
    }

    /** 지표는 항목이 많아 테스트마다 필요한 값만 바꿔 쓸 수 있게 감싼다. */
    private static final class MetricsBuilder {
        private long realizedIssuance;
        private long realizedRecovery;
        private long medianStudentBalance;
        private long storeAveragePrice;
        private long storeInventoryValue;
        private Integer paymentMissBps;

        private MetricsBuilder realizedIssuance(long value) {
            realizedIssuance = value;
            return this;
        }

        private MetricsBuilder realizedRecovery(long value) {
            realizedRecovery = value;
            return this;
        }

        private MetricsBuilder medianStudentBalance(long value) {
            medianStudentBalance = value;
            return this;
        }

        private MetricsBuilder storeAveragePrice(long value) {
            storeAveragePrice = value;
            return this;
        }

        private MetricsBuilder storeInventoryValue(long value) {
            storeInventoryValue = value;
            return this;
        }

        private MetricsBuilder paymentMissBps(Integer value) {
            paymentMissBps = value;
            return this;
        }

        private ClassroomEconomicMetrics build() {
            return new ClassroomEconomicMetrics(
                realizedIssuance,
                realizedIssuance,
                realizedRecovery,
                0L,
                0L,
                medianStudentBalance,
                storeAveragePrice,
                storeInventoryValue,
                paymentMissBps,
                null,
                0
            );
        }
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
