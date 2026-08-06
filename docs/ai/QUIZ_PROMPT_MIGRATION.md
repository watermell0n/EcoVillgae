# 퀴즈 프롬프트 분리 — 적용 안내

> 상태: **적용 완료 (v0.2).**
> 아래 코드 3곳을 `prompting.py`에 반영했고 `pytest` 558건과 `ruff check` 모두 통과했습니다.
> 이 문서는 무엇을 왜 바꿨는지 남기는 기록입니다.
>
> 검증 결과 — 넌센스 5학년 요청의 시스템 프롬프트에서 경제 지시가 사라졌습니다.
>
> | 요청 | 읽는 파일 | 마지막 줄 | 경제 지시 |
> |---|---|---|---|
> | ECONOMY 6학년 | `economy.txt` | 6학년 수준에서 원인과 영향을 구분하되… | 있음 (정상) |
> | NONSENSE 5학년 | `nonsense.txt` | 5학년이 아는 낱말로 말놀이를 만든다… | **없음 (해결)** |

## 왜 고치나

지금 시스템 프롬프트는 세 조각을 이어 붙입니다([`prompting.py:67-73`](../../ai-service/src/ecovillage_ai/prompting.py)).

```
common.txt  +  quiz-draft.txt  +  GRADE_INSTRUCTIONS[학년]
```

그런데 `GRADE_INSTRUCTIONS`는 **학년만 보고 카테고리를 보지 않습니다.**

| 학년 | 붙는 문장 | 넌센스일 때 |
|---|---|---|
| 4 | 짧고 구체적인 표현을 사용한다 | 문제없음 |
| 5 | 초등 5학년 수준의 **경제 개념을 일상 사례와 연결해 설명한다** | ❌ |
| 6 | 6학년 수준에서 **원인과 영향을 구분**하되 실제 금융 조언은 하지 않는다 | ❌ |

이 문장이 프롬프트 **맨 마지막**에 붙습니다. AI는 보통 마지막 지시를 가장 강하게 따릅니다.
그래서 넌센스를 시켜도 경제 상식 문제가 나옵니다. **이게 넌센스 품질 저하의 1순위 원인입니다.**

## 새로 만들어 둔 파일

| 파일 | 내용 |
|---|---|
| `prompts/v1/quiz-draft/economy.txt` | 경제 전용. 난이도 3단계 정의 + 오답 설계 규칙 + 예시 4개 |
| `prompts/v1/quiz-draft/nonsense.txt` | 넌센스 전용. 말장난 5가지 방법 + 금지 사항 + 예시 5개 |

두 파일 모두 기존 `quiz-draft.txt`의 공통 규칙을 그대로 품고 있어서 **경로만 바꾸면 됩니다.**

예시는 팀에서 고른 실제 문항([`넌센스.txt`](#), [`경제퀴즈.txt`](#))에서 뽑았습니다.

- 경제: 오답이 전부 실제 경제 용어(세금/월급/이자/투자/저축/소비)인 점을 규칙으로 만들었습니다.
  원본 1~7번=용어 뜻, 8~9번=이유·현상, 10번=교실 상황 적용이 난이도 3단계와 그대로 맞아떨어집니다.
- 넌센스: 40개 문항을 말장난 방식 5가지로 분류해 규칙으로 만들었습니다
  (소리 겹치기 / 글자 쪼개기 / 외국 이름 다시 읽기 / 흉내내는 말 / 질문 속 함정).

## 고쳐야 할 코드 3곳

모두 [`ai-service/src/ecovillage_ai/prompting.py`](../../ai-service/src/ecovillage_ai/prompting.py) 한 파일입니다.

### 1. 카테고리별 학년 지침 추가 (22행 근처)

기존 `GRADE_INSTRUCTIONS`는 **그대로 둡니다.** 밸런싱·챗봇·경제 이벤트가 계속 씁니다.
아래를 새로 추가합니다.

```python
QUIZ_GRADE_INSTRUCTIONS = {
    ("economy", 4): "초등학교 4학년이 아는 낱말만 쓰고 문장을 짧게 쓴다. 경제 용어는 배운 그대로 쓴다.",
    ("economy", 5): "초등학교 5학년 수준의 경제 개념을 일상 사례와 연결해 설명한다.",
    ("economy", 6): "초등학교 6학년 수준에서 원인과 영향을 구분하되 실제 금융 조언은 하지 않는다.",
    ("nonsense", 4): "초등학교 4학년이 아는 낱말로 말놀이를 만든다. 경제 개념을 설명하려 하지 않는다.",
    ("nonsense", 5): "초등학교 5학년이 아는 낱말로 말놀이를 만든다. 경제 개념을 설명하려 하지 않는다.",
    ("nonsense", 6): "초등학교 6학년이 아는 낱말로 말놀이를 만든다. 경제 개념을 설명하려 하지 않는다.",
}
```

### 2. 프롬프트 조립에서 카테고리 반영 (67행 근처)

```python
        system_instruction = "\n\n".join(
            (
                self._read_asset("v1/common.txt"),
                self._read_asset(selection.asset_path),
                self._grade_instruction(kind, selection),   # ← 바뀐 줄
            )
        )
```

그리고 클래스에 메서드를 하나 더합니다.

```python
    @staticmethod
    def _grade_instruction(kind: GenerationKind, selection: PromptSelection) -> str:
        if kind is GenerationKind.QUIZ_DRAFT:
            category = selection.mode.split("-", 1)[0]
            return QUIZ_GRADE_INSTRUCTIONS[(category, selection.grade)]
        return GRADE_INSTRUCTIONS[selection.grade]
```

`selection.mode`는 이미 `economy-multiple-choice` 형태라서 앞부분만 잘라 쓰면 됩니다.

### 3. 카테고리별 파일 경로 (161행 근처)

```python
        if kind is GenerationKind.QUIZ_DRAFT and isinstance(
            request, QuizDraftGenerationRequest
        ):
            category = request.payload.category.value.casefold()
            question_type = request.payload.question_type.value.casefold().replace("_", "-")
            return (
                f"{category}-{question_type}",
                "quiz-draft-v1",
                f"v1/quiz-draft/{category}.txt",   # ← 바뀐 줄
            )
```

경제 이벤트가 이미 같은 방식(`f"v1/economic-event/{mode}.txt"`)을 쓰고 있어서 새로운 패턴이 아닙니다.

## 확인한 부작용 — 없음

| 확인 항목 | 결과 |
|---|---|
| `tests/test_prompting.py` | `asset_path`를 검증하지 않고 `mode`("economy-multiple-choice")만 봅니다. **영향 없음** |
| 패키징 (`pyproject.toml`) | hatchling이 `src/ecovillage_ai` 전체를 담습니다. `prompts/v1/economic-event/`가 이미 하위 폴더로 동작하므로 **설정 변경 불필요** |
| `prompt_version` | `quiz-draft-v1` 그대로 둡니다. 아래 참고 |
| 기존 `quiz-draft.txt` | 참조가 사라지지만 **1차에는 지우지 마세요.** 되돌리기가 쉬워집니다. 안정화 후 별도 커밋으로 삭제 |

## prompt_version을 올릴 것인가

내용이 크게 바뀌니 `quiz-draft-v2`로 올리고 싶어지지만, 올리면 **네 곳을 같이 고쳐야 합니다.**

| 파일 | 고칠 내용 |
|---|---|
| `prompting.py` | `SUPPORTED_PROMPT_VERSIONS`, `_selection_parts` 반환값 |
| `QuizDraftContract.java:10` | `PROMPT_VERSION` 상수 |
| `tests/fixtures/ai_mvp_release_v1.json:5-11` | `prompt_versions` 목록 |
| `tests/fixtures/quiz_draft_evaluation_v1.json` | `prompt_version` 필드 |

하나라도 빠뜨리면 [`evaluation.py:74`](../../ai-service/src/ecovillage_ai/evaluation.py)의
`prompt_versions != set(resolved["prompt_versions"])` 검사에 걸려 **출시 게이트 전체가 실패합니다.**

→ **권장: MVP 기간에는 `v1`을 유지**하고, 출시 뒤 프롬프트를 또 바꿀 때 한 번에 `v2`로 올립니다.

## 코드 없이도 되는 것

`prompting.py`를 못 고치는 동안에도 **모델만 바꾸는 건 `.env` 한 줄**입니다.

```bash
ECOVILLAGE_AI_ROUTE_QUIZ_DRAFT=OPENAI:gpt-5.4
```

골든셋 `run-b-model`이 바로 이 실행입니다. 코드 변경 0으로 모델 효과만 따로 잴 수 있습니다.

## 이 변경으로 해결되지 않는 것

프롬프트 분리는 넌센스 문제와 난이도 문제를 고치지만, 아래 두 가지는 남습니다.

| 남는 문제 | 위치 | 필요한 작업 |
|---|---|---|
| 개념이 주제 1개로 고정 → 문항 다양성 부족 | [`QuizDraftService.java:156`](../../backend/src/main/java/com/b201/ecovillage/ai/application/QuizDraftService.java) `List.of(command.topic())` | `concepts`를 여러 개 받기 (스키마는 이미 20개까지 허용) |
| 교사가 난이도를 못 고름 | [`QuizDraftService.java:348`](../../backend/src/main/java/com/b201/ecovillage/ai/application/QuizDraftService.java) 학년→난이도 하드코딩 | 요청에 `difficulty` 추가, 미입력 시 현재 자동값 유지 |

## 적용 순서

1. 팀원 develop 작업 종료 확인
2. 골든셋 `run-0-baseline` 먼저 측정 (프롬프트 바꾸기 **전**에 기준선 확보)
3. 위 코드 3곳 수정
4. `pytest`, `ruff check .` 통과 확인
5. `run-a-prompt` 측정 → 기준선과 블라인드 비교

2번을 건너뛰면 좋아졌는지 확인할 방법이 없어집니다.
자세한 순서는 [`QUIZ_GOLDEN_SET.md`](QUIZ_GOLDEN_SET.md)에 있습니다.
