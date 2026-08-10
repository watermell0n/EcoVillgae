package com.b201.ecovillage.ruleengine.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.b201.ecovillage.investment.domain.InvestmentAssetCatalog;
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
import com.b201.ecovillage.ruleengine.domain.RuleEvaluationMetrics;
import com.b201.ecovillage.ruleengine.domain.RuleSeverity;
import com.b201.ecovillage.ruleengine.domain.RuleTargetType;
import com.b201.ecovillage.ruleengine.domain.RuleValueUnit;

@Service
public class RuleEngineService {

    public static final String RULE_VERSION = "rule-engine-v3";

    private static final long BASE_SALARY = 1_000L;
    private static final long MIN_SALARY = 100L;
    // 대출 금리 규칙(evaluateLoan)이 예금 금리를 이 범위로 전제하므로 함께 바꿔야 한다.
    private static final long DEPOSIT_ALLOWED_MIN_BPS = 100L;
    private static final long DEPOSIT_ALLOWED_MAX_BPS = 600L;
    private static final long SAVINGS_ALLOWED_MIN_BPS = 100L;
    private static final long SAVINGS_ALLOWED_MAX_BPS = 800L;
    // 이 인원을 넘으면 같은 이율에도 학급 전체 순발행이 커지므로 상한을 한 단계 낮춘다.
    private static final int LARGE_CLASS_STUDENT_COUNT = 25;

    // --- 학급 경제 공통 상수 ---
    // 배수는 만분율(10,000 = 1.00배)로 둔다. 정수 연산만으로 반올림 오차 없이 곱할 수 있다.
    private static final long UNIT_BPS = 10_000L;
    /** 발행한 돈 가운데 되걷히기를 바라는 몫이다. 기존 예산 검증(50~120%)의 가운데 값이다. */
    private static final long TARGET_RECOVERY_BPS = 7_000L;
    /** 상점이 열려 있을 때 급여 가운데 소비로 되걷힌다고 보는 몫이다. */
    private static final long STORE_CONSUMPTION_SHARE_BPS = 6_000L;
    /** 예금·적금 이자로 새로 발행해도 되는 몫이다. 이자가 발행을 밀어 올리지 않게 막는다. */
    private static final long INTEREST_BUDGET_SHARE_BPS = 500L;
    /** 학생이 가진 돈 가운데 예·적금에 넣는다고 보는 몫이다. 참여율을 알면 그 값을 쓴다. */
    private static final long DEFAULT_SAVINGS_PARTICIPATION_BPS = 4_000L;
    /** 예금 한 번의 만기다. 운영 기간 동안 이자가 몇 번 붙는지 계산하는 데 쓴다. */
    private static final int DEPOSIT_TERM_DAYS = 7;
    /** 적금 납입 주기다. 평균 예치 기간이 예금의 몇 배인지 계산하는 데 쓴다. */
    private static final int SAVINGS_INSTALLMENT_DAYS = 7;
    /** 적금 표면금리를 예금의 몇 배까지 올릴 수 있는지다. 평균 예치 기간 차이의 상한이다. */
    private static final long SAVINGS_TERM_MULTIPLIER_CAP_BPS = 18_000L;
    /** 대출의 기본 업무 마진이다. 우리나라 예대금리차(약 1.4%p)를 기준으로 잡았다. */
    private static final long LOAN_OPERATING_MARGIN_BPS = 150L;
    private static final long LOAN_DEFAULT_CREDIT_RISK_BPS = 250L;
    private static final long LOAN_MIN_CREDIT_RISK_BPS = 100L;
    private static final long LOAN_MAX_CREDIT_RISK_BPS = 400L;
    private static final long LOAN_TIGHT_LIQUIDITY_PREMIUM_BPS = 100L;
    /** 회수 채널을 하나도 모를 때 쓰는 소득세율이다. 재정 균형을 계산할 수 없을 때만 쓴다. */
    private static final long DEFAULT_TAX_BPS = 500L;
    /** 세후 급여로 평균 상품을 이만큼은 살 수 있어야 한다. 세율의 상한을 정한다. */
    private static final long TAX_PROTECTED_BASKET_ITEMS = 2L;
    /** 1회 급여로 평균 상품을 이만큼 살 수 있게 잡는다. 나머지는 저축·투자 여력이다. */
    private static final long SALARY_TARGET_BASKET_ITEMS = 3L;
    /** 발행액이 상점 재고 값어치의 이 배를 넘으면 물가가 오를 수 있다고 알린다. */
    private static final long SALARY_STORE_CAPACITY_MULTIPLE = 2L;
    /** 운영 기간에 급여가 이만큼은 나가야 학생이 벌고 쓰는 흐름을 겪는다. */
    private static final int MIN_HEALTHY_PAYMENTS = 3;
    private static final int MAX_HEALTHY_PAYMENTS = 12;
    /** 추천 구간의 기본 폭이다. 학생이 적은 학급은 한 명의 행동이 지표를 흔들어 더 넓게 준다. */
    private static final long RATE_BAND_BPS = 100L;
    private static final long SMALL_CLASS_RATE_BAND_BPS = 150L;
    private static final int SMALL_CLASS_STUDENT_COUNT = 10;
    // 단기는 하루 만에 결과가 나므로 그 하루에 변동을 느낄 수 있어야 한다.
    private static final long INVESTMENT_SHORT_VOLATILITY_BPS = 650L;
    private static final long INVESTMENT_SHORT_VOLATILITY_MIN_BPS = 200L;
    private static final long INVESTMENT_SHORT_VOLATILITY_MAX_BPS = 1_500L;
    private static final long INVESTMENT_LONG_VOLATILITY_MIN_BPS = 50L;
    private static final long INVESTMENT_LONG_VOLATILITY_MAX_BPS = 600L;
    // 학생이 가진 돈 가운데 투자에 쓴다고 보는 몫이다. 단기는 하루면 돌려받으므로 더 크게 본다.
    private static final BigDecimal INVESTMENT_SHORT_BUDGET_SHARE = new BigDecimal("0.30");
    private static final BigDecimal INVESTMENT_LONG_BUDGET_SHARE = new BigDecimal("0.20");
    // 그 몫으로 한 종목을 몇 주 살 수 있게 할지다. 한 주만 살 수 있으면 나눠 담을 수 없다.
    private static final long INVESTMENT_TARGET_SHARES = 3L;
    private static final long INVESTMENT_MIN_MEANINGFUL_BALANCE = 100L;
    private static final BigDecimal REWARD_WARN_SHARE = new BigDecimal("0.25");
    private static final BigDecimal REWARD_MAX_SHARE = new BigDecimal("0.30");
    private static final BigDecimal MIN_RECOVERY_RATIO = new BigDecimal("0.50");
    private static final BigDecimal MAX_RECOVERY_RATIO = new BigDecimal("1.20");

    public RuleEngineResult evaluate(RuleEngineRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Evaluation evaluation = new Evaluation();
        validateCommon(request, evaluation);

        PaymentSchedule schedule = calculateSchedule(request, evaluation);
        // 금리·세율·급여가 모두 "얼마나 발행되고 얼마나 걷히는가"에서 나오므로 문맥과 상관없이
        // 먼저 계산한다. 이 계산이 남기는 경고는 문맥별 규칙이 따로 남기므로 여기서는 버린다.
        EconomicContext economy = economicContext(request, schedule);
        ReferenceSalary reference = ReferenceSalary.notCalculated();
        long candidateRewardIssuance = 0L;

        if (request.contextType() != null) {
            switch (request.contextType()) {
                case CLASSROOM_SETUP -> evaluateClassroomSetup(request, evaluation);
                case MODULE_ACTIVATION ->
                    evaluateModuleActivation(request, economy, schedule, evaluation);
                case PRODUCT_DRAFT -> {
                    reference = calculateReferenceSalary(request, evaluation);
                    evaluateProduct(request, reference.net(), evaluation);
                }
                case QUIZ_SESSION_DRAFT -> {
                    reference = calculateReferenceSalary(request, evaluation);
                    candidateRewardIssuance = evaluateQuiz(
                        request,
                        reference.gross(),
                        evaluation
                    );
                }
            }
        }

        BudgetMetrics budget = evaluateBudget(
            request,
            candidateRewardIssuance,
            evaluation
        );
        addPositiveChecks(evaluation);

        RuleSeverity overallSeverity = evaluation.checks.stream()
            .map(RuleCheck::severity)
            .max(Comparator.comparingInt(RuleSeverity::priority))
            .orElse(RuleSeverity.PASS);

        return new RuleEngineResult(
            RULE_VERSION,
            request.contextType(),
            overallSeverity,
            evaluation.candidates,
            evaluation.checks,
            new RuleEvaluationMetrics(
                reference.gross(),
                reference.net(),
                schedule,
                request.nonRewardIssuance(),
                request.existingRewardIssuance(),
                candidateRewardIssuance,
                budget.rewardShare(),
                request.expectedRecovery(),
                budget.recoveryRatio()
            )
        );
    }

    public PaymentSchedule calculatePaymentSchedule(
        LocalDate operationStartsOn,
        LocalDate operationEndsOn,
        Integer payIntervalDays,
        LocalDate firstPayOn
    ) {
        if (operationStartsOn == null || operationEndsOn == null) {
            throw new IllegalArgumentException("operation period is required");
        }
        if (operationEndsOn.isBefore(operationStartsOn)) {
            throw new IllegalArgumentException("operation end must not precede start");
        }
        int operationDays = Math.toIntExact(
            ChronoUnit.DAYS.between(operationStartsOn, operationEndsOn) + 1L
        );
        int interval = payIntervalDays == null
            ? Math.min(7, operationDays)
            : payIntervalDays;
        if (interval < 1) {
            throw new IllegalArgumentException("pay interval must be at least one day");
        }
        LocalDate first = firstPayOn == null
            ? operationStartsOn.plusDays(interval - 1L)
            : firstPayOn;
        int count = 0;
        if (!first.isBefore(operationStartsOn) && !first.isAfter(operationEndsOn)) {
            count = Math.toIntExact(
                ChronoUnit.DAYS.between(first, operationEndsOn) / interval + 1L
            );
        }
        return new PaymentSchedule(operationDays, interval, first, count);
    }

    private void validateCommon(RuleEngineRequest request, Evaluation evaluation) {
        if (request.contextType() == null) {
            evaluation.missing(
                "context-type-required",
                "실행 문맥이 필요합니다.",
                List.of()
            );
        }
        if (request.grade() < 4 || request.grade() > 6) {
            evaluation.rangeBlock(
                "grade-out-of-range",
                "학년은 4~6 범위여야 합니다.",
                List.of()
            );
        }
        if (request.operationStartsOn() == null || request.operationEndsOn() == null) {
            evaluation.missing(
                "operation-period-required",
                "실제 운영 시작일과 종료일이 필요합니다.",
                List.of()
            );
        } else if (request.operationEndsOn().isBefore(request.operationStartsOn())) {
            evaluation.rangeBlock(
                "operation-period-invalid",
                "운영 종료일은 시작일보다 빠를 수 없습니다.",
                List.of()
            );
        }
        if (request.activeStudentCount() < 0) {
            evaluation.rangeBlock(
                "active-student-count-invalid",
                "활성 학생 수는 음수일 수 없습니다.",
                List.of()
            );
        }
        if (request.currencyName() == null || request.currencyName().isBlank()) {
            evaluation.addCheck(
                "currency-name-missing",
                RuleSeverity.BLOCK,
                "CURRENCY_NAME_MISSING",
                List.of(),
                "학급 통화명이 필요합니다."
            );
            evaluation.requiredMissing = true;
        }
        if (request.systemAmountCap() < MIN_SALARY) {
            evaluation.rangeBlock(
                "system-amount-cap-invalid",
                "시스템 금액 상한은 급여 허용 하한 이상이어야 합니다.",
                List.of()
            );
        }
        if (
            request.nonRewardIssuance() < 0
                || request.existingRewardIssuance() < 0
                || request.expectedRecovery() < 0
        ) {
            evaluation.rangeBlock(
                "budget-value-negative",
                "발행액과 회수액은 음수일 수 없습니다.",
                List.of()
            );
        }
    }

    private PaymentSchedule calculateSchedule(
        RuleEngineRequest request,
        Evaluation evaluation
    ) {
        if (
            request.operationStartsOn() == null
                || request.operationEndsOn() == null
                || request.operationEndsOn().isBefore(request.operationStartsOn())
        ) {
            return null;
        }
        RuleEngineRequest.SalaryPolicy salaryPolicy = request.salaryPolicy();
        Integer interval = salaryPolicy == null ? null : salaryPolicy.payIntervalDays();
        LocalDate firstPayOn = salaryPolicy == null ? null : salaryPolicy.firstPayOn();
        try {
            return calculatePaymentSchedule(
                request.operationStartsOn(),
                request.operationEndsOn(),
                interval,
                firstPayOn
            );
        } catch (IllegalArgumentException exception) {
            evaluation.rangeBlock(
                "pay-schedule-invalid",
                exception.getMessage(),
                List.of()
            );
            return calculatePaymentSchedule(
                request.operationStartsOn(),
                request.operationEndsOn(),
                null,
                null
            );
        }
    }

    private void evaluateClassroomSetup(
        RuleEngineRequest request,
        Evaluation evaluation
    ) {
        if (request.activeModules().isEmpty()) {
            evaluation.addCheck(
                "no-economic-modules",
                RuleSeverity.PASS,
                "NO_ECONOMIC_MODULES_SELECTED",
                List.of(),
                "선택한 경제 모듈이 없어 공통 설정만 확인했습니다."
            );
        }
    }

    private void evaluateModuleActivation(
        RuleEngineRequest request,
        EconomicContext economy,
        PaymentSchedule schedule,
        Evaluation evaluation
    ) {
        RuleEngineRequest.ModuleActivation activation = request.moduleActivation();
        if (activation == null) {
            evaluation.missing(
                "module-activation-required",
                "활성화할 모듈 정보가 필요합니다.",
                List.of()
            );
            return;
        }
        if (activation.moduleCode() == null || activation.moduleCode().isBlank()) {
            evaluation.missing(
                "module-code-required",
                "활성화할 모듈 코드가 필요합니다.",
                List.of()
            );
            return;
        }
        if (activation.targetId() == null) {
            evaluation.missing(
                "module-id-required",
                "활성화할 모듈 ID가 필요합니다.",
                List.of()
            );
            return;
        }

        switch (activation.moduleCode()) {
            case "ROLE_SALARY", "SALARY" ->
                evaluateSalary(request, economy, activation, evaluation);
            case "INCOME_TAX" -> evaluateIncomeTax(request, economy, activation, evaluation);
            case "DEPOSIT" -> evaluateDeposit(request, economy, schedule, activation, evaluation);
            case "SAVINGS" -> evaluateSavings(request, economy, schedule, activation, evaluation);
            case "LOAN" -> evaluateLoan(request, economy, activation, evaluation);
            case "INVESTMENT" -> evaluateInvestment(request, schedule, activation, evaluation);
            default -> {
                // Modules without an initial numeric policy intentionally return no candidate.
            }
        }
    }

    /**
     * 역할별 1회 급여를 추천한다.
     *
     * <p>급여는 "얼마짜리 숫자인가"가 아니라 "그 돈으로 무엇을 살 수 있는가"로 정해야 뜻이 있다.
     * 그래서 상점에 게시된 상품의 평균 가격을 기준으로 잡고, 1회 급여로 평균 상품을
     * {@value #SALARY_TARGET_BASKET_ITEMS}개쯤 살 수 있게 한다. 나머지는 저축·투자로 돌릴 몫이다.
     * 상점이 아직 비어 있으면 물가를 알 수 없으므로 기본 급여를 쓰고 그 사실을 알린다.
     *
     * <p>역할 사이의 차이는 교사가 정한 가중치로만 낸다. 학년은 금액을 바꾸지 않고, 가중치의
     * 허용 폭과 역할 간 격차 경고 기준만 정한다.
     */
    private void evaluateSalary(
        RuleEngineRequest request,
        EconomicContext economy,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        RuleEngineRequest.SalaryPolicy policy = request.salaryPolicy();
        if (policy == null || policy.roles().isEmpty()) {
            evaluation.missing(
                "salary-roles-required",
                "역할·급여 모듈에는 하나 이상의 역할 구성이 필요합니다.",
                List.of()
            );
            return;
        }

        long baseSalary = baseSalary(request);
        BigDecimal minWeight = minRoleWeight(request.grade());
        BigDecimal maxWeight = maxRoleWeight(request.grade());
        long allowedMax = Math.min(
            request.systemAmountCap(),
            roundAmount(BigDecimal.valueOf(baseSalary).multiply(maxWeight))
        );
        List<Long> salaries = new ArrayList<>();

        for (int index = 0; index < policy.roles().size(); index++) {
            RuleEngineRequest.SalaryRole role = policy.roles().get(index);
            String roleCode = role.roleCode();
            if (roleCode == null || roleCode.isBlank() || role.weight() == null) {
                evaluation.missing(
                    "salary-role-value-required-" + index,
                    "각 역할에는 역할 코드와 가중치가 필요합니다.",
                    List.of()
                );
                continue;
            }
            if (
                role.weight().compareTo(minWeight) < 0
                    || role.weight().compareTo(maxWeight) > 0
                    || role.assignedStudentCount() < 0
            ) {
                evaluation.rangeBlock(
                    "salary-role-input-out-of-range-" + index,
                    "역할 가중치 또는 배정 학생 수가 학년별 허용 범위를 벗어났습니다.",
                    List.of()
                );
                continue;
            }

            long proposed = Math.max(
                MIN_SALARY,
                roundAmount(BigDecimal.valueOf(baseSalary).multiply(role.weight()))
            );
            proposed = Math.min(proposed, allowedMax);
            long recommendedMin = Math.max(
                MIN_SALARY,
                roundAmount(
                    BigDecimal.valueOf(proposed)
                        .multiply(BigDecimal.ONE.subtract(gradeBand(request.grade())))
                )
            );
            long recommendedMax = Math.min(
                allowedMax,
                roundAmount(
                    BigDecimal.valueOf(proposed)
                        .multiply(BigDecimal.ONE.add(gradeBand(request.grade())))
                )
            );
            String actionId = "role-salary-"
                + normalizeActionPart(roleCode)
                + "-"
                + (index + 1);
            RuleCandidate candidate = new RuleCandidate(
                actionId,
                RuleTargetType.ROLE_SALARY,
                activation.targetId(),
                proposed,
                recommendedMin,
                recommendedMax,
                MIN_SALARY,
                allowedMax,
                RuleValueUnit.AMOUNT,
                List.of("SALARY_ROLE_WEIGHT", "SALARY_STORE_PRICE_LEVEL", "SALARY_GRADE_BAND"),
                roleCode + " 1회 급여"
            );
            evaluation.addCandidate(candidate, role.confirmedSalary());
            salaries.add(role.confirmedSalary() == null ? proposed : role.confirmedSalary());
        }

        List<Long> positiveSalaries = salaries.stream().filter(value -> value > 0).toList();
        if (positiveSalaries.size() > 1) {
            long min = positiveSalaries.stream().mapToLong(Long::longValue).min().orElseThrow();
            long max = positiveSalaries.stream().mapToLong(Long::longValue).max().orElseThrow();
            BigDecimal gap = BigDecimal.valueOf(max)
                .divide(BigDecimal.valueOf(min), 4, RoundingMode.HALF_UP);
            if (gap.compareTo(maxSalaryGap(request.grade())) > 0) {
                evaluation.addCheck(
                    "role-salary-gap",
                    RuleSeverity.WARN,
                    "ROLE_SALARY_GAP_EXCESSIVE",
                    evaluation.actionIds(RuleTargetType.ROLE_SALARY),
                    "최고·최저 급여 격차가 학년별 권장 상한을 초과합니다."
                );
            }
        }
        explainSalaryBasis(request, economy, baseSalary, evaluation);
    }

    /** 교사가 "왜 이 금액인가"와 "이 금액이 학급 전체에 어떤 뜻인가"를 함께 볼 수 있게 한다. */
    private void explainSalaryBasis(
        RuleEngineRequest request,
        EconomicContext economy,
        long baseSalary,
        Evaluation evaluation
    ) {
        List<String> actionIds = evaluation.actionIds(RuleTargetType.ROLE_SALARY);
        long storePrice = request.metrics().storeAveragePrice();
        evaluation.addCheck(
            "salary-basis",
            RuleSeverity.PASS,
            "SALARY_BASIS",
            actionIds,
            storePrice > 0
                ? "상점 평균 가격 " + storePrice + "을 기준으로, 1회 급여로 평균 상품을 "
                    + SALARY_TARGET_BASKET_ITEMS + "개쯤 살 수 있는 " + baseSalary
                    + "을 기준 급여로 잡았습니다."
                : "상점에 게시된 상품이 없어 물가를 알 수 없으므로 기본 급여 " + baseSalary
                    + "을 기준으로 잡았습니다. 상점을 연 뒤 다시 추천받으면 물가에 맞춰집니다."
        );
        long inventory = request.metrics().storeInventoryValue();
        if (
            inventory > 0
                && economy.plannedSalaryIssuance()
                    > multiplySaturated(inventory, SALARY_STORE_CAPACITY_MULTIPLE)
        ) {
            evaluation.addCheck(
                "salary-store-capacity",
                RuleSeverity.WARN,
                "SALARY_ISSUANCE_EXCEEDS_STORE_CAPACITY",
                actionIds,
                "운영 기간 동안 나갈 급여 " + economy.plannedSalaryIssuance()
                    + "이 상점 재고 값어치 " + inventory
                    + "보다 훨씬 많습니다. 살 것이 부족해 물가가 오를 수 있으니 "
                    + "상품이나 재고를 늘리는 편이 좋습니다."
            );
        }
        int payments = economy.paymentCount();
        if (payments > 0 && (payments < MIN_HEALTHY_PAYMENTS || payments > MAX_HEALTHY_PAYMENTS)) {
            evaluation.addCheck(
                "salary-payment-count",
                RuleSeverity.WARN,
                "SALARY_PAYMENT_COUNT_UNBALANCED",
                actionIds,
                "운영 기간 동안 급여가 " + payments + "번 나갑니다. "
                    + MIN_HEALTHY_PAYMENTS + "~" + MAX_HEALTHY_PAYMENTS
                    + "번이 되도록 지급 주기를 조정하면 학생이 벌고 쓰는 흐름을 더 잘 겪습니다."
            );
        }
    }

    /**
     * 기준 급여다. 상점 물가를 알면 거기에 맞추고, 모르면 기존 기본값을 그대로 쓴다.
     * 금액 단위는 학년에 맞춰 끊어 학생이 읽기 쉽게 한다.
     */
    private static long baseSalary(RuleEngineRequest request) {
        long storePrice = request.metrics().storeAveragePrice();
        if (storePrice <= 0L) {
            return BASE_SALARY;
        }
        long target = multiplySaturated(storePrice, SALARY_TARGET_BASKET_ITEMS);
        return Math.max(MIN_SALARY, roundToUnit(target, amountUnit(request.grade())));
    }

    /**
     * 소득세율을 학급 재정에서 역산한다.
     *
     * <p>현실의 세율도 "얼마를 걷을지"가 아니라 "정부가 쓴 만큼을 어떻게 메울지"에서 나온다.
     * 우리 학급에서 정부 지출에 해당하는 것은 급여·보상으로 새로 발행한 돈이고, 세금은 그것을
     * 되걷는 여러 채널 가운데 하나다. 그래서 발행액에 목표 회수율을 곱해 되걷어야 할 총액을
     * 구하고, 상점 소비처럼 이미 걷히는 몫을 뺀 나머지를 급여 총액으로 나눈다.
     *
     * <p>여기에 구매력 상한을 함께 건다. 세금을 떼고 남은 급여로도 평균 상품을
     * {@value #TAX_PROTECTED_BASKET_ITEMS}개는 살 수 있어야 학생이 일할 마음이 든다.
     *
     * <p>세율은 학급 규모에 비례하지 않는다. 발행액과 회수액이 함께 학생 수에 비례해 늘어 비율이
     * 그대로이기 때문이며, 이는 현실에서도 인구가 늘어도 세율이 오르지 않는 것과 같다. 학년은
     * 값을 정하지 않고 허용 범위(안전 폭)만 정한다.
     */
    private void evaluateIncomeTax(
        RuleEngineRequest request,
        EconomicContext economy,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        if (!hasSalaryModule(request.activeModules())) {
            evaluation.addCheck(
                "income-tax-prerequisite",
                RuleSeverity.BLOCK,
                "MODULE_PREREQUISITE_MISSING",
                List.of(),
                "소득세 모듈을 활성화하려면 역할·급여 모듈이 활성 상태여야 합니다."
            );
        }
        RateBand band = taxBand(request.grade());
        TaxBasis basis = taxBasis(request, economy);
        long proposed = clamp(
            roundRate50(basis.proposedBps()),
            band.allowedMin(),
            band.allowedMax()
        );
        long recommendedBand = rateBand(request);
        RuleCandidate candidate = new RuleCandidate(
            "income-tax-rate",
            RuleTargetType.INCOME_TAX_RATE,
            activation.targetId(),
            proposed,
            Math.max(band.allowedMin(), proposed - recommendedBand),
            Math.min(band.allowedMax(), proposed + recommendedBand),
            band.allowedMin(),
            band.allowedMax(),
            RuleValueUnit.BPS,
            List.of("TAX_FISCAL_BALANCE", "TAX_PURCHASING_POWER", "TAX_GRADE_BAND"),
            "소득세율"
        );
        evaluation.addCandidate(candidate, activation.confirmedValue());
        evaluation.addCheck(
            "income-tax-basis",
            RuleSeverity.PASS,
            "INCOME_TAX_BASIS",
            List.of(candidate.actionId()),
            basis.message() + " 최종 추천은 " + percentText(proposed) + "입니다."
        );
        if (basis.onlyRecoveryChannel()) {
            evaluation.addCheck(
                "income-tax-sole-recovery",
                RuleSeverity.WARN,
                "TAX_IS_ONLY_RECOVERY_CHANNEL",
                List.of(candidate.actionId()),
                "돈을 되걷는 채널이 세금뿐이라 세율이 높게 계산됩니다. "
                    + "상점을 열면 소비로도 회수되어 세율을 낮출 수 있습니다."
            );
        }
    }

    /**
     * 재정 균형에서 나온 세율과 구매력 상한 가운데 낮은 쪽을 고른다. 둘 다 계산할 수 없으면
     * 기본값을 쓰고 그 사실을 알린다.
     */
    private TaxBasis taxBasis(RuleEngineRequest request, EconomicContext economy) {
        Long fiscalBps = null;
        boolean onlyRecoveryChannel = false;
        long salaryIssuance = economy.salaryIssuance();
        long totalIssuance = economy.totalIssuance();
        if (salaryIssuance > 0L && totalIssuance > 0L) {
            long recoveryTarget = share(totalIssuance, TARGET_RECOVERY_BPS);
            long knownRecovery = economy.recoveryAmount();
            long estimatedConsumption = request.activeModules().contains("STORE")
                ? share(salaryIssuance, STORE_CONSUMPTION_SHARE_BPS)
                : 0L;
            long nonTaxRecovery = Math.max(knownRecovery, estimatedConsumption);
            onlyRecoveryChannel = nonTaxRecovery == 0L;
            long taxTarget = Math.max(0L, recoveryTarget - nonTaxRecovery);
            fiscalBps = bps(taxTarget, salaryIssuance);
        }

        Long purchasingPowerBps = null;
        long storePrice = request.metrics().storeAveragePrice();
        long gross = economy.perStudentSalary();
        if (storePrice > 0L && gross > 0L) {
            long protectedNet = multiplySaturated(storePrice, TAX_PROTECTED_BASKET_ITEMS);
            purchasingPowerBps = Math.max(0L, bps(Math.max(0L, gross - protectedNet), gross));
        }

        if (fiscalBps == null && purchasingPowerBps == null) {
            return new TaxBasis(
                DEFAULT_TAX_BPS,
                false,
                "급여 계획과 상점 물가를 아직 알 수 없어 기본 세율 "
                    + percentText(DEFAULT_TAX_BPS) + "로 시작합니다."
            );
        }
        if (purchasingPowerBps == null) {
            return new TaxBasis(
                fiscalBps,
                onlyRecoveryChannel,
                "발행 예정액 " + totalIssuance + " 가운데 "
                    + percentText(TARGET_RECOVERY_BPS) + "를 되걷는 것을 목표로 하면 "
                    + "급여에 매길 세율은 " + percentText(fiscalBps) + "입니다."
            );
        }
        if (fiscalBps == null) {
            return new TaxBasis(
                purchasingPowerBps,
                false,
                "세금을 떼고도 평균 상품 " + TAX_PROTECTED_BASKET_ITEMS + "개를 살 수 있으려면 "
                    + "세율이 " + percentText(purchasingPowerBps) + " 이하여야 합니다."
            );
        }
        long chosen = Math.min(fiscalBps, purchasingPowerBps);
        return new TaxBasis(
            chosen,
            // 구매력 상한이 이겼더라도 "세금 말고 걷을 데가 없다"는 사실은 그대로다.
            onlyRecoveryChannel,
            "재정 균형으로는 " + percentText(fiscalBps) + ", 세후 급여로 평균 상품 "
                + TAX_PROTECTED_BASKET_ITEMS + "개를 살 수 있는 한도로는 "
                + percentText(purchasingPowerBps) + "가 나와 낮은 쪽을 골랐습니다."
        );
    }

    /**
     * 예금 이자는 만기 1회 단순이자(SIMPLE_FULL_TERM)이므로 기준금리는 운영 기간에 따라 달라진다.
     * 짧은 수업은 예금이 한두 번밖에 만기되지 않아 한 번으로도 이자를 체감해야 하고,
     * 긴 운영은 만기가 반복되며 이자가 순발행으로 누적되므로 낮춰야 한다.
     *
     * 허용 구간은 대출 금리 규칙이 예금 금리를 100~600 bps로 전제하므로 넓히지 않는다.
     * (evaluateLoan이 이 범위를 벗어난 예금 금리를 BLOCK 처리한다.)
     */
    private void evaluateDeposit(
        RuleEngineRequest request,
        EconomicContext economy,
        PaymentSchedule schedule,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        Integer operationDays = schedule == null ? null : schedule.operationDays();
        long base = depositBaseRate(operationDays);
        long adjusted = share(
            share(base, economy.liquidityFactorBps()),
            economy.velocityFactorBps()
        );
        Long budgetCap = interestBudgetCapBps(request, economy, operationDays);
        long ceiling = budgetCap == null
            ? DEPOSIT_ALLOWED_MAX_BPS
            : clamp(floorRate50(budgetCap), DEPOSIT_ALLOWED_MIN_BPS, DEPOSIT_ALLOWED_MAX_BPS);
        long proposed = clamp(roundRate50(adjusted), DEPOSIT_ALLOWED_MIN_BPS, ceiling);
        long band = rateBand(request);
        RuleCandidate candidate = new RuleCandidate(
            "deposit-rate",
            RuleTargetType.DEPOSIT_RATE,
            activation.targetId(),
            proposed,
            Math.max(DEPOSIT_ALLOWED_MIN_BPS, proposed - band),
            Math.min(DEPOSIT_ALLOWED_MAX_BPS, proposed + band),
            // 허용 범위는 교사가 정할 수 있는 폭이라 지표에 따라 좁히지 않는다. 좁히면 이미
            // 확정해 둔 금리가 갑자기 범위를 벗어나 모듈 활성화가 막힐 수 있다.
            DEPOSIT_ALLOWED_MIN_BPS,
            DEPOSIT_ALLOWED_MAX_BPS,
            RuleValueUnit.BPS,
            List.of(
                "DEPOSIT_OPERATION_PERIOD",
                "DEPOSIT_LIQUIDITY_PRESSURE",
                "DEPOSIT_INTEREST_BUDGET"
            ),
            "예금 기준금리"
        );
        evaluation.addCandidate(candidate, activation.confirmedValue());
        // 교사가 "왜 이 값인가"를 화면에서 바로 확인할 수 있도록 근거를 남긴다.
        evaluation.addCheck(
            "deposit-rate-basis",
            RuleSeverity.PASS,
            "DEPOSIT_RATE_BASIS",
            List.of(candidate.actionId()),
            rateBasisMessage(request, economy, operationDays, base, proposed, budgetCap, ceiling)
        );
    }

    /**
     * 예금·적금 이자로 새로 나갈 돈이 전체 발행의
     * {@value #INTEREST_BUDGET_SHARE_BPS} bps를 넘지 않게 하는 금리 상한이다.
     *
     * <p>학생이 많거나 지갑에 돈이 쌓일수록 같은 이율이라도 학급 전체가 받아 갈 이자가 커진다.
     * 이자도 새로 찍는 돈이므로 그대로 두면 금리가 스스로 통화량을 밀어 올린다. 그래서 학생 수와
     * 예상 예치액을 실제로 계산해 상한을 만든다. 발행 계획을 모르면 상한을 걸지 않는다.
     */
    private static Long interestBudgetCapBps(
        RuleEngineRequest request,
        EconomicContext economy,
        Integer operationDays
    ) {
        long issuance = economy.totalIssuance();
        long students = Math.max(0, request.activeStudentCount());
        if (issuance <= 0L || students <= 0L || economy.representativeBalance() <= 0L) {
            return null;
        }
        Integer participation = request.metrics().financialParticipationBps();
        long participationBps = participation == null || participation <= 0
            ? DEFAULT_SAVINGS_PARTICIPATION_BPS
            : Math.min(UNIT_BPS, participation);
        long expectedDeposits = share(
            multiplySaturated(economy.representativeBalance(), students),
            participationBps
        );
        if (expectedDeposits <= 0L) {
            return null;
        }
        int days = operationDays == null ? DEPOSIT_TERM_DAYS * 4 : operationDays;
        long terms = Math.max(1L, days / DEPOSIT_TERM_DAYS);
        long interestBudget = share(issuance, INTEREST_BUDGET_SHARE_BPS);
        return bps(interestBudget, multiplySaturated(expectedDeposits, terms));
    }

    private static String rateBasisMessage(
        RuleEngineRequest request,
        EconomicContext economy,
        Integer operationDays,
        long base,
        long proposed,
        Long budgetCap,
        long ceiling
    ) {
        StringBuilder message = new StringBuilder();
        message.append(operationDays == null
            ? "운영 기간을 확인할 수 없어 기본 기간 기준 " + percentText(base)
            : "운영 기간 " + operationDays + "일 기준 " + percentText(base));
        message.append("에서 시작했습니다.");
        if (economy.recoveryRatioBps() != null && economy.liquidityFactorBps() != UNIT_BPS) {
            message.append(economy.liquidityFactorBps() > UNIT_BPS
                ? " 발행한 돈 가운데 되걷힌 몫이 "
                    + percentText(economy.recoveryRatioBps())
                    + "뿐이라 돈을 묶어 두려고 금리를 올렸습니다."
                : " 되걷힌 몫이 "
                    + percentText(economy.recoveryRatioBps())
                    + "로 충분해 금리를 낮췄습니다.");
        }
        if (economy.velocityFactorBps() != UNIT_BPS) {
            message.append(economy.velocityFactorBps() > UNIT_BPS
                ? " 학생 " + request.activeStudentCount() + "명이라 돈이 빠르게 돌아 조금 올렸습니다."
                : " 학생 " + request.activeStudentCount() + "명으로 적어 조금 낮췄습니다.");
        }
        if (budgetCap != null && ceiling < base) {
            message.append(" 다만 이자로 새로 나갈 돈이 전체 발행의 ")
                .append(percentText(INTEREST_BUDGET_SHARE_BPS))
                .append("를 넘지 않도록 ")
                .append(percentText(ceiling))
                .append("까지만 올릴 수 있어 낮췄습니다.");
        }
        message.append(" 그래서 ").append(percentText(proposed)).append("를 추천했습니다.");
        return message.toString();
    }

    /**
     * 운영 기간을 알 수 없으면(날짜 누락·역전) 한 달 운영 기준을 그대로 쓴다.
     * 이 경우 validateCommon이 이미 BLOCK을 남겨 두었으므로 여기서 추가로 막지 않는다.
     */
    private static long depositBaseRate(Integer operationDays) {
        if (operationDays == null) {
            return 300L;
        }
        if (operationDays <= 7) {
            return 500L;
        }
        if (operationDays <= 31) {
            return 300L;
        }
        if (operationDays <= 90) {
            return 200L;
        }
        return 150L;
    }

    /**
     * 적금은 주 1회씩 나눠 넣고 만기에 한 번 이자를 받는다. 같은 만기라도 처음부터 전액을 맡기는
     * 예금과 달리 평균 예치 기간이 절반쯤이라, 같은 이자를 받으려면 표면 금리가 그만큼 높아야
     * 한다. 그래서 고정된 가산폭 대신 납입 횟수에서 나오는 배수를 예금 금리에 곱한다.
     *
     * <p>납입을 n번 한다면 평균 예치 기간은 만기의 (n+1)/(2n)배다. 뒤집으면 2n/(n+1)배를 곱해야
     * 예금과 같은 이자가 되고, 납입이 잦을수록 2배에 가까워진다. 학생이 지나치게 높은 숫자를
     * 보지 않도록 {@value #SAVINGS_TERM_MULTIPLIER_CAP_BPS} bps에서 끊는다.
     *
     * <p>이자도 새로 찍는 돈이므로 예금과 같은 발행 예산 상한을 함께 건다.
     */
    private void evaluateSavings(
        RuleEngineRequest request,
        EconomicContext economy,
        PaymentSchedule schedule,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        Integer operationDays = schedule == null ? null : schedule.operationDays();
        long depositBase = share(
            share(depositBaseRate(operationDays), economy.liquidityFactorBps()),
            economy.velocityFactorBps()
        );
        long installments = savingsInstallments(operationDays);
        long multiplierBps = savingsTermMultiplierBps(installments);
        long base = share(depositBase, multiplierBps);
        Long budgetCap = interestBudgetCapBps(request, economy, operationDays);
        long ceiling = budgetCap == null
            ? SAVINGS_ALLOWED_MAX_BPS
            : clamp(
                floorRate50(share(budgetCap, multiplierBps)),
                SAVINGS_ALLOWED_MIN_BPS,
                SAVINGS_ALLOWED_MAX_BPS
            );
        long proposed = clamp(roundRate50(base), SAVINGS_ALLOWED_MIN_BPS, ceiling);
        long band = rateBand(request);
        RuleCandidate candidate = new RuleCandidate(
            "savings-rate",
            RuleTargetType.SAVINGS_RATE,
            activation.targetId(),
            proposed,
            Math.max(SAVINGS_ALLOWED_MIN_BPS, proposed - band),
            Math.min(SAVINGS_ALLOWED_MAX_BPS, proposed + band),
            SAVINGS_ALLOWED_MIN_BPS,
            SAVINGS_ALLOWED_MAX_BPS,
            RuleValueUnit.BPS,
            List.of(
                "SAVINGS_AVERAGE_TERM_MULTIPLIER",
                "DEPOSIT_OPERATION_PERIOD",
                "DEPOSIT_INTEREST_BUDGET"
            ),
            "적금 기준금리"
        );
        evaluation.addCandidate(candidate, activation.confirmedValue());
        evaluation.addCheck(
            "savings-rate-basis",
            RuleSeverity.PASS,
            "SAVINGS_RATE_BASIS",
            List.of(candidate.actionId()),
            (operationDays == null
                ? "운영 기간을 확인할 수 없어 기본 기간을 기준으로 "
                : "운영 기간 " + operationDays + "일 동안 " + installments + "번 납입하는 것을 기준으로 ")
                + percentText(proposed) + "를 추천했습니다. 나눠 넣으면 평균 맡긴 기간이 "
                + "예금의 절반쯤이라, 같은 이자를 받으려면 표면 금리를 예금의 "
                + multipleText(multiplierBps) + "배로 잡아야 합니다."
        );
    }

    /** 만기까지 몇 번 넣는지다. 적어도 2번은 넣어야 "나눠 넣는다"는 뜻이 산다. */
    private static long savingsInstallments(Integer operationDays) {
        if (operationDays == null) {
            return 4L;
        }
        return clamp(operationDays / SAVINGS_INSTALLMENT_DAYS, 2L, 12L);
    }

    /** 2n/(n+1)을 만분율로 돌려준다. 납입이 잦을수록 2배에 가까워진다. */
    private static long savingsTermMultiplierBps(long installments) {
        long multiplier = 2L * installments * UNIT_BPS / (installments + 1L);
        return Math.min(multiplier, SAVINGS_TERM_MULTIPLIER_CAP_BPS);
    }

    private static String percentText(long bps) {
        return BigDecimal.valueOf(bps)
            .divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    /**
     * 대출 금리를 예금 금리에 예대마진을 얹어 정한다.
     *
     * <p>현실의 대출 금리도 예금 금리에 업무 원가, 떼일 위험(신용위험), 목표 이익을 더해 만든다.
     * 우리나라 예대금리차는 1.4%p 안팎이라 업무 마진은 그 정도로 두고, 나머지는 학급이 실제로
     * 겪고 있는 위험에서 가져온다. 납입을 밀린 비율이 높으면 그만큼 금리가 올라가고, 돈이 잘
     * 걷히지 않는 학급에서는 대출로 돈이 더 풀리지 않도록 조금 더 얹는다.
     *
     * <p>연체 자료가 아직 없는 학급에서는 기본 위험값을 쓴다. 그래서 예금 3%인 학급의 첫 대출
     * 금리는 7%가 되고, 연체가 쌓이면 학생이 "안 갚으면 이자가 오른다"를 실제로 겪는다.
     */
    private void evaluateLoan(
        RuleEngineRequest request,
        EconomicContext economy,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        if (!request.activeModules().contains("DEPOSIT")) {
            evaluation.addCheck(
                "loan-prerequisite",
                RuleSeverity.BLOCK,
                "MODULE_PREREQUISITE_MISSING",
                List.of(),
                "대출 모듈을 활성화하려면 예금 모듈이 활성 상태여야 합니다."
            );
        }
        Integer deposit = activation.currentDepositRateBps();
        if (deposit == null) {
            evaluation.missing(
                "loan-deposit-rate-required",
                "대출 금리 계산에는 현재 예금 기준금리가 필요합니다.",
                List.of()
            );
            return;
        }
        if (deposit < DEPOSIT_ALLOWED_MIN_BPS || deposit > DEPOSIT_ALLOWED_MAX_BPS) {
            evaluation.rangeBlock(
                "loan-deposit-rate-invalid",
                "현재 예금 기준금리가 허용 범위를 벗어났습니다.",
                List.of()
            );
            return;
        }
        long allowedMin = deposit + 200L;
        long allowedMax = Math.min(deposit + 900L, 1_500L);
        Integer missBps = request.metrics().paymentMissBps();
        long creditRisk = missBps == null
            ? LOAN_DEFAULT_CREDIT_RISK_BPS
            : clamp(missBps * 2L, LOAN_MIN_CREDIT_RISK_BPS, LOAN_MAX_CREDIT_RISK_BPS);
        long liquidityPremium =
            economy.recoveryRatioBps() != null && economy.recoveryRatioBps() < 5_000L
                ? LOAN_TIGHT_LIQUIDITY_PREMIUM_BPS
                : 0L;
        long margin = LOAN_OPERATING_MARGIN_BPS + creditRisk + liquidityPremium;
        long proposed = clamp(roundRate50(deposit + margin), allowedMin, allowedMax);
        long band = rateBand(request);
        RuleCandidate candidate = new RuleCandidate(
            "loan-rate",
            RuleTargetType.LOAN_RATE,
            activation.targetId(),
            proposed,
            Math.max(allowedMin, proposed - band),
            Math.min(allowedMax, proposed + band),
            allowedMin,
            allowedMax,
            RuleValueUnit.BPS,
            List.of("LOAN_DEPOSIT_SPREAD", "LOAN_CREDIT_RISK", "LOAN_LIQUIDITY_PREMIUM"),
            "대출 기준금리"
        );
        Long confirmed = activation.confirmedValue();
        evaluation.addCandidate(candidate, confirmed);
        evaluation.addCheck(
            "loan-rate-basis",
            RuleSeverity.PASS,
            "LOAN_RATE_BASIS",
            List.of(candidate.actionId()),
            "예금 금리 " + percentText(deposit) + "에 은행 운영 몫 "
                + percentText(LOAN_OPERATING_MARGIN_BPS) + "와 "
                + (missBps == null
                    ? "아직 연체 기록이 없어 기본 위험 몫 " + percentText(creditRisk)
                    : "연체율 " + percentText(missBps) + "에서 나온 위험 몫 " + percentText(creditRisk))
                + "를 더해 " + percentText(proposed) + "를 추천했습니다."
                + (liquidityPremium > 0L
                    ? " 되걷히는 돈이 적은 학급이라 대출이 더 풀리지 않도록 "
                        + percentText(liquidityPremium) + "를 더 얹었습니다."
                    : "")
        );
        if (confirmed != null && confirmed < allowedMin) {
            evaluation.addCheck(
                "loan-deposit-spread",
                RuleSeverity.BLOCK,
                "LOAN_DEPOSIT_SPREAD_VIOLATION",
                List.of(candidate.actionId()),
                "대출 금리는 예금 금리보다 최소 200 bps 높아야 합니다."
            );
        }
        long loanCap = loanCapPerStudent(request, economy);
        if (loanCap > 0L) {
            evaluation.addCheck(
                "loan-issuance-cap",
                RuleSeverity.PASS,
                "LOAN_ISSUANCE_CAP",
                List.of(candidate.actionId()),
                "대출도 새로 찍는 돈입니다. 전체 발행의 30%를 넘지 않으려면 1인당 한도를 "
                    + loanCap + " 안팎으로 두는 것이 좋습니다."
            );
        }
    }

    /** 대출 발행이 전체 발행의 30%를 넘지 않게 하는 1인당 한도다. 참고용 안내로만 쓴다. */
    private static long loanCapPerStudent(RuleEngineRequest request, EconomicContext economy) {
        long students = Math.max(0, request.activeStudentCount());
        if (students <= 0L || economy.totalIssuance() <= 0L) {
            return 0L;
        }
        return roundToUnit(
            share(economy.totalIssuance(), 3_000L) / students,
            amountUnit(request.grade())
        );
    }

    /**
     * 투자 운영 조건(종목별 하루 변동 범위, 종목별 1주 시작가)을 추천한다.
     *
     * <p>변동 범위는 학생이 하루 사이 가격이 움직였다고 느낄 만큼은 커야 하고, 며칠에 걸쳐
     * 누적됐을 때 가격이 터지지 않을 만큼은 작아야 한다. 그래서 단기는 크게, 장기는 운영
     * 기간이 길수록 작게 잡는다. 학생이 많으면 같은 범위라도 학급 전체가 주고받는 돈이
     * 커지므로 적금과 같은 방식으로 상한을 한 단계 낮춘다. 종목 사이의 위험 차이는 종목
     * 목록이 가진 위험 비중으로만 낸다.
     *
     * <p>범위는 "±몇 %"인 한 개의 폭으로 추천한다. AI 계약이 후보값을 음수로 받지 않기도 하고,
     * 오르기만 하거나 내리기만 하는 종목이 없어야 학생이 위험을 제대로 배운다.
     *
     * <p>1주 시작가는 학생이 실제로 살 수 있어야 의미가 있다. 대표 잔액(중앙값 우선)의 일부를
     * 투자에 쓴다고 보고, 그 돈으로 한 종목을 {@value #INVESTMENT_TARGET_SHARES}주쯤 살 수
     * 있는 값을 기준가로 삼는다. 종목 사이의 가격 차이는 종목 목록이 가진 가격 비중으로만 낸다.
     */
    private void evaluateInvestment(
        RuleEngineRequest request,
        PaymentSchedule schedule,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        RuleEngineRequest.InvestmentDraft draft = request.investmentDraft();
        // 투자 모듈은 활성화만으로는 정할 숫자가 없다. 조건 초안을 함께 보낸 요청에서만
        // 후보를 만든다. 그러지 않으면 모듈 활성화가 "필수값 없음"으로 막힌다.
        if (draft == null) {
            return;
        }
        if (!Set.of("SHORT_TERM", "LONG_TERM").contains(draft.investmentMode())) {
            evaluation.missing(
                "investment-mode-required",
                "투자 방식은 단기 또는 장기여야 합니다.",
                List.of()
            );
            return;
        }
        if (draft.assets().isEmpty()) {
            evaluation.missing(
                "investment-assets-required",
                "1주 시작가를 정할 종목이 없습니다.",
                List.of()
            );
            return;
        }
        boolean shortTerm = "SHORT_TERM".equals(draft.investmentMode());
        Integer operationDays = schedule == null ? null : schedule.operationDays();
        evaluateInvestmentBands(request, draft, shortTerm, operationDays, activation, evaluation);
        evaluateInvestmentPrices(request, draft, shortTerm, activation, evaluation);
    }

    private void evaluateInvestmentBands(
        RuleEngineRequest request,
        RuleEngineRequest.InvestmentDraft draft,
        boolean shortTerm,
        Integer operationDays,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        long allowedMin = shortTerm
            ? INVESTMENT_SHORT_VOLATILITY_MIN_BPS
            : INVESTMENT_LONG_VOLATILITY_MIN_BPS;
        long allowedMax = (shortTerm
            ? INVESTMENT_SHORT_VOLATILITY_MAX_BPS
            : INVESTMENT_LONG_VOLATILITY_MAX_BPS)
            - (request.activeStudentCount() > LARGE_CLASS_STUDENT_COUNT ? 100L : 0L);
        long base = shortTerm
            ? INVESTMENT_SHORT_VOLATILITY_BPS
            : longTermVolatilityBps(operationDays);
        List<String> bandActionIds = new ArrayList<>();
        for (RuleEngineRequest.InvestmentAsset asset : draft.assets()) {
            BigDecimal risk = asset.riskWeight() == null ? BigDecimal.ONE : asset.riskWeight();
            long proposed = clamp(
                roundRate50(BigDecimal.valueOf(base).multiply(risk).longValue()),
                allowedMin,
                allowedMax
            );
            RuleCandidate candidate = new RuleCandidate(
                "investment-asset-band-" + normalizeActionPart(asset.symbol()),
                RuleTargetType.INVESTMENT_CONDITION,
                activation.targetId(),
                proposed,
                Math.max(allowedMin, proposed - 100L),
                Math.min(allowedMax, proposed + 100L),
                allowedMin,
                allowedMax,
                RuleValueUnit.BPS,
                List.of("INVESTMENT_MODE_HORIZON", "INVESTMENT_ASSET_RISK", "INVESTMENT_CLASS_SIZE"),
                (asset.name() == null ? asset.symbol() : asset.name()) + " 하루 변동 범위(±)"
            );
            evaluation.addCandidate(candidate, asset.confirmedBandBps());
            bandActionIds.add(candidate.actionId());
        }
        evaluation.addCheck(
            "investment-band-basis",
            RuleSeverity.PASS,
            "INVESTMENT_VOLATILITY_BASIS",
            bandActionIds,
            (shortTerm
                ? "단기는 하루 만에 결과가 나므로 변동을 느낄 수 있게 기준 "
                : (operationDays == null
                    ? "운영 기간을 확인할 수 없어 기본 기간 기준으로 기준 "
                    : "장기는 " + operationDays + "일 동안 변동이 쌓이므로 기준 "))
                + percentText(clamp(roundRate50(base), allowedMin, allowedMax))
                + "를 잡고, 종목의 위험 정도만큼 넓히거나 좁혔습니다. "
                + "가격은 이 범위 안에서 매일 새로 정해지므로 오를 수도 내릴 수도 있습니다."
        );
    }

    private void evaluateInvestmentPrices(
        RuleEngineRequest request,
        RuleEngineRequest.InvestmentDraft draft,
        boolean shortTerm,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        long representative = representativeStudentBalance(request, draft);
        BigDecimal investableShare = shortTerm
            ? INVESTMENT_SHORT_BUDGET_SHARE
            : INVESTMENT_LONG_BUDGET_SHARE;
        long basePrice = Math.max(
            1L,
            roundAmount(
                BigDecimal.valueOf(representative)
                    .multiply(investableShare)
                    .divide(BigDecimal.valueOf(INVESTMENT_TARGET_SHARES), 12, RoundingMode.HALF_UP)
            )
        );
        // 1주가 대표 잔액보다 비싸면 아무도 살 수 없다. 그 위로는 허용하지 않는다.
        long allowedMax = Math.max(1L, Math.min(request.systemAmountCap(), representative));
        List<String> priceActionIds = new ArrayList<>();
        for (RuleEngineRequest.InvestmentAsset asset : draft.assets()) {
            BigDecimal weight = asset.priceWeight() == null ? BigDecimal.ONE : asset.priceWeight();
            long proposed = clamp(
                InvestmentAssetCatalog.roundPrice(BigDecimal.valueOf(basePrice).multiply(weight)),
                1L,
                allowedMax
            );
            RuleCandidate candidate = new RuleCandidate(
                "investment-asset-price-" + normalizeActionPart(asset.symbol()),
                RuleTargetType.INVESTMENT_CONDITION,
                activation.targetId(),
                proposed,
                clamp(InvestmentAssetCatalog.roundPrice(BigDecimal.valueOf(proposed).multiply(new BigDecimal("0.8"))), 1L, proposed),
                clamp(InvestmentAssetCatalog.roundPrice(BigDecimal.valueOf(proposed).multiply(new BigDecimal("1.2"))), proposed, allowedMax),
                1L,
                allowedMax,
                RuleValueUnit.AMOUNT,
                List.of("INVESTMENT_STUDENT_BALANCE", "INVESTMENT_ASSET_WEIGHT"),
                (asset.name() == null ? asset.symbol() : asset.name()) + " 1주 시작가"
            );
            evaluation.addCandidate(candidate, asset.confirmedPrice());
            priceActionIds.add(candidate.actionId());
        }
        evaluation.addCheck(
            "investment-price-basis",
            RuleSeverity.PASS,
            "INVESTMENT_PRICE_BASIS",
            priceActionIds,
            "학생 대표 잔액 " + representative + "을 기준으로, 그 가운데 "
                + investableShare.multiply(BigDecimal.valueOf(100L)).stripTrailingZeros().toPlainString()
                + "%로 한 종목을 " + INVESTMENT_TARGET_SHARES + "주쯤 살 수 있게 잡았습니다."
        );
        if (representative <= INVESTMENT_MIN_MEANINGFUL_BALANCE) {
            evaluation.addCheck(
                "investment-balance-too-small",
                RuleSeverity.WARN,
                "INVESTMENT_BALANCE_TOO_SMALL",
                priceActionIds,
                "학생이 가진 돈이 적어 1주를 사기도 어렵습니다. 급여나 보상을 먼저 지급한 뒤 투자를 여는 편이 좋습니다."
            );
        }
    }

    /**
     * 1주 시작가의 기준이 되는 학생 한 명의 돈이다. 잔액이 한쪽으로 몰린 학급에서도 흔들리지
     * 않도록 중앙값을 먼저 쓰고, 없으면 평균, 그것도 없으면 발행액을 학생 수로 나눈다.
     */
    private static long representativeStudentBalance(
        RuleEngineRequest request,
        RuleEngineRequest.InvestmentDraft draft
    ) {
        if (draft.medianStudentBalance() > 0) {
            return draft.medianStudentBalance();
        }
        if (draft.averageStudentBalance() > 0) {
            return draft.averageStudentBalance();
        }
        if (draft.moneyIssuance() > 0 && request.activeStudentCount() > 0) {
            return Math.max(1L, draft.moneyIssuance() / request.activeStudentCount());
        }
        return BASE_SALARY;
    }

    /** 장기는 하루 변동이 며칠에 걸쳐 곱해지므로 운영 기간이 길수록 하루 폭을 줄인다. */
    private static long longTermVolatilityBps(Integer operationDays) {
        if (operationDays == null) {
            return 200L;
        }
        if (operationDays <= 7) {
            return 400L;
        }
        if (operationDays <= 14) {
            return 300L;
        }
        return 200L;
    }

    private void evaluateProduct(
        RuleEngineRequest request,
        long netReferenceSalary,
        Evaluation evaluation
    ) {
        RuleEngineRequest.ProductDraft product = request.productDraft();
        if (product == null) {
            evaluation.missing(
                "product-draft-required",
                "상품 가격 추천에 필요한 상품 초안이 없습니다.",
                List.of()
            );
            return;
        }
        if (
            product.targetId() == null
                || product.kind() == null
                || product.expectedPurchaseFrequency() == null
        ) {
            evaluation.missing(
                "product-value-required",
                "상품 ID, 종류, 예상 구매 빈도가 필요합니다.",
                List.of()
            );
            return;
        }
        if (product.free()) {
            if (product.confirmedPrice() != null && product.confirmedPrice() != 0L) {
                evaluation.rangeBlock(
                    "free-product-price-invalid",
                    "무료 상품 가격은 0이어야 합니다.",
                    List.of()
                );
            }
            return;
        }
        if (
            product.stock() < 0
                || product.expectedDemand() < 1
                || product.expectedPurchaseFrequency().compareTo(BigDecimal.ZERO) <= 0
                || product.vatBps() < 0
                || product.vatBps() > 10_000
        ) {
            evaluation.rangeBlock(
                "product-input-out-of-range",
                "재고·예상 수요·구매 빈도·부가세율이 허용 범위를 벗어났습니다.",
                List.of()
            );
            return;
        }

        BigDecimal targetCheckout = BigDecimal.valueOf(netReferenceSalary)
            .multiply(productRatio(product.kind()))
            .divide(product.expectedPurchaseFrequency(), 12, RoundingMode.HALF_UP);
        BigDecimal scarcity = BigDecimal.ONE;
        if (product.stock() < product.expectedDemand()) {
            BigDecimal stockRatio = BigDecimal.valueOf(product.stock())
                .divide(
                    BigDecimal.valueOf(product.expectedDemand()),
                    12,
                    RoundingMode.HALF_UP
                );
            scarcity = BigDecimal.ONE.add(
                new BigDecimal("0.2").multiply(BigDecimal.ONE.subtract(stockRatio))
            );
            scarcity = scarcity.min(new BigDecimal("1.2")).max(BigDecimal.ONE);
        }
        BigDecimal vatMultiplier = BigDecimal.ONE.add(
            BigDecimal.valueOf(product.vatBps())
                .divide(BigDecimal.valueOf(10_000L), 12, RoundingMode.HALF_UP)
        );
        long proposed = Math.max(
            1L,
            roundAmount(
                targetCheckout.multiply(scarcity)
                    .divide(vatMultiplier, 12, RoundingMode.HALF_UP)
            )
        );
        proposed = Math.min(proposed, request.systemAmountCap());
        long recommendedMin = Math.max(
            1L,
            roundAmount(
                BigDecimal.valueOf(proposed)
                    .multiply(BigDecimal.ONE.subtract(gradeBand(request.grade())))
            )
        );
        long recommendedMax = Math.min(
            request.systemAmountCap(),
            roundAmount(
                BigDecimal.valueOf(proposed)
                    .multiply(BigDecimal.ONE.add(gradeBand(request.grade())))
            )
        );
        RuleCandidate candidate = new RuleCandidate(
            "product-price",
            RuleTargetType.PRODUCT_PRICE,
            product.targetId(),
            proposed,
            recommendedMin,
            recommendedMax,
            1L,
            request.systemAmountCap(),
            RuleValueUnit.AMOUNT,
            List.of(
                "SALARY_PRICE_BALANCE",
                "PRODUCT_KIND_RATIO",
                "PRODUCT_STOCK_SCARCITY"
            ),
            "상품 세전 가격"
        );
        evaluation.addCandidate(candidate, product.confirmedPrice());
    }

    private long evaluateQuiz(
        RuleEngineRequest request,
        long referenceSalary,
        Evaluation evaluation
    ) {
        RuleEngineRequest.QuizDraft quiz = request.quizDraft();
        if (quiz == null) {
            evaluation.missing(
                "quiz-draft-required",
                "퀴즈 보상 추천에 필요한 세션 초안이 없습니다.",
                List.of()
            );
            return 0L;
        }
        if (quiz.targetId() == null || quiz.mode() == null || quiz.category() == null) {
            evaluation.missing(
                "quiz-value-required",
                "퀴즈 ID, 실행 모드, 분류가 필요합니다.",
                List.of()
            );
            return 0L;
        }
        if (quiz.questionCount() < 1) {
            evaluation.rangeBlock(
                "quiz-question-count-invalid",
                "퀴즈 문항 수는 1 이상이어야 합니다.",
                List.of()
            );
        }
        if (quiz.mode() == QuizMode.TEST) {
            if (quiz.confirmedRewardPerPerson() != null && quiz.confirmedRewardPerPerson() > 0) {
                evaluation.addCheck(
                    "test-reward-forbidden",
                    RuleSeverity.BLOCK,
                    "FORBIDDEN_REWARD_CONTEXT",
                    List.of(),
                    "TEST 모드에는 금전 보상을 설정할 수 없습니다."
                );
            }
            return 0L;
        }
        if (
            quiz.expectedParticipants() == null
                || quiz.expectedRuns() == null
                || quiz.expectedParticipants() < 1
                || quiz.expectedRuns() < 1
        ) {
            evaluation.missing(
                "quiz-participation-required",
                "GAME 보상 계산에는 1 이상의 예상 참가자 수와 실행 횟수가 필요합니다.",
                List.of()
            );
            return 0L;
        }

        long adjustedEconomyBase = roundAmount(
            BigDecimal.valueOf(referenceSalary).multiply(new BigDecimal("0.05"))
        );
        long base = quiz.category() == QuizCategory.ECONOMY
            ? adjustedEconomyBase
            : BigDecimal.valueOf(adjustedEconomyBase)
                .multiply(new BigDecimal("0.30"))
                .setScale(0, RoundingMode.FLOOR)
                .longValue();
        long maxCandidateTotal = Math.max(
            0L,
            BigDecimal.valueOf(request.nonRewardIssuance())
                .multiply(BigDecimal.valueOf(3L))
                .divide(BigDecimal.valueOf(7L), 0, RoundingMode.FLOOR)
                .longValue()
                - request.existingRewardIssuance()
        );
        long divisor = multiplySaturated(
            quiz.expectedParticipants().longValue(),
            quiz.expectedRuns().longValue()
        );
        long maxPerPerson = divisor == 0L ? 0L : maxCandidateTotal / divisor;
        long proposed = Math.min(base, maxPerPerson);
        long recommendedMin = Math.max(
            0L,
            roundAmount(
                BigDecimal.valueOf(proposed)
                    .multiply(BigDecimal.ONE.subtract(gradeBand(request.grade())))
            )
        );
        long recommendedMax = Math.min(
            maxPerPerson,
            roundAmount(
                BigDecimal.valueOf(proposed)
                    .multiply(BigDecimal.ONE.add(gradeBand(request.grade())))
            )
        );
        RuleCandidate candidate = new RuleCandidate(
            "game-reward",
            RuleTargetType.GAME_REWARD,
            quiz.targetId(),
            proposed,
            recommendedMin,
            recommendedMax,
            0L,
            maxPerPerson,
            RuleValueUnit.AMOUNT,
            List.of("SALARY_REWARD_BALANCE", "REWARD_ISSUANCE_SHARE"),
            "GAME 1인 참여 보상"
        );
        evaluation.addCandidate(candidate, quiz.confirmedRewardPerPerson());
        long selected = quiz.confirmedRewardPerPerson() == null
            ? proposed
            : Math.max(0L, quiz.confirmedRewardPerPerson());
        long candidateTotal = multiplySaturated(selected, divisor);
        evaluateRewardShare(
            request.nonRewardIssuance(),
            request.existingRewardIssuance(),
            candidateTotal,
            candidate.actionId(),
            evaluation
        );
        return candidateTotal;
    }

    private ReferenceSalary calculateReferenceSalary(
        RuleEngineRequest request,
        Evaluation evaluation
    ) {
        long gross;
        RuleEngineRequest.SalaryPolicy declaredPolicy = request.salaryPolicy();
        // 급여 모듈을 지금 켜는 중이라면 활성 목록에는 아직 없다. 그래도 정책이 들어와 있으면
        // 그것이 곧 이 학급의 급여이므로 참조값 계산에 쓴다.
        boolean salaryKnown = hasSalaryModule(request.activeModules())
            || (declaredPolicy != null && !declaredPolicy.roles().isEmpty());
        if (salaryKnown) {
            RuleEngineRequest.SalaryPolicy policy = request.salaryPolicy();
            List<RuleEngineRequest.SalaryRole> activeRoles = policy == null
                ? List.of()
                : policy.roles().stream().filter(RuleEngineRequest.SalaryRole::active).toList();
            if (!activeRoles.isEmpty() && request.activeStudentCount() > 0) {
                long assigned = activeRoles.stream()
                    .mapToLong(role -> Math.max(0, role.assignedStudentCount()))
                    .sum();
                if (assigned > request.activeStudentCount()) {
                    evaluation.rangeBlock(
                        "salary-assignment-count-invalid",
                        "역할 배정 학생 수가 활성 학생 수를 초과합니다.",
                        List.of()
                    );
                }
                int usedAssigned = (int) Math.min(assigned, request.activeStudentCount());
                BigDecimal total = BigDecimal.ZERO;
                int remainingAssigned = usedAssigned;
                for (RuleEngineRequest.SalaryRole role : activeRoles) {
                    int roleStudents = Math.min(
                        Math.max(0, role.assignedStudentCount()),
                        remainingAssigned
                    );
                    total = total.add(
                        BigDecimal.valueOf(currentRoleSalary(role))
                            .multiply(BigDecimal.valueOf(roleStudents))
                    );
                    remainingAssigned -= roleStudents;
                }
                int unassigned = request.activeStudentCount() - usedAssigned;
                if (unassigned > 0) {
                    long fallbackSalary = policy.defaultRoleSalary() == null
                        ? BASE_SALARY
                        : Math.max(MIN_SALARY, policy.defaultRoleSalary());
                    total = total.add(
                        BigDecimal.valueOf(fallbackSalary)
                            .multiply(BigDecimal.valueOf(unassigned))
                    );
                    evaluation.addCheck(
                        "unassigned-salary-role",
                        RuleSeverity.WARN,
                        "UNASSIGNED_SALARY_ROLE",
                        List.of(),
                        "일부 활성 학생에게 역할이 없어 기본 급여를 참조값에 사용했습니다."
                    );
                }
                gross = roundAmount(
                    total.divide(
                        BigDecimal.valueOf(request.activeStudentCount()),
                        12,
                        RoundingMode.HALF_UP
                    )
                );
            } else if (!activeRoles.isEmpty()) {
                BigDecimal sum = activeRoles.stream()
                    .map(role -> BigDecimal.valueOf(currentRoleSalary(role)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                gross = roundAmount(
                    sum.divide(
                        BigDecimal.valueOf(activeRoles.size()),
                        12,
                        RoundingMode.HALF_UP
                    )
                );
            } else {
                gross = fallbackReferenceSalary(request, evaluation);
            }
        } else {
            gross = fallbackReferenceSalary(request, evaluation);
        }

        int taxBps = 0;
        if (request.activeModules().contains("INCOME_TAX")) {
            if (request.effectiveTaxBps() == null) {
                evaluation.missing(
                    "effective-tax-rate-required",
                    "활성 소득세 모듈의 현재 세율이 필요합니다.",
                    List.of()
                );
            } else if (request.effectiveTaxBps() < 0 || request.effectiveTaxBps() > 10_000) {
                evaluation.rangeBlock(
                    "effective-tax-rate-invalid",
                    "현재 소득세율은 0~10,000 bps 범위여야 합니다.",
                    List.of()
                );
            } else {
                taxBps = request.effectiveTaxBps();
            }
        }
        long net = roundAmount(
            BigDecimal.valueOf(gross)
                .multiply(
                    BigDecimal.ONE.subtract(
                        BigDecimal.valueOf(taxBps)
                            .divide(BigDecimal.valueOf(10_000L), 12, RoundingMode.HALF_UP)
                    )
                )
        );
        return new ReferenceSalary(gross, net);
    }

    private long fallbackReferenceSalary(
        RuleEngineRequest request,
        Evaluation evaluation
    ) {
        if (
            request.otherPeriodicIssuancePerStudent() != null
                && request.otherPeriodicIssuancePerStudent() > 0
        ) {
            return request.otherPeriodicIssuancePerStudent();
        }
        evaluation.addCheck(
            "no-issuance-channel",
            RuleSeverity.WARN,
            "NO_ISSUANCE_CHANNEL",
            List.of(),
            "정기 발행 채널이 없어 1,000을 설명용 참조값으로만 사용했습니다."
        );
        return BASE_SALARY;
    }

    private long currentRoleSalary(RuleEngineRequest.SalaryRole role) {
        if (role.confirmedSalary() != null && role.confirmedSalary() >= MIN_SALARY) {
            return role.confirmedSalary();
        }
        BigDecimal weight = role.weight() == null ? BigDecimal.ONE : role.weight();
        return Math.max(
            MIN_SALARY,
            roundAmount(BigDecimal.valueOf(BASE_SALARY).multiply(weight))
        );
    }

    private void evaluateRewardShare(
        long nonReward,
        long existingReward,
        long candidateReward,
        String actionId,
        Evaluation evaluation
    ) {
        long totalReward = saturatedAdd(existingReward, candidateReward);
        long totalIssuance = saturatedAdd(nonReward, totalReward);
        if (totalIssuance == 0L) {
            return;
        }
        BigDecimal share = ratio(totalReward, totalIssuance);
        if (share.compareTo(REWARD_MAX_SHARE) > 0) {
            evaluation.addCheck(
                "reward-share-exceeded",
                RuleSeverity.BLOCK,
                "REWARD_ISSUANCE_SHARE_EXCEEDED",
                List.of(actionId),
                "전체 발행액 중 보상 비중이 30%를 초과합니다."
            );
        } else if (share.compareTo(REWARD_WARN_SHARE) > 0) {
            evaluation.addCheck(
                "reward-share-high",
                RuleSeverity.WARN,
                "REWARD_SHARE_HIGH",
                List.of(actionId),
                "전체 발행액 중 보상 비중이 25%를 초과합니다."
            );
        }
    }

    private BudgetMetrics evaluateBudget(
        RuleEngineRequest request,
        long candidateReward,
        Evaluation evaluation
    ) {
        long totalReward = saturatedAdd(request.existingRewardIssuance(), candidateReward);
        long totalIssuance = saturatedAdd(request.nonRewardIssuance(), totalReward);
        BigDecimal rewardShare = totalIssuance == 0L
            ? null
            : ratio(totalReward, totalIssuance);

        if (totalIssuance == 0L && request.expectedRecovery() == 0L) {
            evaluation.addCheck(
                "budget-not-applicable",
                RuleSeverity.PASS,
                "BUDGET_NOT_APPLICABLE",
                List.of(),
                "발행액과 회수액이 모두 없어 예산 균형을 적용하지 않습니다."
            );
            return new BudgetMetrics(rewardShare, null);
        }
        if (totalIssuance > 0L && request.expectedRecovery() == 0L) {
            evaluation.addCheck(
                "no-recovery-channel",
                RuleSeverity.WARN,
                "NO_RECOVERY_CHANNEL",
                List.of(),
                "발행은 예정되어 있지만 회수 채널이 없습니다."
            );
        }
        BigDecimal recoveryRatio = totalIssuance == 0L
            ? null
            : ratio(request.expectedRecovery(), totalIssuance);
        if (
            recoveryRatio != null
                && recoveryRatio.compareTo(MIN_RECOVERY_RATIO) >= 0
                && recoveryRatio.compareTo(MAX_RECOVERY_RATIO) <= 0
        ) {
            evaluation.addCheck(
                "budget-balanced",
                RuleSeverity.PASS,
                "BUDGET_BALANCED",
                List.of(),
                "예상 회수액이 전체 발행액의 권장 범위 안에 있습니다."
            );
        } else {
            evaluation.addCheck(
                "issuance-recovery-imbalance",
                RuleSeverity.WARN,
                "ISSUANCE_RECOVERY_IMBALANCE",
                List.of(),
                "예상 회수액이 전체 발행액의 50~120% 권장 범위를 벗어납니다."
            );
        }
        return new BudgetMetrics(rewardShare, recoveryRatio);
    }

    private void addPositiveChecks(Evaluation evaluation) {
        if (!evaluation.requiredMissing) {
            evaluation.addCheck(
                "required-values",
                RuleSeverity.PASS,
                "REQUIRED_VALUES_PRESENT",
                evaluation.allActionIds(),
                "필수 설정값이 준비되었습니다."
            );
        }
        if (!evaluation.candidates.isEmpty() && !evaluation.allowedRangeViolated) {
            evaluation.addCheck(
                "values-within-range",
                RuleSeverity.PASS,
                "VALUES_WITHIN_RANGE",
                evaluation.allActionIds(),
                "확정값과 추천값이 허용 범위 안에 있습니다."
            );
        }
    }

    private static boolean hasSalaryModule(Set<String> activeModules) {
        return activeModules.contains("ROLE_SALARY") || activeModules.contains("SALARY");
    }

    private static BigDecimal gradeBand(int grade) {
        return switch (grade) {
            case 4 -> new BigDecimal("0.10");
            case 5 -> new BigDecimal("0.15");
            case 6 -> new BigDecimal("0.20");
            default -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal minRoleWeight(int grade) {
        return switch (grade) {
            case 4 -> new BigDecimal("0.9");
            case 5 -> new BigDecimal("0.8");
            case 6 -> new BigDecimal("0.7");
            default -> BigDecimal.ONE;
        };
    }

    private static BigDecimal maxRoleWeight(int grade) {
        return switch (grade) {
            case 4 -> new BigDecimal("1.2");
            case 5 -> new BigDecimal("1.4");
            case 6 -> new BigDecimal("1.7");
            default -> BigDecimal.ONE;
        };
    }

    private static BigDecimal maxSalaryGap(int grade) {
        return switch (grade) {
            case 4 -> new BigDecimal("1.5");
            case 5 -> new BigDecimal("2.0");
            case 6 -> new BigDecimal("2.5");
            default -> BigDecimal.ONE;
        };
    }

    /**
     * 소득세율의 허용 범위다. 값 자체는 학급 재정에서 나오고, 학년은 "여기서 벗어나면 수업이
     * 어려워진다"는 안전 폭만 정한다. 높은 학년일수록 더 큰 세율까지 다뤄 볼 수 있게 넓힌다.
     */
    private static RateBand taxBand(int grade) {
        return switch (grade) {
            case 4 -> new RateBand(DEFAULT_TAX_BPS, 200L, 800L);
            case 5 -> new RateBand(DEFAULT_TAX_BPS, 200L, 1_200L);
            case 6 -> new RateBand(DEFAULT_TAX_BPS, 200L, 2_000L);
            default -> new RateBand(DEFAULT_TAX_BPS, 200L, 800L);
        };
    }

    private static BigDecimal productRatio(ProductKind kind) {
        return switch (kind) {
            case CONSUMABLE, PASS -> new BigDecimal("0.15");
            case NORMAL -> new BigDecimal("0.30");
            case RARE, CHARACTER -> new BigDecimal("0.50");
        };
    }

    private static long roundAmount(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** 상한은 넘지 않아야 하므로 내림으로 끊는다. 반올림하면 예산을 조금 넘길 수 있다. */
    private static long floorRate50(long value) {
        return value / 50L * 50L;
    }

    private static long roundRate50(long value) {
        return BigDecimal.valueOf(value)
            .divide(BigDecimal.valueOf(50L), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(50L))
            .longValue();
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    }

    private static long multiplySaturated(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String normalizeActionPart(String value) {
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-")
            .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "role" : normalized;
    }

    /**
     * 학급 경제의 지금 모습이다. 금리·세율·급여가 모두 여기서 나온다.
     *
     * <p>운영이 시작되기 전에는 원장에 거래가 없으므로 급여 계획으로 계산한 예정 발행액을 쓰고,
     * 운영이 시작되면 실제로 오간 금액을 쓴다. 둘 다 있으면 큰 쪽을 택해 발행 규모를 낮잡아 보지
     * 않는다.
     */
    private EconomicContext economicContext(
        RuleEngineRequest request,
        PaymentSchedule schedule
    ) {
        // 여기서 나오는 경고는 문맥별 규칙이 각자 남기므로 버린다. 값만 필요하다.
        ReferenceSalary reference = calculateReferenceSalary(request, new Evaluation());
        ClassroomEconomicMetrics metrics = request.metrics();
        long students = Math.max(0, request.activeStudentCount());
        int payments = schedule == null ? 0 : schedule.paymentCount();
        long plannedSalaryIssuance = multiplySaturated(
            multiplySaturated(reference.gross(), students),
            payments
        );
        long salaryIssuance = Math.max(metrics.realizedSalaryIssuance(), plannedSalaryIssuance);
        long totalIssuance = Math.max(
            Math.max(metrics.realizedIssuance(), metrics.moneyIssuance()),
            Math.max(
                saturatedAdd(plannedSalaryIssuance, request.existingRewardIssuance()),
                saturatedAdd(request.nonRewardIssuance(), request.existingRewardIssuance())
            )
        );
        long recovery = metrics.realizedRecovery() > 0L
            ? metrics.realizedRecovery()
            : request.expectedRecovery();
        Long recoveryRatioBps = totalIssuance > 0L && (recovery > 0L || metrics.hasLedgerHistory())
            ? bps(recovery, totalIssuance)
            : null;
        long representativeBalance = representativeBalance(reference.gross(), metrics);
        return new EconomicContext(
            reference.gross(),
            plannedSalaryIssuance,
            salaryIssuance,
            totalIssuance,
            recovery,
            recoveryRatioBps,
            liquidityFactorBps(recoveryRatioBps),
            velocityFactorBps(request.activeStudentCount()),
            representativeBalance,
            payments
        );
    }

    /** 학생 한 명이 들고 있는 돈이다. 중앙값을 먼저 쓰고, 없으면 평균, 그것도 없으면 급여로 본다. */
    private static long representativeBalance(long gross, ClassroomEconomicMetrics metrics) {
        if (metrics.medianStudentBalance() > 0L) {
            return metrics.medianStudentBalance();
        }
        if (metrics.averageStudentBalance() > 0L) {
            return metrics.averageStudentBalance();
        }
        return gross > 0L ? gross : BASE_SALARY;
    }

    /**
     * 되걷히는 몫이 적으면 돈이 학급에 쌓여 물가가 오른다. 그럴 때 금리를 올려 돈을 묶어 두고,
     * 반대로 너무 많이 걷히면 내려 준다. 중앙은행이 물가를 보고 기준금리를 움직이는 것과 같다.
     */
    private static long liquidityFactorBps(Long recoveryRatioBps) {
        if (recoveryRatioBps == null) {
            return UNIT_BPS;
        }
        if (recoveryRatioBps < 3_500L) {
            return 12_500L;
        }
        if (recoveryRatioBps < 5_000L) {
            return 11_000L;
        }
        return recoveryRatioBps <= 12_000L ? UNIT_BPS : 8_500L;
    }

    /**
     * 학생이 많을수록 거래 상대가 많아 같은 돈이 더 자주 돈다. 교환방정식(MV=PY)에서 유통속도가
     * 오르면 통화량이 그대로여도 물가가 오르므로, 큰 학급에서는 금리를 조금 더 높게 잡는다.
     */
    private static long velocityFactorBps(int studentCount) {
        if (studentCount <= 0) {
            return UNIT_BPS;
        }
        if (studentCount <= SMALL_CLASS_STUDENT_COUNT) {
            return 9_500L;
        }
        if (studentCount <= 20) {
            return UNIT_BPS;
        }
        return studentCount <= LARGE_CLASS_STUDENT_COUNT + 5 ? 10_500L : 11_000L;
    }

    /** 학생이 적으면 한 명의 행동이 지표를 크게 흔들므로 추천 구간을 넓게 준다. */
    private static long rateBand(RuleEngineRequest request) {
        int students = request.activeStudentCount();
        return students > 0 && students < SMALL_CLASS_STUDENT_COUNT
            ? SMALL_CLASS_RATE_BAND_BPS
            : RATE_BAND_BPS;
    }

    /** 금액을 끊어 읽는 단위다. 낮은 학년일수록 굵게 끊어 숫자를 다루기 쉽게 한다. */
    private static long amountUnit(int grade) {
        return switch (grade) {
            case 4 -> 100L;
            case 5 -> 50L;
            default -> 10L;
        };
    }

    private static long roundToUnit(long value, long unit) {
        if (unit <= 1L) {
            return value;
        }
        return BigDecimal.valueOf(value)
            .divide(BigDecimal.valueOf(unit), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(unit))
            .longValue();
    }

    /** 만분율로 표현한 배수를 곱한다. 10,000이 1.00배다. */
    private static long share(long value, long factorBps) {
        return BigDecimal.valueOf(value)
            .multiply(BigDecimal.valueOf(factorBps))
            .divide(BigDecimal.valueOf(UNIT_BPS), 0, RoundingMode.HALF_UP)
            .longValue();
    }

    private static long bps(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(UNIT_BPS))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
            .longValue();
    }

    private static String multipleText(long factorBps) {
        return BigDecimal.valueOf(factorBps)
            .divide(BigDecimal.valueOf(UNIT_BPS), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    private record EconomicContext(
        long perStudentSalary,
        long plannedSalaryIssuance,
        long salaryIssuance,
        long totalIssuance,
        long recoveryAmount,
        Long recoveryRatioBps,
        long liquidityFactorBps,
        long velocityFactorBps,
        long representativeBalance,
        int paymentCount
    ) {
    }

    private record TaxBasis(long proposedBps, boolean onlyRecoveryChannel, String message) {
    }

    private record RateBand(long proposed, long allowedMin, long allowedMax) {
    }

    private record ReferenceSalary(long gross, long net) {
        private static ReferenceSalary notCalculated() {
            return new ReferenceSalary(0L, 0L);
        }
    }

    private record BudgetMetrics(BigDecimal rewardShare, BigDecimal recoveryRatio) {
    }

    private static final class Evaluation {
        private final List<RuleCandidate> candidates = new ArrayList<>();
        private final List<RuleCheck> checks = new ArrayList<>();
        private boolean requiredMissing;
        private boolean allowedRangeViolated;

        private Evaluation() {
        }

        private void addCandidate(RuleCandidate candidate, Long confirmedValue) {
            candidates.add(candidate);
            if (confirmedValue == null) {
                return;
            }
            if (
                confirmedValue < candidate.allowedMinValue()
                    || confirmedValue > candidate.allowedMaxValue()
            ) {
                rangeBlock(
                    candidate.actionId() + "-out-of-range",
                    "확정값이 허용 범위를 벗어났습니다.",
                    List.of(candidate.actionId())
                );
            } else if (
                confirmedValue < candidate.recommendedMinValue()
                    || confirmedValue > candidate.recommendedMaxValue()
            ) {
                addCheck(
                    candidate.actionId() + "-outside-recommended",
                    RuleSeverity.WARN,
                    "OUT_OF_RECOMMENDED_BAND",
                    List.of(candidate.actionId()),
                    "확정값이 허용 범위 안이지만 추천 구간을 벗어났습니다."
                );
            }
        }

        private void missing(String id, String message, List<String> actionIds) {
            requiredMissing = true;
            addCheck(
                id,
                RuleSeverity.BLOCK,
                "REQUIRED_VALUE_MISSING",
                actionIds,
                message
            );
        }

        private void rangeBlock(String id, String message, List<String> actionIds) {
            allowedRangeViolated = true;
            addCheck(
                id,
                RuleSeverity.BLOCK,
                "VALUE_OUT_OF_RANGE",
                actionIds,
                message
            );
        }

        private void addCheck(
            String id,
            RuleSeverity severity,
            String reasonCode,
            List<String> actionIds,
            String message
        ) {
            checks.add(new RuleCheck(id, severity, reasonCode, actionIds, message));
        }

        private List<String> actionIds(RuleTargetType targetType) {
            return candidates.stream()
                .filter(candidate -> candidate.targetType() == targetType)
                .map(RuleCandidate::actionId)
                .toList();
        }

        private List<String> allActionIds() {
            return candidates.stream().map(RuleCandidate::actionId).toList();
        }
    }
}
