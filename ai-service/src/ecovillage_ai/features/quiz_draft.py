from __future__ import annotations

import re
from decimal import Decimal, DivisionByZero, InvalidOperation

from ecovillage_ai.errors import ServiceError
from ecovillage_ai.features.teacher_chat import (
    EMAIL_PATTERN,
    PHONE_PATTERN,
    SECRET_PATTERN,
    UUID_PATTERN,
)
from ecovillage_ai.models import (
    ErrorCode,
    QuizDraftGenerationRequest,
    QuizDraftOutput,
    QuizQuestionType,
)

PROMPT_INJECTION_PATTERNS = (
    re.compile(r"이전\s*(?:지시|명령).{0,8}무시"),
    re.compile(r"(?:시스템|숨겨진)\s*(?:프롬프트|지시)"),
    re.compile(r"(?:비밀|내부\s*지시).{0,12}(?:보여|출력|공개)"),
)
UNSAFE_CONTENT_PATTERN = re.compile(
    r"(?:병신|등신|멍청이?|바보|찌질이?|왕따|죽여|때려|성행위|야한|"
    r"개꿀|노잼|킹받|[ㅋㅎ]{4,})"
)
ELEMENTARY_SCOPE_PATTERN = re.compile(
    r"(?:미분|적분|파생상품|공매도|레버리지|선물\s*옵션|신용부도스왑)"
)
NORMALIZE_PATTERN = re.compile(r"[^0-9A-Za-z가-힣]+")
ARITHMETIC_PATTERN = re.compile(
    r"(?<!\d)(-?\d+(?:\.\d+)?)\s*([+\-×xX*÷/])\s*(-?\d+(?:\.\d+)?)"
)
NUMERIC_CHOICE_PATTERN = re.compile(
    r"^\s*(-?\d+(?:\.\d+)?)\s*(?:원|개|명|%|퍼센트)?\s*$"
)
EXPLANATION_KEY_PATTERN = re.compile(
    r"정답\s*(?:은|:)?\s*([A-EXO])(?:\s*번|입니다|이다|예요|이다\.)?"
)

OX_TRUE_LABELS = {"o", "맞다", "참", "옳다"}
OX_FALSE_LABELS = {"x", "아니다", "거짓", "틀리다"}
GRADE_TEXT_LIMITS = {
    4: (180, 100, 300),
    5: (220, 120, 400),
    6: (260, 150, 500),
}


def validate_quiz_draft_input(request: QuizDraftGenerationRequest) -> None:
    """민감하거나 지시를 탈취하는 퀴즈 요청을 공급자 호출 전에 거부한다."""

    text = " ".join(
        (
            request.payload.topic,
            *request.payload.concepts,
            *request.payload.learning_objectives,
        )
    )
    codes: list[str] = []
    if UUID_PATTERN.search(text) or EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text):
        codes.append("SENSITIVE_INPUT_DETECTED")
    if SECRET_PATTERN.search(text):
        codes.append("SECRET_INPUT_DETECTED")
    if any(pattern.search(text) for pattern in PROMPT_INJECTION_PATTERNS):
        codes.append("QUIZ_PROMPT_INJECTION_INPUT")
    if UNSAFE_CONTENT_PATTERN.search(text):
        codes.append("QUIZ_UNSAFE_INPUT")
    if ELEMENTARY_SCOPE_PATTERN.search(text):
        codes.append("QUIZ_ELEMENTARY_SCOPE_INPUT_VIOLATION")
    if not codes:
        return
    raise ServiceError(
        status_code=422,
        code=ErrorCode.INPUT_SCHEMA_INVALID,
        message="퀴즈 초안 입력에 허용되지 않는 내용이 포함되어 있습니다.",
        retryable=False,
        validation_codes=list(dict.fromkeys(codes)),
        validation_stage="CONTENT_SAFETY",
    )


def quiz_structure_quality_codes(output: QuizDraftOutput) -> list[str]:
    codes: list[str] = []
    normalized_questions = [_normalize(question.question) for question in output.questions]
    if len(normalized_questions) != len(set(normalized_questions)):
        codes.append("QUIZ_DUPLICATE_QUESTION")

    for question in output.questions:
        normalized_choices = [_normalize(choice.text) for choice in question.choices]
        if len(normalized_choices) != len(set(normalized_choices)):
            codes.append("QUIZ_DUPLICATE_CHOICE_MEANING")

        if question.question_type is QuizQuestionType.OX:
            choice_by_key = {
                choice.choice_key: _normalize(choice.text) for choice in question.choices
            }
            if (
                choice_by_key.get("O") not in OX_TRUE_LABELS
                or choice_by_key.get("X") not in OX_FALSE_LABELS
            ):
                codes.append("QUIZ_OX_CHOICE_TEXT_MISMATCH")

        explanation_keys = EXPLANATION_KEY_PATTERN.findall(question.explanation.upper())
        if any(key != question.correct_choice_key for key in explanation_keys):
            codes.append("QUIZ_EXPLANATION_ANSWER_MISMATCH")

        codes.extend(_numeric_answer_codes(question))
    return list(dict.fromkeys(codes))


def quiz_grade_quality_codes(
    request: QuizDraftGenerationRequest,
    output: QuizDraftOutput,
) -> list[str]:
    question_limit, choice_limit, explanation_limit = GRADE_TEXT_LIMITS[request.grade]
    codes: list[str] = []
    for question in output.questions:
        if (
            len(question.question) > question_limit
            or len(question.explanation) > explanation_limit
            or any(len(choice.text) > choice_limit for choice in question.choices)
        ):
            codes.append("QUIZ_GRADE_TEXT_TOO_COMPLEX")
    text = " ".join(
        item
        for question in output.questions
        for item in (
            question.question,
            question.explanation,
            *(choice.text for choice in question.choices),
        )
    )
    if ELEMENTARY_SCOPE_PATTERN.search(text):
        codes.append("QUIZ_ELEMENTARY_SCOPE_VIOLATION")
    return list(dict.fromkeys(codes))


def quiz_content_safety_codes(output: QuizDraftOutput) -> list[str]:
    text = " ".join(
        item
        for question in output.questions
        for item in (
            question.question,
            question.explanation,
            *(choice.text for choice in question.choices),
        )
    )
    return ["QUIZ_UNSAFE_CONTENT"] if UNSAFE_CONTENT_PATTERN.search(text) else []


def _numeric_answer_codes(question: object) -> list[str]:
    question_text = getattr(question, "question", "")
    match = ARITHMETIC_PATTERN.search(question_text)
    if match is None:
        return []
    try:
        expected = _calculate(Decimal(match[1]), match[2], Decimal(match[3]))
    except (DivisionByZero, InvalidOperation):
        return ["QUIZ_NUMERIC_EXPRESSION_INVALID"]

    matching_keys = []
    for choice in getattr(question, "choices", []):
        numeric_match = NUMERIC_CHOICE_PATTERN.fullmatch(choice.text)
        if numeric_match is not None and Decimal(numeric_match[1]) == expected:
            matching_keys.append(choice.choice_key)
    if not matching_keys or getattr(question, "correct_choice_key", None) not in matching_keys:
        return ["QUIZ_NUMERIC_ANSWER_MISMATCH"]
    if len(matching_keys) > 1:
        return ["QUIZ_MULTIPLE_NUMERIC_ANSWERS"]
    return []


def _calculate(left: Decimal, operator: str, right: Decimal) -> Decimal:
    if operator == "+":
        return left + right
    if operator == "-":
        return left - right
    if operator in {"×", "x", "X", "*"}:
        return left * right
    return left / right


def _normalize(value: str) -> str:
    return NORMALIZE_PATTERN.sub("", value).casefold()
