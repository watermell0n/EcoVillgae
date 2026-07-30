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


class QuizCategory(StrEnum):
    ECONOMY = "ECONOMY"
    NONSENSE = "NONSENSE"


class QuizQuestionType(StrEnum):
    MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
    OX = "OX"


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
    active_students: Annotated[StrictInt, Field(ge=5)]
    related_transactions: Annotated[StrictInt, Field(ge=10)]
    status: Literal["SUFFICIENT"]


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


class BalancingPayload(ContractModel):
    metric_version: Literal["economic-metrics-v1"]
    threshold_version: Literal["economic-thresholds-v1"]
    period: Period
    sample: Sample
    metrics: Annotated[list[Metric], Field(min_length=1, max_length=100)]
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


class FactUnit(StrEnum):
    AMOUNT = "AMOUNT"
    COUNT = "COUNT"
    RATE_BPS = "RATE_BPS"
    STATUS = "STATUS"
    TEXT = "TEXT"


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


class EconomicEventPayload(ContractModel):
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

    @model_validator(mode="after")
    def validate_interest_rate_scope(self) -> EconomicEventPayload:
        if self.template_type is EventTemplateType.INTEREST_RATE:
            if self.interest_rate_scope is None:
                raise ValueError("INTEREST_RATE 템플릿에는 interest_rate_scope가 필요합니다.")
        elif self.interest_rate_scope is not None:
            raise ValueError("금리 외 템플릿에는 interest_rate_scope를 지정할 수 없습니다.")
        return self


class QuizDraftPayload(ContractModel):
    topic: Annotated[str, Field(min_length=1, max_length=200)]
    learning_objectives: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]],
        Field(min_length=1, max_length=10),
    ]
    category: QuizCategory
    question_type: QuizQuestionType
    count: Annotated[StrictInt, Field(ge=1, le=30)]


class CommonGenerationRequest(ContractModel):
    ai_run_id: UUID
    request_id: UUID
    correlation_id: UUID
    schema_version: Literal["1.0"]
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
    payload: BalancingPayload


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


class BalancingOutput(ContractModel):
    summary: Annotated[str, Field(min_length=1, max_length=1500)]
    evidence_metric_codes: Annotated[list[str], Field(min_length=1)]
    causes: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=500)]],
        Field(min_length=1, max_length=10),
    ]
    proposals: Annotated[list[BalancingProposal], Field(max_length=20)]

    @field_validator("evidence_metric_codes")
    @classmethod
    def evidence_codes_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("근거 지표 코드는 중복될 수 없습니다.")
        return values


class Recommendation(ContractModel):
    text: Annotated[str, Field(min_length=1, max_length=700)]
    side_effects: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]], Field(min_length=1, max_length=5)
    ]


class TeacherChatOutput(ContractModel):
    answer: Annotated[str, Field(min_length=1, max_length=3000)]
    evidence_fact_ids: Annotated[list[str], Field(min_length=1)]
    recommendations: Annotated[list[Recommendation], Field(max_length=5)]
    period: Period
    data_as_of: datetime

    @field_validator("evidence_fact_ids")
    @classmethod
    def evidence_ids_must_be_unique(cls, values: list[str]) -> list[str]:
        if len(values) != len(set(values)):
            raise ValueError("근거 fact_id는 중복될 수 없습니다.")
        return values


class EventChoice(ContractModel):
    choice_key: Annotated[str, Field(pattern=r"^[A-Z][A-Z0-9_]{0,31}$")]
    label: Annotated[str, Field(min_length=1, max_length=200)]
    consequence_text: Annotated[str, Field(min_length=1, max_length=500)]


class EconomicEventOutput(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    template_type: EventTemplateType
    title: Annotated[str, Field(min_length=1, max_length=150)]
    narrative: Annotated[str, Field(min_length=1, max_length=2000)]
    choices: Annotated[list[EventChoice], Field(max_length=5)]
    progress_messages: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=300)]],
        Field(min_length=1, max_length=10),
    ]

    @field_validator("choices")
    @classmethod
    def choice_keys_must_be_unique(cls, values: list[EventChoice]) -> list[EventChoice]:
        keys = [value.choice_key for value in values]
        if len(keys) != len(set(keys)):
            raise ValueError("이벤트 선택지 키는 중복될 수 없습니다.")
        return values


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
    age_appropriateness_tags: Annotated[
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
        return self


class QuizDraftOutput(ContractModel):
    review_status: Literal["REVIEW_REQUIRED"]
    category: QuizCategory
    questions: Annotated[list[QuizQuestionDraft], Field(min_length=1, max_length=30)]


GenerationOutput = BalancingOutput | TeacherChatOutput | EconomicEventOutput | QuizDraftOutput


class Usage(ContractModel):
    input_tokens: Annotated[StrictInt, Field(ge=0)]
    output_tokens: Annotated[StrictInt, Field(ge=0)]


class ResponseMetadata(ContractModel):
    ai_run_id: UUID
    schema_version: Literal["1.0"]
    prompt_version: Annotated[str, Field(pattern=r"^[a-z0-9-]+-v[1-9][0-9]*$")]
    provider: Annotated[str, Field(min_length=1, max_length=50)]
    model_id: Annotated[str, Field(min_length=1, max_length=200)]
    safety_status: Literal["PASSED"]
    validation_codes: ValidationCodes
    usage: Usage


class BalancingGenerationResponse(ResponseMetadata):
    output: BalancingOutput


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
