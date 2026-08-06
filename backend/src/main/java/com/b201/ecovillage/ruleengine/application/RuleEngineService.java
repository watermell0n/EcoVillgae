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

    public static final String RULE_VERSION = "rule-engine-v2";

    private static final long BASE_SALARY = 1_000L;
    private static final long MIN_SALARY = 100L;
    // 대출 금리 규칙(evaluateLoan)이 예금 금리를 이 범위로 전제하므로 함께 바꿔야 한다.
    private static final long DEPOSIT_ALLOWED_MIN_BPS = 100L;
    private static final long DEPOSIT_ALLOWED_MAX_BPS = 600L;
    // 적금은 주 1회 납입이라 평균 예치 기간이 짧다. 예금 기준금리보다 이만큼 높게 추천한다.
    private static final long SAVINGS_DEPOSIT_SPREAD_BPS = 150L;
    private static final long SAVINGS_ALLOWED_MIN_BPS = 100L;
    private static final long SAVINGS_ALLOWED_MAX_BPS = 800L;
    // 이 인원을 넘으면 같은 이율에도 학급 전체 순발행이 커지므로 상한을 한 단계 낮춘다.
    private static final int LARGE_CLASS_STUDENT_COUNT = 25;
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
        ReferenceSalary reference = ReferenceSalary.notCalculated();
        long candidateRewardIssuance = 0L;

        if (request.contextType() != null) {
            switch (request.contextType()) {
                case CLASSROOM_SETUP -> evaluateClassroomSetup(request, evaluation);
                case MODULE_ACTIVATION -> evaluateModuleActivation(request, schedule, evaluation);
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
            case "ROLE_SALARY", "SALARY" -> evaluateSalary(request, activation, evaluation);
            case "INCOME_TAX" -> evaluateIncomeTax(request, activation, evaluation);
            case "DEPOSIT" -> evaluateDeposit(schedule, activation, evaluation);
            case "SAVINGS" -> evaluateSavings(request, schedule, activation, evaluation);
            case "LOAN" -> evaluateLoan(request, activation, evaluation);
            case "INVESTMENT" -> evaluateInvestment(request, schedule, activation, evaluation);
            default -> {
                // Modules without an initial numeric policy intentionally return no candidate.
            }
        }
    }

    private void evaluateSalary(
        RuleEngineRequest request,
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

        BigDecimal minWeight = minRoleWeight(request.grade());
        BigDecimal maxWeight = maxRoleWeight(request.grade());
        long allowedMax = Math.min(
            request.systemAmountCap(),
            roundAmount(BigDecimal.valueOf(BASE_SALARY).multiply(maxWeight))
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
                roundAmount(BigDecimal.valueOf(BASE_SALARY).multiply(role.weight()))
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
                List.of("SALARY_ROLE_WEIGHT", "SALARY_GRADE_PROFILE"),
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
    }

    private void evaluateIncomeTax(
        RuleEngineRequest request,
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
        long proposed = roundRate50(band.proposed());
        RuleCandidate candidate = new RuleCandidate(
            "income-tax-rate",
            RuleTargetType.INCOME_TAX_RATE,
            activation.targetId(),
            proposed,
            Math.max(band.allowedMin(), proposed - 100L),
            Math.min(band.allowedMax(), proposed + 100L),
            band.allowedMin(),
            band.allowedMax(),
            RuleValueUnit.BPS,
            List.of("TAX_GRADE_PROFILE", "TAX_RECOVERY_BALANCE"),
            "소득세율"
        );
        evaluation.addCandidate(candidate, activation.confirmedValue());
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
        PaymentSchedule schedule,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        Integer operationDays = schedule == null ? null : schedule.operationDays();
        long proposed = clamp(
            roundRate50(depositBaseRate(operationDays)),
            DEPOSIT_ALLOWED_MIN_BPS,
            DEPOSIT_ALLOWED_MAX_BPS
        );
        RuleCandidate candidate = new RuleCandidate(
            "deposit-rate",
            RuleTargetType.DEPOSIT_RATE,
            activation.targetId(),
            proposed,
            Math.max(DEPOSIT_ALLOWED_MIN_BPS, proposed - 100L),
            Math.min(DEPOSIT_ALLOWED_MAX_BPS, proposed + 100L),
            DEPOSIT_ALLOWED_MIN_BPS,
            DEPOSIT_ALLOWED_MAX_BPS,
            RuleValueUnit.BPS,
            List.of("DEPOSIT_TERM_BASED", "DEPOSIT_OPERATION_PERIOD"),
            "예금 기준금리"
        );
        evaluation.addCandidate(candidate, activation.confirmedValue());
        // 교사가 "왜 이 값인가"를 화면에서 바로 확인할 수 있도록 근거를 남긴다.
        evaluation.addCheck(
            "deposit-rate-basis",
            RuleSeverity.PASS,
            "DEPOSIT_RATE_BASIS",
            List.of(candidate.actionId()),
            operationDays == null
                ? "운영 기간을 확인할 수 없어 기본 기간 기준으로 "
                    + percentText(proposed) + "를 추천했습니다."
                : "운영 기간 " + operationDays + "일을 기준으로 "
                    + percentText(proposed) + "를 추천했습니다."
        );
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
     * 적금은 주 1회씩 나눠 넣고 만기에 한 번 이자를 받는다. 같은 만기라면 예금보다 평균 예치
     * 기간이 짧아 학생이 체감하는 이자가 작으므로, 예금 기준금리보다 한 단계 높게 추천한다.
     *
     * <p>발행 규모도 함께 본다. 학생 수가 많을수록 같은 이율이라도 학급 전체 순발행이 커지므로
     * 상한을 낮춘다. 예금과 마찬가지로 대출 금리 규칙(예금 대비 최소 200 bps 상향)과 충돌하지
     * 않도록 허용 구간을 예금 상한 위로 넓히지 않는다.
     */
    private void evaluateSavings(
        RuleEngineRequest request,
        PaymentSchedule schedule,
        RuleEngineRequest.ModuleActivation activation,
        Evaluation evaluation
    ) {
        Integer operationDays = schedule == null ? null : schedule.operationDays();
        long base = depositBaseRate(operationDays) + SAVINGS_DEPOSIT_SPREAD_BPS;
        long allowedMax = request.activeStudentCount() > LARGE_CLASS_STUDENT_COUNT
            ? SAVINGS_ALLOWED_MAX_BPS - 100L
            : SAVINGS_ALLOWED_MAX_BPS;
        long proposed = clamp(roundRate50(base), SAVINGS_ALLOWED_MIN_BPS, allowedMax);
        RuleCandidate candidate = new RuleCandidate(
            "savings-rate",
            RuleTargetType.SAVINGS_RATE,
            activation.targetId(),
            proposed,
            Math.max(SAVINGS_ALLOWED_MIN_BPS, proposed - 100L),
            Math.min(allowedMax, proposed + 100L),
            SAVINGS_ALLOWED_MIN_BPS,
            allowedMax,
            RuleValueUnit.BPS,
            List.of("SAVINGS_INSTALLMENT_BASED", "DEPOSIT_OPERATION_PERIOD"),
            "적금 기준금리"
        );
        evaluation.addCandidate(candidate, activation.confirmedValue());
        evaluation.addCheck(
            "savings-rate-basis",
            RuleSeverity.PASS,
            "SAVINGS_RATE_BASIS",
            List.of(candidate.actionId()),
            (operationDays == null
                ? "운영 기간을 확인할 수 없어 기본 기간 기준으로 "
                : "운영 기간 " + operationDays + "일과 학생 " + request.activeStudentCount()
                    + "명을 기준으로 ")
                + percentText(proposed) + "를 추천했습니다. 주 1회 납입이라 예금보다 "
                + percentText(SAVINGS_DEPOSIT_SPREAD_BPS) + " 높게 잡았습니다."
        );
    }

    private static String percentText(long bps) {
        return BigDecimal.valueOf(bps)
            .divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    private void evaluateLoan(
        RuleEngineRequest request,
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
        long proposed = clamp(roundRate50(deposit + 400L), allowedMin, allowedMax);
        RuleCandidate candidate = new RuleCandidate(
            "loan-rate",
            RuleTargetType.LOAN_RATE,
            activation.targetId(),
            proposed,
            Math.max(allowedMin, proposed - 100L),
            Math.min(allowedMax, proposed + 100L),
            allowedMin,
            allowedMax,
            RuleValueUnit.BPS,
            List.of("LOAN_DEPOSIT_SPREAD", "LOAN_GRADE_BAND"),
            "대출 기준금리"
        );
        Long confirmed = activation.confirmedValue();
        evaluation.addCandidate(candidate, confirmed);
        if (confirmed != null && confirmed < allowedMin) {
            evaluation.addCheck(
                "loan-deposit-spread",
                RuleSeverity.BLOCK,
                "LOAN_DEPOSIT_SPREAD_VIOLATION",
                List.of(candidate.actionId()),
                "대출 금리는 예금 금리보다 최소 200 bps 높아야 합니다."
            );
        }
    }

    /**
     * 투자 운영 조건(하루 변동폭, 종목별 1주 시작가)을 추천한다.
     *
     * <p>변동폭은 학생이 하루 사이 가격이 움직였다고 느낄 만큼은 커야 하고, 며칠에 걸쳐
     * 누적됐을 때 가격이 터지지 않을 만큼은 작아야 한다. 그래서 단기는 크게, 장기는 운영
     * 기간이 길수록 작게 잡는다. 학생이 많으면 같은 변동폭이라도 학급 전체가 주고받는 돈이
     * 커지므로 적금과 같은 방식으로 상한을 한 단계 낮춘다.
     *
     * <p>1주 시작가는 학생이 실제로 살 수 있어야 의미가 있다. 대표 잔액(중앙값 우선)의 일부를
     * 투자에 쓴다고 보고, 그 돈으로 한 종목을 {@value #INVESTMENT_TARGET_SHARES}주쯤 살 수
     * 있는 값을 기준가로 삼는다. 종목 사이의 가격 차이는 종목 목록이 가진 비중으로만 낸다.
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
        evaluateInvestmentVolatility(request, draft, shortTerm, operationDays, activation, evaluation);
        evaluateInvestmentPrices(request, draft, shortTerm, activation, evaluation);
    }

    private void evaluateInvestmentVolatility(
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
        long proposed = clamp(roundRate50(base), allowedMin, allowedMax);
        RuleCandidate candidate = new RuleCandidate(
            "investment-daily-volatility",
            RuleTargetType.INVESTMENT_CONDITION,
            activation.targetId(),
            proposed,
            Math.max(allowedMin, proposed - 100L),
            Math.min(allowedMax, proposed + 100L),
            allowedMin,
            allowedMax,
            RuleValueUnit.BPS,
            List.of("INVESTMENT_MODE_HORIZON", "INVESTMENT_CLASS_SIZE"),
            "하루 최대 변동폭"
        );
        evaluation.addCandidate(candidate, draft.confirmedVolatilityBps() == null
            ? null
            : draft.confirmedVolatilityBps().longValue());
        evaluation.addCheck(
            "investment-volatility-basis",
            RuleSeverity.PASS,
            "INVESTMENT_VOLATILITY_BASIS",
            List.of(candidate.actionId()),
            (shortTerm
                ? "단기는 하루 만에 결과가 나므로 변동을 느낄 수 있게 "
                : (operationDays == null
                    ? "운영 기간을 확인할 수 없어 기본 기간 기준으로 "
                    : "장기는 " + operationDays + "일 동안 변동이 쌓이므로 "))
                + percentText(proposed) + "를 추천했습니다."
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
                roundToTen(BigDecimal.valueOf(basePrice).multiply(weight)),
                1L,
                allowedMax
            );
            RuleCandidate candidate = new RuleCandidate(
                "investment-asset-price-" + normalizeActionPart(asset.symbol()),
                RuleTargetType.INVESTMENT_CONDITION,
                activation.targetId(),
                proposed,
                clamp(roundToTen(BigDecimal.valueOf(proposed).multiply(new BigDecimal("0.8"))), 1L, proposed),
                clamp(roundToTen(BigDecimal.valueOf(proposed).multiply(new BigDecimal("1.2"))), proposed, allowedMax),
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
        if (hasSalaryModule(request.activeModules())) {
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

    private static RateBand taxBand(int grade) {
        return switch (grade) {
            case 4 -> new RateBand(300L, 200L, 500L);
            case 5 -> new RateBand(500L, 300L, 800L);
            case 6 -> new RateBand(1_000L, 500L, 1_500L);
            default -> new RateBand(300L, 200L, 500L);
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

    /** 1주 가격은 10 단위로 끊어 학생이 읽기 쉽게 한다. */
    private static long roundToTen(BigDecimal value) {
        return value.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.TEN)
            .longValue();
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
