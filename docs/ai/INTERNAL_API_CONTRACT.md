# 이코빌리지 내부 AI API 계약

- 버전: v1.0
- 기준일: 2026-07-30
- 상태: MVP 구현 기준선
- 호출 관계: Spring 핵심 API·비동기 작업자 → FastAPI AI 서비스
- 기계 판독 계약: [`contracts/ai-internal-openapi.yaml`](../../contracts/ai-internal-openapi.yaml)

## 1. 목적과 책임

외부 `/api/v1` API는 Spring 핵심 API가 소유합니다. FastAPI는 외부 사용자 토큰을
받지 않으며, 핵심 API가 권한·학급 범위·AI 설정·사용량과 입력 최소화를 확인한 뒤
만든 구조화 요청만 처리합니다.

| 책임 | Spring 핵심 API | FastAPI AI 서비스 |
| --- | --- | --- |
| 사용자 인증·OWN/COT 권한 | 최종 판정 | 사용자 토큰을 받지 않음 |
| 학급·학교 AI 활성화 | 최종 판정 | `AI_DISABLED` 우회 불가 |
| 일일 30회 제한 | 교사·일자별 원자적 차감 | 적용 결과를 신뢰 |
| 원본 조회·집계·수치 계산 | 수행 | DB를 직접 조회하지 않음 |
| 임계값·허용 범위·경제 효과 | 계산·버전 고정 | 입력 범위를 벗어나지 않게 검증 |
| 모델 호출·프롬프트 | 호출하지 않음 | 공급자 어댑터와 프롬프트 버전 관리 |
| 구조화 출력·안전검사 | 도메인 규칙으로 재검증 | Pydantic·콘텐츠 정책으로 1차 검증 |
| AI 실행·산출물 저장 | `ai_runs`, `ai_artifacts` 등에 저장 | 업무 DB에 쓰지 않음 |
| 승인·거절·정책 적용 | 수행 | 직접 적용하지 않음 |

## 2. 실행 방식

### 2.1 동기 호출

교사용 챗봇은 외부 `API-AI-001` 요청 안에서 내부
`POST /internal/v1/generations/teacher-chat`을 호출합니다.

- 외부 요청 전체 제한은 20초입니다.
- 핵심 API는 FastAPI에 절대 시각 `deadline_at`을 전달합니다.
- 자동 재시도는 전체 호출 경로에서 최대 1회입니다.
- 양쪽 계층이 각각 재시도해 총 재시도가 늘어나지 않도록 핵심 API가 재시도를
  소유하고 FastAPI 공급자 어댑터는 전달된 시도 번호를 사용합니다.
- 시간 초과 후에는 기존 집계 화면과 수동 운영 경로를 제공합니다.

### 2.2 비동기 호출

밸런싱·경제 이벤트 초안·퀴즈 초안은 다음 순서로 처리합니다. 외부 계약에서도 경제 이벤트
초안 생성 `API-AI-004`와 교사 결정 `API-AI-006`을 분리합니다.

1. 핵심 API가 `async_operations`와 `ai_runs`를 생성합니다.
2. 작업 메시지에는 원본 학생 데이터가 아니라 `ai_run_id`와 집계 입력 참조만 둡니다.
3. 권한 있는 내부 작업자가 입력 스냅샷을 구성해 FastAPI를 호출합니다.
4. FastAPI가 구조화 산출물과 검증 메타데이터를 반환합니다.
5. 핵심 API가 최신 정책·상태로 다시 검증하고 산출물을 저장합니다.
6. 성공·실패 상태를 `API-READ-030`과 `WS-OPS-001`로 알립니다.

로컬 구현이 SQS 없이 시작되더라도 애플리케이션 서비스는 같은 작업 인터페이스를
사용합니다. 운영 비동기 전달은 SQS로 교체하고 FastAPI의 생성 계약은 바꾸지 않습니다.

## 3. 인증과 공통 헤더

| 항목 | 규칙 |
| --- | --- |
| `Authorization` | 대상 서비스와 scope가 제한된 짧은 만료 Bearer 서비스 토큰 |
| `X-Correlation-Id` | 외부 요청부터 DB·AI 공급자까지 이어지는 추적 UUID |
| `X-AI-Run-Id` | 핵심 API가 먼저 생성한 AI 실행 UUID, 본문 값과 같아야 함 |
| `Content-Type` | `application/json` |

허용 scope는 기능별로 `ai:generate:balancing`, `ai:generate:teacher-chat`,
`ai:generate:economic-event`, `ai:generate:quiz-draft`를 사용합니다. 사용자 액세스 토큰,
학생 세션 토큰과 공용 화면 토큰은 내부 AI API에서 거절합니다.

## 4. 공통 요청 envelope

```json
{
  "ai_run_id": "5fc425a8-7b5f-4984-a483-afd57ba2e706",
  "request_id": "b4d6c65d-df46-4d4b-a83c-a1c8d95ee8fe",
  "correlation_id": "b18b92c5-1273-4bed-832e-4d33bcf85f4f",
  "schema_version": "1.0",
  "prompt_version": "balancing-v1",
  "attempt": 1,
  "deadline_at": "2026-07-30T05:30:20.000Z",
  "locale": "ko-KR",
  "grade": 5,
  "payload": {}
}
```

- `ai_run_id`와 `request_id`는 핵심 API가 생성합니다.
- `attempt`는 1 또는 2이며 2를 넘으면 거절합니다.
- `deadline_at`이 지났거나 남은 시간이 모델 호출에 부족하면 모델을 호출하지 않고
  `AI_DEADLINE_EXCEEDED`를 반환합니다.
- 선언되지 않은 최상위 필드와 payload 필드는 거절합니다.
- `classroom_id`, 학생 ID, 닉네임과 실명은 생성 payload에 넣지 않습니다.
- 학년은 4·5·6만 허용합니다.

## 5. 공통 성공 envelope

```json
{
  "ai_run_id": "5fc425a8-7b5f-4984-a483-afd57ba2e706",
  "schema_version": "1.0",
  "prompt_version": "balancing-v1",
  "provider": "BEDROCK",
  "model_id": "configured-model-id",
  "safety_status": "PASSED",
  "validation_codes": [],
  "usage": {
    "input_tokens": 1200,
    "output_tokens": 420
  },
  "output": {}
}
```

- 비용은 공급자 사용량과 운영 가격표를 이용해 핵심 API 또는 운영 집계 계층에서
  계산합니다. 모델이 비용을 생성하지 않습니다.
- `provider`, `model_id`, 토큰 수와 검증 코드는 메타데이터이며 학생에게 공개하지
  않습니다.
- `safety_status`가 `PASSED`가 아니면 핵심 API는 산출물을 게시·적용 후보로 저장하지
  않습니다.

## 6. 기능별 계약

### 6.1 경제 밸런싱

`POST /internal/v1/generations/balancing`

입력:

- 분석 기간과 `data_as_of`
- 현재·직전 동일 길이 분석 구간의 지표
- 학생 수와 관련 거래 수
- 규칙 엔진이 계산한 경고 코드
- 조정 대상별 현재값·최솟값·최댓값·단위

출력:

- 전체 요약
- 근거 지표 코드
- 원인 목록
- 우선순위가 있는 조정 제안
- 예상 효과와 부작용

제안은 입력으로 받은 `action_id`와 정수 `proposed_value`만 참조합니다. 새로운 대상,
금액 단위, 정책 종류 또는 허용 범위를 만들 수 없습니다. 최소 표본 판정은 모델 호출
전에 핵심 API가 수행하고 FastAPI도 같은 값을 방어적으로 검사합니다.

### 6.2 교사용 챗봇

`POST /internal/v1/generations/teacher-chat`

입력:

- 교사 질문
- 조회 기간과 데이터 갱신 시각
- 허용 목록에 포함된 학급 설정·집계 사실
- 선택한 경우의 정책 변경 이력 요약

출력:

- 답변
- 답변에 사용한 근거 코드
- 원인·대응안·부작용
- 조회 기간과 데이터 갱신 시각

근거 코드는 입력 `facts[].fact_id`만 참조할 수 있습니다. 존재하지 않는 근거, 개인
학생 분석, 실제 금융 조언과 미래 투자 가격 예측이 포함되면 거절합니다.

### 6.3 경제 이벤트

`POST /internal/v1/generations/economic-event`

템플릿은 다음 7개로 고정합니다.

```text
PRICE | SUPPLY | TAX | INTEREST_RATE | INVESTMENT_NEWS | REWARD | PUBLIC_FUND
```

`INTEREST_RATE`는 `DEPOSIT`, `SAVINGS`, `LOAN` scope를 갖습니다. 초4는
`PRICE·SUPPLY·PUBLIC_FUND`, 초5는 여기에 `TAX`와 예금·적금 금리를, 초6은 전체
템플릿과 대출금리를 사용할 수 있습니다.

핵심 API가 수치 효과를 `effect_summary`라는 설명용 읽기 전용 데이터로 전달합니다.
FastAPI 출력에는 효과 수치나 정책 변경 명령을 포함하지 않고 제목·이야기·선택지·진행
문구만 포함합니다.

### 6.4 퀴즈 초안

`POST /internal/v1/generations/quiz-draft`

입력은 학년, 주제, 학습목표, `ECONOMY·NONSENSE`, `MULTIPLE_CHOICE·OX`, 문항 수로
제한합니다. 출력 문항은 문제, 선택지, 정답 key, 해설, 난이도와 개념 태그를
포함합니다.

- 객관식은 3~5개 선택지와 정확히 하나의 정답을 가집니다.
- OX는 `O`, `X` 두 선택지와 정확히 하나의 정답을 가집니다.
- 문항 수는 1~30입니다.
- 모든 AI 문항의 저장 시작 상태는 `REVIEW_REQUIRED`입니다.
- 파일·시험지·URL 업로드와 단답형·서술형은 받지 않습니다.

## 7. 오류 계약

```json
{
  "error": {
    "code": "AI_OUTPUT_SCHEMA_INVALID",
    "message": "AI 출력이 구조화 응답 계약을 충족하지 않습니다.",
    "retryable": false,
    "validation_codes": ["QUIZ_MULTIPLE_CORRECT_ANSWERS"]
  },
  "ai_run_id": "5fc425a8-7b5f-4984-a483-afd57ba2e706",
  "correlation_id": "b18b92c5-1273-4bed-832e-4d33bcf85f4f"
}
```

| HTTP | 코드 | 재시도 |
| ---: | --- | --- |
| 400 | `AI_INPUT_SCHEMA_INVALID` | 아니요 |
| 403 | `AI_SERVICE_SCOPE_VIOLATION` | 아니요 |
| 409 | `AI_RUN_CONTEXT_MISMATCH` | 아니요 |
| 422 | `AI_INSUFFICIENT_DATA`, `AI_OUTPUT_SCHEMA_INVALID`, `AI_OUTPUT_SAFETY_REJECTED` | 아니요 |
| 429 | `AI_PROVIDER_RATE_LIMITED` | 남은 deadline 안에서 핵심 API가 1회 판단 |
| 503 | `AI_PROVIDER_UNAVAILABLE` | 남은 deadline 안에서 핵심 API가 1회 판단 |
| 504 | `AI_DEADLINE_EXCEEDED` | 아니요 |

내부 오류 응답에 공급자 원문, 프롬프트, 토큰, 자격증명과 학생 정보를 포함하지
않습니다.

## 8. 멱등성·저장·보존

- 핵심 API가 `ai_run_id`의 상태와 단일 산출물 저장을 보장합니다.
- FastAPI 호출은 학생 계좌나 업무 DB에 부작용이 없는 생성 요청입니다.
- 재시도 결과가 둘 이상 도착하면 핵심 API는 먼저 검증·확정한 결과만 저장하고 나머지는
  중복 시도로 감사합니다.
- 챗봇 질문·답변 원문은 생성 후 30일, 실행 메타데이터는 실행 후 1년 보관합니다.
- FastAPI 애플리케이션 로그에는 질문·답변·프롬프트·전체 payload를 남기지 않습니다.

## 9. 계약 변경 절차

1. 제품 정책은 `docs/AI_DIRECTION.md`를 먼저 변경합니다.
2. 이 문서와 `contracts/ai-internal-openapi.yaml`을 함께 변경합니다.
3. Pydantic 모델과 Spring 내부 DTO의 계약 테스트를 갱신합니다.
4. 프롬프트·스키마 버전의 호환성을 판정합니다.
5. 외부 필드가 바뀌면 `docs/HTTP_API_SPEC.md`와 생성된 `contracts/openapi.yaml`,
   `contracts/api-field-mapping.json`을 함께 갱신합니다.

## 10. 다음 계약 버전 반영 항목

아래 항목은 2026-07-30 제품 결정으로 확정했지만 현재 기계 판독 계약 v1.0에는 아직
포함되지 않았습니다. 기능 코드를 추가하기 전에 v1.1로 올리고 이 문서·OpenAPI·Pydantic·
Spring DTO를 한 커밋에서 함께 변경합니다.

### 10.1 설정 추천·자동 모니터링

- 밸런싱 요청에 `analysis_mode=SETUP_PRECHECK|ON_DEMAND|AUTO_MONITOR`를 추가합니다.
- `SETUP_PRECHECK` 입력은 학년·코스·예상 학생 수·활성 모듈과 Spring이 계산한
  급여·세율·예금·대출금리·상품 가격·GAME 보상 후보로 제한합니다.
- 규칙 기반 숫자와 BLOCK·WARN 판정만 필요하면 FastAPI를 호출하지 않습니다. 설명을
  요청할 때도 FastAPI는 입력 `action_id`, 후보값과 허용 범위를 바꿀 수 없습니다.
- `AUTO_MONITOR`의 NORMAL은 FastAPI를 호출하지 않고 Spring에서 완료합니다.
  OBSERVE·WARN만 현재 밸런싱 생성 계약을 사용합니다.
- 요청 envelope에는 분석 트리거와 구간을 추가하고, 자동 실행은 사용자 ID 대신 시스템
  행위와 스케줄 멱등 키를 Spring 저장 메타데이터에 기록합니다.

### 10.2 이벤트 출처·투자 뉴스 연결

- 경제 이벤트 입력에 `source_type=TEACHER_REQUEST|MONITOR_RECOMMENDATION`과 선택적인
  `source_proposal_id`를 추가합니다.
- `INVESTMENT_NEWS`에는 `scenario_version_id`, `asset_id`, `price_step_id`, 현재 단계까지의
  공개 가능한 가격 사실을 전달합니다. 다음 단계 가격과 다른 미공개 뉴스는 전달하지 않습니다.
- FastAPI는 기업·재무정보·가격·수익률을 생성하지 않고 연결된 단계의 제목·뉴스 본문·
  교사용 설명만 반환합니다.
- 핵심 API는 시장 시작 전에 단계별 생성 작업을 실행하고 교사 승인 상태를 저장하며,
  학생 조회에는 가격 단계 공개 이후의 승인 뉴스만 포함합니다.
