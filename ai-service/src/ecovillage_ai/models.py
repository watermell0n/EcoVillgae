"""이코빌리지 내부 AI 요청·응답 계약 모델."""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    field_validator,
    model_validator,
)

ShortText = Annotated[str, Field(min_length=1, max_length=100)]
ValidationCodes = Annotated[list[str], Field(max_length=50)]
ReasonCode = Annotated[str, Field(pattern=r"^[A-Z][A-Z0-9_]{1,79}$")]


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class MetricCode(StrEnum):
    LIQUID_MONEY_CHANGE_RATE_BPS = "LIQUID_MONEY_CHANGE_RATE_BPS"
    ISSUANCE_AMOUNT = "ISSUANCE_AMOUNT"
    RECOVERY_AMOUNT = "RECOVERY_AMOUNT"
    ISSUANCE_RECOVERY_RATE_BPS = "ISSUANCE_RECOVERY_RATE_BPS"
    WALLET_AVERAGE_CHANGE_RATE_BPS = "WALLET_AVERAGE_CHANGE_RATE_BPS"
    WALLET_MEDIAN_CHANGE_RATE_BPS = "WALLET_MEDIAN_CHANGE_RATE_BPS"
    ASSET_CONCENTRATION_TOP20_BPS = "ASSET_CONCENTRATION_TOP20_BPS"
    CONSUMPTION_RATE_BPS = "CONSUMPTION_RATE_BPS"
    PRODUCT_SELL_THROUGH_RATE_BPS = "PRODUCT_SELL_THROUGH_RATE_BPS"
    PRODUCT_STOCKOUT_SHARE_BPS = "PRODUCT_STOCKOUT_SHARE_BPS"
    FINANCIAL_PARTICIPATION_RATE_BPS = "FINANCIAL_PARTICIPATION_RATE_BPS"
    PAYMENT_MISS_RATE_BPS = "PAYMENT_MISS_RATE_BPS"
    INVESTMENT_PARTICIPATION_RATE_BPS = "INVESTMENT_PARTICIPATION_RATE_BPS"
    INVESTMENT_MEDIAN_RETURN_BPS = "INVESTMENT_MEDIAN_RETURN_BPS"
    INVESTMENT_LOSS_PARTICIPANT_RATE_BPS = "INVESTMENT_LOSS_PARTICIPANT_RATE_BPS"
    REWARD_ISSUANCE_SHARE_BPS = "REWARD_ISSUANCE_SHARE_BPS"


class MetricAvailability(StrEnum):
    AVAILABLE = "AVAILABLE"
    UNAVAILABLE = "UNAVAILABLE"


class MetricUnit(StrEnum):
    AMOUNT = "AMOUNT"
    COUNT = "COUNT"
    RATE_BPS = "RATE_BPS"


class MetricState(StrEnum):
    NORMAL = "NORMAL"
    OBSERVE = "OBSERVE"
    WARN = "WARN"
    UNAVAILABLE = "UNAVAILABLE"


class MetricDirection(StrEnum):
    LOW = "LOW"
    NORMAL = "NORMAL"
    HIGH = "HIGH"
    NOT_APPLICABLE = "NOT_APPLICABLE"


class ActionTargetType(StrEnum):
    PRODUCT_PRICE = "PRODUCT_PRICE"
    PRODUCT_STOCK = "PRODUCT_STOCK"
    ACTIVITY_REWARD = "ACTIVITY_REWARD"
    GAME_REWARD = "GAME_REWARD"
    QUIZ_REWARD = "QUIZ_REWARD"
    TAX_RATE = "TAX_RATE"
    INFLATION_RATE = "INFLATION_RATE"
    DEPOSIT_RATE = "DEPOSIT_RATE"
    SAVINGS_RATE = "SAVINGS_RATE"
    LOAN_RATE = "LOAN_RATE"


class AnalysisMode(StrEnum):
    SETUP_PRECHECK = "SETUP_PRECHECK"
    ON_DEMAND = "ON_DEMAND"
    AUTO_MONITOR = "AUTO_MONITOR"


class AnalysisTriggerType(StrEnum):
    TEACHER_REQUEST = "TEACHER_REQUEST"
    AUTO_MONITOR = "AUTO_MONITOR"


class AnalysisSeverity(StrEnum):
    NORMAL = "NORMAL"
    OBSERVE = "OBSERVE"
    WARN = "WARN"


class SampleStatus(StrEnum):
    SUFFICIENT = "SUFFICIENT"
    INSUFFICIENT = "INSUFFICIENT"


class SetupContextType(StrEnum):
    CLASSROOM_SETUP = "CLASSROOM_SETUP"
    PRODUCT_DRAFT = "PRODUCT_DRAFT"
    QUIZ_SESSION_DRAFT = "QUIZ_SESSION_DRAFT"


class CourseScale(StrEnum):
    ONE_SHOT = "ONE_SHOT"
    ONE_WEEK = "ONE_WEEK"
    ONE_MONTH = "ONE_MONTH"
    ONE_SEMESTER = "ONE_SEMESTER"


class SetupRecommendationTargetType(StrEnum):
    ROLE_SALARY = "ROLE_SALARY"
    INCOME_TAX_RATE = "INCOME_TAX_RATE"
    DEPOSIT_RATE = "DEPOSIT_RATE"
    LOAN_RATE = "LOAN_RATE"
    PRODUCT_PRICE = "PRODUCT_PRICE"
    GAME_REWARD = "GAME_REWARD"


class PreflightSeverity(StrEnum):
    PASS = "PASS"
    WARN = "WARN"
    BLOCK = "BLOCK"


class EventTemplateType(StrEnum):
    PRICE = "PRICE"
    SUPPLY = "SUPPLY"
    TAX = "TAX"
    INTEREST_RATE = "INTEREST_RATE"
    INVESTMENT_NEWS = "INVESTMENT_NEWS"
    REWARD = "REWARD"
    PUBLIC_FUND = "PUBLIC_FUND"


class InterestRateScope(StrEnum):
    DEPOSIT = "DEPOSIT"
    SAVINGS = "SAVINGS"
    LOAN = "LOAN"


class EventSourceType(StrEnum):
    TEACHER_REQUEST = "TEACHER_REQUEST"
    MONITOR_RECOMMENDATION = "MONITOR_RECOMMENDATION"


class QuizCategory(StrEnum):
    ECONOMY = "ECONOMY"
    NONSENSE = "NONSENSE"


class QuizQuestionType(StrEnum):
    MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
    OX = "OX"


class QuizDifficulty(StrEnum):
    INTRODUCTORY = "INTRODUCTORY"
    STANDARD = "STANDARD"
    ADVANCED = "ADVANCED"


class QuizRunMode(StrEnum):
    GAME = "GAME"
    TEST = "TEST"


class Period(ContractModel):
    from_: datetime = Field(alias="from")
    to: datetime
    data_as_of: datetime

    @model_validator(mode="after")
    def validate_period(self) -> Period:
        values = (self.from_, self.to, self.data_as_of)
        if any(value.tzinfo is None or value.utcoffset() is None for value in values):
            raise ValueError("기간 시각에는 시간대가 필요합니다.")
        if self.from_ > self.to or self.to > self.data_as_of:
            raise ValueError("기간은 from <= to <= data_as_of 순서여야 합니다.")
        return self


class Sample(ContractModel):
    active_students: Annotated[StrictInt, Field(ge=0)]
    related_transactions: Annotated[StrictInt, Field(ge=0)]
    status: SampleStatus

    @model_validator(mode="after")
    def validate_status(self) -> Sample:
        expected = (
            SampleStatus.SUFFICIENT
            if self.active_students >= 5 and self.related_transactions >= 10
            else SampleStatus.INSUFFICIENT
        )
        if self.status is not expected:
            raise ValueError("표본 상태가 활성 학생·관련 거래 최소 기준과 일치하지 않습니다.")
        return self


class Metric(ContractModel):
    code: MetricCode
    value: StrictInt | None
    previous_value: StrictInt | None = None
    availability: MetricAvailability
    unavailable_reason: Annotated[str, Field(max_length=200)] | None
    unit: MetricUnit
    state: MetricState
    direction: MetricDirection
    subject_label: Annotated[str, Field(max_length=100)] | None = None

    @model_validator(mode="after")
    def validate_availability(self) -> Metric:
        if self.availability is MetricAvailability.AVAILABLE:
            if self.value is None or self.unavailable_reason is not None:
                raise ValueError(
                    "사용 가능한 지표에는 값이 있고 unavailable_reason이 없어야 합니다."
                )
            if self.state is MetricState.UNAVAILABLE:
                raise ValueError("사용 가능한 지표의 상태는 UNAVAILABLE일 수 없습니다.")
        else:
            if self.value is not None or not self.unavailable_reason:
                raise ValueError("사용 불가능한 지표에는 사유가 필요하고 값은 없어야 합니다.")
            if self.state is not MetricState.UNAVAILABLE:
                raise ValueError("사용 불가능한 지표의 상태는 UNAVAILABLE이어야 합니다.")
            if self.direction is not MetricDirection.NOT_APPLICABLE:
                raise ValueError("사용 불가능한 지표의 방향은 NOT_APPLICABLE이어야 합니다.")
        return self


class AllowedAction(ContractModel):
    action_id: ShortText
    target_type: ActionTargetType
    target_id: UUID
    current_value: Annotated[StrictInt, Field(ge=0)]
    min_value: Annotated[StrictInt, Field(ge=0)]
    max_value: Annotated[StrictInt, Field(ge=0)]
    unit: MetricUnit
    label: Annotated[str, Field(min_length=1, max_length=100)] | None = None

    @model_validator(mode="after")
    def validate_range(self) -> AllowedAction:
        if not self.min_value <= self.current_value <= self.max_value:
            raise ValueError("허용 조정값은 min_value <= current_value <= max_value여야 합니다.")
        return self


class SetupContext(ContractModel):
    context_type: SetupContextType
    course_scale: CourseScale
    expected_students: Annotated[StrictInt, Field(ge=1, le=100)]
    operation_period_days: Annotated[StrictInt, Field(ge=1, le=365)]
    active_modules: Annotated[list[ShortText], Field(max_length=30)]
    quiz_mode: QuizRunMode | None = None

    @model_validator(mode="after")
    def validate_quiz_mode(self) -> SetupContext:
        if len(self.active_modules) != len(set(self.active_modules)):
            raise ValueError("활성 모듈은 중복될 수 없습니다.")
        if self.context_type is SetupContextType.QUIZ_SESSION_DRAFT:
            if self.quiz_mode is None:
                raise ValueError("퀴즈 세션 설정 문맥에는 quiz_mode가 필요합니다.")
        elif self.quiz_mode is not None:
            raise ValueError("퀴즈 세션 외 설정 문맥에는 quiz_mode를 지정할 수 없습니다.")
        return self


class SetupCandidate(ContractModel):
    action_id: ShortText
    target_type: SetupRecommendationTargetType
    target_id: UUID | None = None
    proposed_value: Annotated[StrictInt, Field(ge=0)]
    min_value: Annotated[StrictInt, Field(ge=0)]
    max_value: Annotated[StrictInt, Field(ge=0)]
    unit: MetricUnit
    reason_codes: Annotated[list[ReasonCode], Field(min_length=1, max_length=20)]
    label: Annotated[str, Field(min_length=1, max_length=100)]

    @model_validator(mode="after")
    def validate_range(self) -> SetupCandidate:
        if not self.min_value <= self.proposed_value <= self.max_value:
            raise ValueError("설정 후보값은 min_value와 max_value 범위 안이어야 합니다.")
        if len(self.reason_codes) != len(set(self.reason_codes)):
            raise ValueError("설정 후보의 reason_codes는 중복될 수 없습니다.")
        return self


class PreflightCheck(ContractModel):
    check_id: ShortText
    severity: PreflightSeverity
    reason_code: ReasonCode
    subject_action_ids: Annotated[list[ShortText], Field(max_length=20)]
    message: Annotated[str, Field(min_length=1, max_length=500)]

    @field_validator("subject_action_ids")
    @classmethod
    def subject_action_ids_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("점검 대상 action_id는 중복될 수 없습니다.")
        return values


class SetupPrecheckPayload(ContractModel):
    context: SetupContext
    candidates: Annotated[list[SetupCandidate], Field(max_length=30)]
    checks: Annotated[list[PreflightCheck], Field(min_length=1, max_length=50)]

    @model_validator(mode="after")
    def validate_context_candidates(self) -> SetupPrecheckPayload:
        candidate_ids = [candidate.action_id for candidate in self.candidates]
        if len(candidate_ids) != len(set(candidate_ids)):
            raise ValueError("설정 후보 action_id는 중복될 수 없습니다.")
        check_ids = [check.check_id for check in self.checks]
        if len(check_ids) != len(set(check_ids)):
            raise ValueError("시작 전 점검 check_id는 중복될 수 없습니다.")

        allowed_targets = {
            SetupContextType.CLASSROOM_SETUP: {
                SetupRecommendationTargetType.ROLE_SALARY,
                SetupRecommendationTargetType.INCOME_TAX_RATE,
                SetupRecommendationTargetType.DEPOSIT_RATE,
                SetupRecommendationTargetType.LOAN_RATE,
            },
            SetupContextType.PRODUCT_DRAFT: {SetupRecommendationTargetType.PRODUCT_PRICE},
            SetupContextType.QUIZ_SESSION_DRAFT: {SetupRecommendationTargetType.GAME_REWARD},
        }
        if any(
            candidate.target_type not in allowed_targets[self.context.context_type]
            for candidate in self.candidates
        ):
            raise ValueError("설정 문맥에서 허용하지 않는 추천 대상입니다.")
        if self.context.course_scale is CourseScale.ONE_SHOT and self.candidates:
            raise ValueError("1회 코스에는 경제 설정·화폐 보상 후보를 둘 수 없습니다.")
        if self.context.context_type is SetupContextType.QUIZ_SESSION_DRAFT and (
            self.context.quiz_mode is QuizRunMode.TEST
        ):
            if self.candidates:
                raise ValueError("TEST와 1회 코스 퀴즈에는 화폐 보상 후보를 둘 수 없습니다.")

        if self.context.context_type is SetupContextType.CLASSROOM_SETUP and any(
            candidate.target_id is not None for candidate in self.candidates
        ):
            raise ValueError("학급 개설 설정 후보에는 개별 target_id를 둘 수 없습니다.")
        if self.context.context_type in {
            SetupContextType.PRODUCT_DRAFT,
            SetupContextType.QUIZ_SESSION_DRAFT,
        } and any(candidate.target_id is None for candidate in self.candidates):
            raise ValueError("상품·퀴즈 설정 후보에는 대상 target_id가 필요합니다.")

        known_ids = set(candidate_ids)
        if any(
            not set(check.subject_action_ids) <= known_ids
            for check in self.checks
        ):
            raise ValueError("점검 결과가 알 수 없는 설정 후보를 참조합니다.")
        return self


class BalancingPayload(ContractModel):
    metric_version: Literal["economic-metrics-v1"]
    threshold_version: Literal["economic-thresholds-v1"]
    period: Period
    previous_period: Period | None = None
    analysis_severity: AnalysisSeverity
    sample: Sample
    metrics: Annotated[list[Metric], Field(max_length=100)]
    allowed_actions: Annotated[list[AllowedAction], Field(max_length=100)]

    @field_validator("metrics")
    @classmethod
    def metric_codes_must_be_unique(cls, values: list[Metric]) -> list[Metric]:
        codes = [value.code for value in values]
        if len(codes) != len(set(codes)):
            raise ValueError("지표 코드는 중복될 수 없습니다.")
        return values

    @field_validator("allowed_actions")
    @classmethod
    def action_ids_must_be_unique(cls, values: list[AllowedAction]) -> list[AllowedAction]:
        action_ids = [value.action_id for value in values]
        if len(action_ids) != len(set(action_ids)):
            raise ValueError("action_id는 중복될 수 없습니다.")
        return values

    @model_validator(mode="after")
    def validate_period_pair(self) -> BalancingPayload:
        if self.previous_period is None:
            return self
        if self.previous_period.to != self.period.from_:
            raise ValueError("이전 분석 구간은 현재 구간 바로 앞에서 끝나야 합니다.")
        current_duration = self.period.to - self.period.from_
        previous_duration = self.previous_period.to - self.previous_period.from_
        if current_duration != previous_duration:
            raise ValueError("현재와 이전 분석 구간의 길이는 같아야 합니다.")
        if self.previous_period.data_as_of != self.period.data_as_of:
            raise ValueError("현재와 이전 분석 구간의 data_as_of는 같아야 합니다.")
        return self


class FactUnit(StrEnum):
    AMOUNT = "AMOUNT"
    COUNT = "COUNT"
    RATE_BPS = "RATE_BPS"
    STATUS = "STATUS"
    TEXT = "TEXT"


class TeacherChatResponseMode(StrEnum):
    ANSWER = "ANSWER"
    LIMITED = "LIMITED"


class TeacherChatLimitationCode(StrEnum):
    PERSONAL_ANALYSIS_NOT_SUPPORTED = "PERSONAL_ANALYSIS_NOT_SUPPORTED"
    REAL_FINANCIAL_ADVICE_NOT_SUPPORTED = "REAL_FINANCIAL_ADVICE_NOT_SUPPORTED"
    FUTURE_PREDICTION_NOT_SUPPORTED = "FUTURE_PREDICTION_NOT_SUPPORTED"
    READ_ONLY_BOUNDARY = "READ_ONLY_BOUNDARY"
    PROMPT_INJECTION_BLOCKED = "PROMPT_INJECTION_BLOCKED"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class Fact(ContractModel):
    fact_id: ShortText
    label: Annotated[str, Field(min_length=1, max_length=150)]
    value: StrictInt | Annotated[str, Field(max_length=500)]
    unit: FactUnit


class PolicyChangeFact(ContractModel):
    fact_id: ShortText
    changed_at: datetime
    summary: Annotated[str, Field(min_length=1, max_length=500)]

    @field_validator("changed_at")
    @classmethod
    def changed_at_requires_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("changed_at에는 시간대가 필요합니다.")
        return value


class TeacherChatPayload(ContractModel):
    question: Annotated[str, Field(min_length=1, max_length=1000)]
    period: Period
    facts: Annotated[list[Fact], Field(min_length=1, max_length=100)]
    recent_policy_changes: Annotated[list[PolicyChangeFact], Field(max_length=20)]

    @field_validator("facts")
    @classmethod
    def fact_ids_must_be_unique(cls, values: list[Fact]) -> list[Fact]:
        fact_ids = [value.fact_id for value in values]
        if len(fact_ids) != len(set(fact_ids)):
            raise ValueError("fact_id는 중복될 수 없습니다.")
        return values

    @field_validator("recent_policy_changes")
    @classmethod
    def policy_fact_ids_must_be_unique(
        cls, values: list[PolicyChangeFact]
    ) -> list[PolicyChangeFact]:
        fact_ids = [value.fact_id for value in values]
        if len(fact_ids) != len(set(fact_ids)):
            raise ValueError("정책 변경 fact_id는 중복될 수 없습니다.")
        changed_at = [value.changed_at for value in values]
        if changed_at != sorted(changed_at):
            raise ValueError("정책 변경 이력은 changed_at 오름차순이어야 합니다.")
        return values

    @model_validator(mode="after")
    def validate_policy_change_context(self) -> TeacherChatPayload:
        fact_ids = {fact.fact_id for fact in self.facts}
        policy_fact_ids = {change.fact_id for change in self.recent_policy_changes}
        if fact_ids & policy_fact_ids:
            raise ValueError("집계 사실과 정책 변경 fact_id는 중복될 수 없습니다.")
        if any(
            change.changed_at > self.period.data_as_of
            for change in self.recent_policy_changes
        ):
            raise ValueError("정책 변경 시각은 data_as_of 이후일 수 없습니다.")
        return self


class InvestmentPriceFact(ContractModel):
    step_index: Annotated[StrictInt, Field(ge=1)]
    price: Annotated[StrictInt, Field(ge=1)]
    change_rate_bps: Annotated[StrictInt, Field(ge=-10000, le=100000)]
    revealed_at: datetime

    @field_validator("revealed_at")
    @classmethod
    def revealed_at_requires_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("revealed_at에는 시간대가 필요합니다.")
        return value


class InvestmentNewsContext(ContractModel):
    scenario_version_id: UUID
    asset_id: UUID
    price_step_id: UUID
    asset_name: Annotated[str, Field(min_length=1, max_length=100)]
    asset_symbol: Annotated[str, Field(pattern=r"^[A-Z][A-Z0-9]{1,9}$")]
    current_step_index: Annotated[StrictInt, Field(ge=1)]
    public_price_facts: Annotated[
        list[InvestmentPriceFact], Field(min_length=1, max_length=100)
    ]

    @model_validator(mode="after")
    def validate_public_price_facts(self) -> InvestmentNewsContext:
        indexes = [fact.step_index for fact in self.public_price_facts]
        if len(indexes) != len(set(indexes)):
            raise ValueError("공개 가격 사실의 step_index는 중복될 수 없습니다.")
        if indexes != sorted(indexes):
            raise ValueError("공개 가격 사실은 step_index 오름차순이어야 합니다.")
        if indexes[-1] != self.current_step_index:
            raise ValueError("공개 가격 사실은 현재 가격 단계까지만 포함해야 합니다.")
        if indexes != list(range(1, self.current_step_index + 1)):
            raise ValueError("공개 가격 사실은 첫 단계부터 현재 단계까지 빠짐없이 포함해야 합니다.")
        revealed_times = [fact.revealed_at for fact in self.public_price_facts]
        if any(
            previous >= current
            for previous, current in zip(revealed_times, revealed_times[1:], strict=False)
        ):
            raise ValueError("공개 가격 사실의 시각은 단계 순서대로 증가해야 합니다.")
        return self


class EconomicEventPayload(ContractModel):
    course_scale: CourseScale
    source_type: EventSourceType
    source_proposal_id: UUID | None = None
    source_evidence_codes: Annotated[list[ReasonCode], Field(max_length=20)]
    template_type: EventTemplateType
    interest_rate_scope: InterestRateScope | None = None
    effect_summary: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]],
        Field(min_length=1, max_length=10),
    ]
    prohibited_effects: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=200)]],
        Field(min_length=1, max_length=30),
    ]
    teacher_context: Annotated[str, Field(max_length=1000)] | None = None
    investment_news_context: InvestmentNewsContext | None = None

    @model_validator(mode="after")
    def validate_event_context(self) -> EconomicEventPayload:
        if self.source_type is EventSourceType.MONITOR_RECOMMENDATION:
            if self.source_proposal_id is None or not self.source_evidence_codes:
                raise ValueError("모니터링 추천 이벤트에는 원본 제안과 근거 코드가 필요합니다.")
        elif self.source_proposal_id is not None or self.source_evidence_codes:
            raise ValueError("교사 요청 이벤트에는 모니터링 제안 참조를 지정할 수 없습니다.")
        if len(self.source_evidence_codes) != len(set(self.source_evidence_codes)):
            raise ValueError("이벤트 출처 근거 코드는 중복될 수 없습니다.")

        if self.template_type is EventTemplateType.INTEREST_RATE:
            if self.interest_rate_scope is None:
                raise ValueError("INTEREST_RATE 템플릿에는 interest_rate_scope가 필요합니다.")
        elif self.interest_rate_scope is not None:
            raise ValueError("금리 외 템플릿에는 interest_rate_scope를 지정할 수 없습니다.")

        if self.template_type is EventTemplateType.INVESTMENT_NEWS:
            if self.investment_news_context is None:
                raise ValueError("INVESTMENT_NEWS에는 투자 뉴스 문맥이 필요합니다.")
        elif self.investment_news_context is not None:
            raise ValueError("투자 뉴스 외 템플릿에는 투자 뉴스 문맥을 지정할 수 없습니다.")
        return self


class QuizDraftPayload(ContractModel):
    topic: Annotated[str, Field(min_length=1, max_length=200)]
    difficulty: QuizDifficulty
    concepts: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=100)]],
        Field(min_length=1, max_length=20),
    ]
    learning_objectives: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]],
        Field(min_length=1, max_length=10),
    ]
    category: QuizCategory
    question_type: QuizQuestionType
    count: Annotated[StrictInt, Field(ge=1, le=30)]

    @field_validator("concepts")
    @classmethod
    def concepts_must_be_unique(cls, values: list[str]) -> list[str]:
        normalized = [" ".join(value.split()).casefold() for value in values]
        if any(not value for value in normalized):
            raise ValueError("퀴즈 개념은 공백일 수 없습니다.")
        if len(normalized) != len(set(normalized)):
            raise ValueError("퀴즈 개념은 중복될 수 없습니다.")
        return values

    @field_validator("learning_objectives")
    @classmethod
    def learning_objectives_must_be_unique(cls, values: list[str]) -> list[str]:
        normalized = [" ".join(value.split()).casefold() for value in values]
        if any(not value for value in normalized):
            raise ValueError("퀴즈 학습목표는 공백일 수 없습니다.")
        if len(normalized) != len(set(normalized)):
            raise ValueError("퀴즈 학습목표는 중복될 수 없습니다.")
        return values


class CommonGenerationRequest(ContractModel):
    ai_run_id: UUID
    request_id: UUID
    correlation_id: UUID
    schema_version: Literal["1.1"]
    prompt_version: Annotated[str, Field(pattern=r"^[a-z0-9-]+-v[1-9][0-9]*$")]
    attempt: Annotated[StrictInt, Field(ge=1, le=2)]
    deadline_at: datetime
    locale: Literal["ko-KR"]
    grade: Annotated[StrictInt, Field(ge=4, le=6)]

    @field_validator("deadline_at")
    @classmethod
    def deadline_requires_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("deadline_at에는 시간대가 필요합니다.")
        return value


class BalancingGenerationRequest(CommonGenerationRequest):
    analysis_mode: AnalysisMode
    trigger_type: AnalysisTriggerType
    payload: SetupPrecheckPayload | BalancingPayload

    @model_validator(mode="after")
    def validate_analysis_context(self) -> BalancingGenerationRequest:
        if self.analysis_mode is AnalysisMode.SETUP_PRECHECK:
            if self.trigger_type is not AnalysisTriggerType.TEACHER_REQUEST:
                raise ValueError("설정 점검은 교사 설정 흐름에서만 요청할 수 있습니다.")
            if not isinstance(self.payload, SetupPrecheckPayload):
                raise ValueError("SETUP_PRECHECK에는 설정 점검 payload가 필요합니다.")
        else:
            if not isinstance(self.payload, BalancingPayload):
                raise ValueError("운영 분석에는 경제 지표 payload가 필요합니다.")
            expected_trigger = (
                AnalysisTriggerType.AUTO_MONITOR
                if self.analysis_mode is AnalysisMode.AUTO_MONITOR
                else AnalysisTriggerType.TEACHER_REQUEST
            )
            if self.trigger_type is not expected_trigger:
                raise ValueError("분석 모드와 trigger_type이 일치하지 않습니다.")
            if (
                self.analysis_mode is AnalysisMode.AUTO_MONITOR
                and self.payload.previous_period is None
            ):
                raise ValueError("자동 모니터링에는 같은 길이의 이전 분석 구간이 필요합니다.")
        return self


class TeacherChatGenerationRequest(CommonGenerationRequest):
    payload: TeacherChatPayload


class EconomicEventGenerationRequest(CommonGenerationRequest):
    payload: EconomicEventPayload


class QuizDraftGenerationRequest(CommonGenerationRequest):
    payload: QuizDraftPayload


GenerationRequest = (
    BalancingGenerationRequest
    | TeacherChatGenerationRequest
    | EconomicEventGenerationRequest
    | QuizDraftGenerationRequest
)


class BalancingProposal(ContractModel):
    action_id: ShortText
    target_type: ActionTargetType
    label: Annotated[str, Field(min_length=1, max_length=100)] | None
    current_value: Annotated[StrictInt, Field(ge=0)]
    min_value: Annotated[StrictInt, Field(ge=0)]
    max_value: Annotated[StrictInt, Field(ge=0)]
    unit: MetricUnit
    proposed_value: Annotated[StrictInt, Field(ge=0)]
    evidence_metric_codes: Annotated[list[str], Field(min_length=1)]
    rationale: Annotated[str, Field(min_length=1, max_length=1000)]
    expected_effect: Annotated[str, Field(min_length=1, max_length=1000)]
    side_effects: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]], Field(min_length=1, max_length=5)
    ]
    priority: Annotated[StrictInt, Field(ge=1, le=100)]

    @field_validator("evidence_metric_codes")
    @classmethod
    def evidence_codes_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("근거 지표 코드는 중복될 수 없습니다.")
        return values


class BalancingCause(ContractModel):
    text: Annotated[str, Field(min_length=1, max_length=500)]
    evidence_metric_codes: Annotated[list[str], Field(min_length=1)]

    @field_validator("evidence_metric_codes")
    @classmethod
    def evidence_codes_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("원인 근거 지표 코드는 중복될 수 없습니다.")
        return values


class BalancingOutput(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    analysis_mode: AnalysisMode
    trigger_type: AnalysisTriggerType
    analysis_severity: AnalysisSeverity
    metric_version: Literal["economic-metrics-v1"]
    threshold_version: Literal["economic-thresholds-v1"]
    period: Period
    previous_period: Period | None
    summary: Annotated[str, Field(min_length=1, max_length=1500)]
    evidence_metric_codes: Annotated[list[str], Field(min_length=1)]
    causes: Annotated[list[BalancingCause], Field(min_length=1, max_length=10)]
    proposals: Annotated[list[BalancingProposal], Field(max_length=20)]

    @field_validator("evidence_metric_codes")
    @classmethod
    def evidence_codes_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("근거 지표 코드는 중복될 수 없습니다.")
        return values


class SetupCandidateExplanation(ContractModel):
    action_id: ShortText
    target_type: SetupRecommendationTargetType
    label: Annotated[str, Field(min_length=1, max_length=100)]
    proposed_value: Annotated[StrictInt, Field(ge=0)]
    min_value: Annotated[StrictInt, Field(ge=0)]
    max_value: Annotated[StrictInt, Field(ge=0)]
    unit: MetricUnit
    evidence_reason_codes: Annotated[list[ReasonCode], Field(min_length=1, max_length=20)]
    rationale: Annotated[str, Field(min_length=1, max_length=1000)]
    cautions: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]], Field(max_length=5)
    ]

    @field_validator("evidence_reason_codes")
    @classmethod
    def evidence_codes_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("설정 후보 설명의 근거 코드는 중복될 수 없습니다.")
        return values


class PreflightCheckExplanation(ContractModel):
    check_id: ShortText
    severity: PreflightSeverity
    reason_code: ReasonCode
    subject_action_ids: Annotated[list[ShortText], Field(max_length=20)]
    explanation: Annotated[str, Field(min_length=1, max_length=1000)]
    correction_guidance: Annotated[str, Field(min_length=1, max_length=1000)] | None = None

    @field_validator("subject_action_ids")
    @classmethod
    def subject_action_ids_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("점검 설명 대상 action_id는 중복될 수 없습니다.")
        return values


class SetupPrecheckOutput(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    context_type: SetupContextType
    overall_severity: PreflightSeverity
    summary: Annotated[str, Field(min_length=1, max_length=1500)]
    candidate_explanations: Annotated[
        list[SetupCandidateExplanation], Field(max_length=30)
    ]
    check_explanations: Annotated[
        list[PreflightCheckExplanation], Field(min_length=1, max_length=50)
    ]


class GroundedCause(ContractModel):
    text: Annotated[str, Field(min_length=1, max_length=700)]
    evidence_fact_ids: Annotated[list[ShortText], Field(min_length=1, max_length=20)]

    @field_validator("evidence_fact_ids")
    @classmethod
    def evidence_ids_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("원인 근거 fact_id는 중복될 수 없습니다.")
        return values


class Recommendation(ContractModel):
    text: Annotated[str, Field(min_length=1, max_length=700)]
    evidence_fact_ids: Annotated[list[ShortText], Field(min_length=1, max_length=20)]
    side_effects: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]], Field(min_length=1, max_length=5)
    ]

    @field_validator("evidence_fact_ids")
    @classmethod
    def evidence_ids_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("대응안 근거 fact_id는 중복될 수 없습니다.")
        return values


class TeacherChatOutput(ContractModel):
    response_mode: TeacherChatResponseMode
    answer: Annotated[str, Field(min_length=1, max_length=3000)]
    evidence_fact_ids: Annotated[list[ShortText], Field(max_length=100)]
    causes: Annotated[list[GroundedCause], Field(max_length=10)]
    recommendations: Annotated[list[Recommendation], Field(max_length=5)]
    limitation_codes: Annotated[list[TeacherChatLimitationCode], Field(max_length=10)]
    period: Period
    data_as_of: datetime

    @field_validator("evidence_fact_ids")
    @classmethod
    def evidence_ids_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("근거 fact_id는 중복될 수 없습니다.")
        return values

    @field_validator("limitation_codes")
    @classmethod
    def limitation_codes_must_be_unique(
        cls, values: list[TeacherChatLimitationCode]
    ) -> list[TeacherChatLimitationCode]:
        if len(values) != len(set(values)):
            raise ValueError("챗봇 제한 코드는 중복될 수 없습니다.")
        return values

    @model_validator(mode="after")
    def validate_response_mode(self) -> TeacherChatOutput:
        if self.response_mode is TeacherChatResponseMode.ANSWER:
            if not self.evidence_fact_ids:
                raise ValueError("일반 답변에는 하나 이상의 근거 fact_id가 필요합니다.")
            if not self.causes:
                raise ValueError("일반 답변에는 근거가 연결된 원인이 필요합니다.")
            if not self.recommendations:
                raise ValueError("일반 답변에는 근거가 연결된 대응안이 필요합니다.")
            if self.limitation_codes:
                raise ValueError("일반 답변에는 제한 코드를 포함할 수 없습니다.")
            evidence_ids = set(self.evidence_fact_ids)
            nested_ids = {
                fact_id
                for item in (*self.causes, *self.recommendations)
                for fact_id in item.evidence_fact_ids
            }
            if not nested_ids <= evidence_ids:
                raise ValueError("원인과 대응안의 근거는 전체 근거 목록에 포함되어야 합니다.")
        else:
            if not self.limitation_codes:
                raise ValueError("제한 답변에는 하나 이상의 제한 코드가 필요합니다.")
            if self.evidence_fact_ids or self.causes or self.recommendations:
                raise ValueError("제한 답변에는 사실 근거·원인·대응안을 포함할 수 없습니다.")
        return self


class EventChoice(ContractModel):
    choice_key: Annotated[str, Field(pattern=r"^[A-Z][A-Z0-9_]{0,31}$")]
    label: Annotated[str, Field(min_length=1, max_length=200)]
    consequence_text: Annotated[str, Field(min_length=1, max_length=500)]


class InvestmentNewsBinding(ContractModel):
    scenario_version_id: UUID
    asset_id: UUID
    price_step_id: UUID
    asset_name: Annotated[str, Field(min_length=1, max_length=100)]
    asset_symbol: Annotated[str, Field(pattern=r"^[A-Z][A-Z0-9]{1,9}$")]
    current_step_index: Annotated[StrictInt, Field(ge=1)]


class EconomicEventOutput(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    template_type: EventTemplateType
    source_type: EventSourceType
    source_proposal_id: UUID | None
    source_evidence_codes: Annotated[list[ReasonCode], Field(max_length=20)]
    interest_rate_scope: InterestRateScope | None
    title: Annotated[str, Field(min_length=1, max_length=150)]
    narrative: Annotated[str, Field(min_length=1, max_length=2000)]
    choices: Annotated[list[EventChoice], Field(min_length=2, max_length=5)]
    progress_messages: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]],
        Field(min_length=1, max_length=10),
    ]
    investment_news_binding: InvestmentNewsBinding | None = None
    teacher_explanation: Annotated[str, Field(min_length=1, max_length=1000)] | None = None

    @field_validator("choices")
    @classmethod
    def choice_keys_must_be_unique(cls, values: list[EventChoice]) -> list[EventChoice]:
        keys = [value.choice_key for value in values]
        if len(keys) != len(set(keys)):
            raise ValueError("이벤트 선택지 키는 중복될 수 없습니다.")
        return values

    @model_validator(mode="after")
    def validate_event_output(self) -> EconomicEventOutput:
        if self.source_type is EventSourceType.MONITOR_RECOMMENDATION:
            if self.source_proposal_id is None or not self.source_evidence_codes:
                raise ValueError("모니터링 추천 출력에는 원본 제안과 근거 코드가 필요합니다.")
        elif self.source_proposal_id is not None or self.source_evidence_codes:
            raise ValueError("교사 요청 출력에는 모니터링 제안 참조를 둘 수 없습니다.")
        if len(self.source_evidence_codes) != len(set(self.source_evidence_codes)):
            raise ValueError("이벤트 출력 근거 코드는 중복될 수 없습니다.")

        if self.template_type is EventTemplateType.INTEREST_RATE:
            if self.interest_rate_scope is None:
                raise ValueError("금리 이벤트 출력에는 interest_rate_scope가 필요합니다.")
        elif self.interest_rate_scope is not None:
            raise ValueError("금리 외 이벤트 출력에는 interest_rate_scope를 둘 수 없습니다.")

        if self.template_type is EventTemplateType.INVESTMENT_NEWS:
            if self.investment_news_binding is None or self.teacher_explanation is None:
                raise ValueError("투자 뉴스 출력에는 단계 연결과 교사용 설명이 필요합니다.")
        elif self.investment_news_binding is not None or self.teacher_explanation is not None:
            raise ValueError("투자 뉴스 외 이벤트에는 투자 뉴스 전용 출력을 둘 수 없습니다.")

        normalized_labels = [" ".join(choice.label.split()).casefold() for choice in self.choices]
        if len(normalized_labels) != len(set(normalized_labels)):
            raise ValueError("이벤트 선택지 문구는 중복될 수 없습니다.")
        normalized_messages = [
            " ".join(message.split()).casefold() for message in self.progress_messages
        ]
        if len(normalized_messages) != len(set(normalized_messages)):
            raise ValueError("이벤트 진행 문구는 중복될 수 없습니다.")
        return self


class QuizChoice(ContractModel):
    choice_key: Annotated[str, Field(pattern=r"^(?:[A-E]|O|X)$")]
    text: Annotated[str, Field(min_length=1, max_length=300)]


class QuizQuestionDraft(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    question_type: QuizQuestionType
    question: Annotated[str, Field(min_length=1, max_length=1000)]
    choices: Annotated[list[QuizChoice], Field(min_length=2, max_length=5)]
    correct_choice_key: Annotated[str, Field(pattern=r"^(?:[A-E]|O|X)$")]
    explanation: Annotated[str, Field(min_length=1, max_length=1000)]
    difficulty: QuizDifficulty
    concept_tags: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=50)]], Field(min_length=1, max_length=10)
    ]

    @model_validator(mode="after")
    def validate_choices(self) -> QuizQuestionDraft:
        keys = [choice.choice_key for choice in self.choices]
        if len(keys) != len(set(keys)):
            raise ValueError("퀴즈 선택지 키는 중복될 수 없습니다.")
        if self.correct_choice_key not in keys:
            raise ValueError("정답 키는 선택지에 존재해야 합니다.")
        if self.question_type is QuizQuestionType.OX and set(keys) != {"O", "X"}:
            raise ValueError("OX 문항은 O와 X 선택지만 가져야 합니다.")
        if self.question_type is QuizQuestionType.MULTIPLE_CHOICE:
            if not 3 <= len(keys) <= 5 or any(key not in "ABCDE" for key in keys):
                raise ValueError("객관식 문항은 A-E 범위의 선택지 3~5개를 가져야 합니다.")
        if len(self.concept_tags) != len(set(self.concept_tags)):
            raise ValueError("퀴즈 개념 태그는 중복될 수 없습니다.")
        return self


class QuizDraftOutput(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    category: QuizCategory
    questions: Annotated[list[QuizQuestionDraft], Field(min_length=1, max_length=30)]


GenerationOutput = (
    SetupPrecheckOutput
    | BalancingOutput
    | TeacherChatOutput
    | EconomicEventOutput
    | QuizDraftOutput
)


class Usage(ContractModel):
    input_tokens: Annotated[StrictInt, Field(ge=0)]
    output_tokens: Annotated[StrictInt, Field(ge=0)]


class ResponseMetadata(ContractModel):
    ai_run_id: UUID
    schema_version: Literal["1.1"]
    prompt_version: Annotated[str, Field(pattern=r"^[a-z0-9-]+-v[1-9][0-9]*$")]
    provider: Annotated[str, Field(min_length=1, max_length=50)]
    model_id: Annotated[str, Field(min_length=1, max_length=200)]
    safety_status: Literal["PASSED"]
    validation_codes: ValidationCodes
    usage: Usage


class BalancingGenerationResponse(ResponseMetadata):
    output: SetupPrecheckOutput | BalancingOutput


class TeacherChatGenerationResponse(ResponseMetadata):
    output: TeacherChatOutput


class EconomicEventGenerationResponse(ResponseMetadata):
    output: EconomicEventOutput


class QuizDraftGenerationResponse(ResponseMetadata):
    output: QuizDraftOutput


GenerationResponse = (
    BalancingGenerationResponse
    | TeacherChatGenerationResponse
    | EconomicEventGenerationResponse
    | QuizDraftGenerationResponse
)


class ErrorCode(StrEnum):
    INPUT_SCHEMA_INVALID = "AI_INPUT_SCHEMA_INVALID"
    SERVICE_SCOPE_VIOLATION = "AI_SERVICE_SCOPE_VIOLATION"
    RUN_CONTEXT_MISMATCH = "AI_RUN_CONTEXT_MISMATCH"
    INSUFFICIENT_DATA = "AI_INSUFFICIENT_DATA"
    OUTPUT_SCHEMA_INVALID = "AI_OUTPUT_SCHEMA_INVALID"
    OUTPUT_SAFETY_REJECTED = "AI_OUTPUT_SAFETY_REJECTED"
    PROVIDER_RATE_LIMITED = "AI_PROVIDER_RATE_LIMITED"
    PROVIDER_UNAVAILABLE = "AI_PROVIDER_UNAVAILABLE"
    DEADLINE_EXCEEDED = "AI_DEADLINE_EXCEEDED"


class ErrorDetail(ContractModel):
    code: ErrorCode
    message: Annotated[str, Field(min_length=1, max_length=500)]
    retryable: bool
    validation_codes: ValidationCodes


class ErrorResponse(ContractModel):
    error: ErrorDetail
    ai_run_id: UUID
    correlation_id: UUID


class HealthResponse(ContractModel):
    status: Literal["UP"]
    service: str
    version: str
    provider: str
