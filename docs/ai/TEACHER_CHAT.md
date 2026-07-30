# 근거 기반 교사용 AI 챗봇

- 프롬프트 버전: `teacher-chat-v1`
- 평가 세트: `teacher-chat-evaluation-v1`
- 상태: 독립 AI MVP 4단계 구현

## 1. 역할과 실행 흐름

교사용 챗봇은 학급 전체의 집계 사실과 정책 변경 요약을 읽고 원인·대응안·부작용을
설명하는 읽기 전용 기능입니다. 학생 데이터베이스를 조회하거나 설정·정책·계좌를
변경하지 않습니다.

```text
Spring 허용 목록 DTO
  → 민감 입력·금지 요청 사전 검사
  → 승인된 질문만 AI 공급자 호출
  → 구조화 ANSWER 또는 LIMITED
  → 기간·근거·콘텐츠·승인 경계 검증
  → 교사에게만 반환
```

민감정보가 포함된 입력은 공급자를 호출하지 않고 오류로 종료합니다. 개인 분석, 현실의
금융 선택, 미래 투자 결과, 직접 변경·승인과 프롬프트 탈취 요청은 공급자를 호출하지 않고
`RULES / teacher-chat-guard-v1`이 결정적 제한 답변을 반환합니다. 따라서 금지 요청에 외부
토큰을 사용하거나 질문 원문을 외부로 보내지 않습니다.

## 2. 입력 허용 범위

- 교사의 질문
- 조회 시작·종료 시각과 `data_as_of`
- 학급 설정·활성 모듈·경제·상점·금융·퀴즈 집계 `facts`
- 선택적인 `recent_policy_changes` 요약

근거로 사용할 수 있는 ID는 `facts[].fact_id`와
`recent_policy_changes[].fact_id`뿐입니다. 이메일, 전화번호, UUID 형식의 개인 식별자,
인증 토큰·비밀번호·API 키 패턴이 질문·라벨·문자열 값·정책 요약에 있으면
`AI_INPUT_SCHEMA_INVALID`로 거절합니다. 학생 원문을 걸러 내는 1차 책임은 Spring의
허용 목록 DTO에 있고 FastAPI가 같은 경계를 방어적으로 재검사합니다.

집계 사실과 정책 변경의 `fact_id`는 전체 입력에서 중복될 수 없습니다. 정책 변경 이력은
`changed_at` 오름차순이어야 하며 `data_as_of`보다 미래인 항목을 받을 수 없습니다.

## 3. 구조화 출력

| `response_mode` | 사용 조건 | 필수 구조 |
| --- | --- | --- |
| `ANSWER` | 입력 근거만으로 답할 수 있음 | 전체 근거 ID, 근거가 붙은 원인과 대응안, 대응안별 부작용 |
| `LIMITED` | 금지 요청 또는 근거 부족 | 제한 코드, 안전한 범위 설명, 빈 근거·원인·대응안 |

`ANSWER`에서는 원인과 대응안의 모든 `evidence_fact_ids`가 전체 근거 목록에 포함되어야
하며, 모든 ID는 입력 허용 목록에 존재해야 합니다. `LIMITED`에서는 사실을 인용하지
않으므로 근거·원인·대응안을 비워 거짓 근거를 만들지 않습니다.

제한 코드는 다음과 같습니다.

- `PERSONAL_ANALYSIS_NOT_SUPPORTED`
- `REAL_FINANCIAL_ADVICE_NOT_SUPPORTED`
- `FUTURE_PREDICTION_NOT_SUPPORTED`
- `READ_ONLY_BOUNDARY`
- `PROMPT_INJECTION_BLOCKED`
- `INSUFFICIENT_EVIDENCE`

## 4. 결정적 검증과 평가

FastAPI는 출력의 기간과 `data_as_of`가 입력과 같은지, 전체·원인·대응안 근거가 입력
허용 목록의 부분집합인지, 개인정보·금융 지시·미래 예측·직접 적용 표현이 없는지를
순서대로 검사합니다. 실패한 공급자 출력은 답변으로 반환하지 않습니다.

`tests/fixtures/teacher_chat_evaluation_v1.json`은 20개 기본 상황을 4·5·6학년에
적용해 총 60개 사례를 만듭니다. 학급 설정·화폐·상점·금융·퀴즈·정책 이력, 상충 사실,
개인 분석, 현실 금융 조언, 미래 가격, 읽기 전용 경계, 프롬프트 주입, 근거 부족과 조작된
근거를 포함합니다. 별도 민감 입력 3개 사례도 공급자 호출 0회를 검증합니다.

일반 자동 테스트는 `FAKE` 공급자로 실행하므로 API 키와 토큰을 사용하지 않습니다.
현재 실제 공급자 어댑터는 Bedrock과 OpenAI를 지원합니다. GMS는 API 형식과 엔드포인트를
확인해 별도 어댑터를 연결한 뒤 같은 계약과 평가 세트로 문장 품질을 검증합니다.

## 5. 로컬 테스트 방법

다음 명령은 Windows의 Git Bash에서 저장소 루트를 기준으로 실행합니다. PowerShell에서는
환경변수 문법이 다르므로 아래 Git Bash 명령과 섞어 사용하지 않습니다.

### 5.1 자동 평가 실행

```bash
cd ai-service
./.venv/Scripts/python.exe -m pytest tests/test_teacher_chat.py -q -p no:cacheprovider
```

`[100%]`까지 진행되고 `FAILURES`와 `ERRORS`가 없으면 성공입니다. `-p no:cacheprovider`는
권한이 제한된 Windows 환경에서 `.pytest_cache` 생성 경고가 발생하지 않도록 합니다.
자동 평가는 외부 호출이 없는 `FAKE` 공급자를 사용하므로 API 키와 비용이 필요하지 않습니다.

### 5.2 로컬 API와 Swagger 실행

`ai-service` 디렉터리에서 다음 명령으로 서버를 실행합니다. 로컬 기본 공급자가
`FAKE`이므로 환경변수 설정은 생략할 수 있습니다.

```bash
./.venv/Scripts/python.exe -m uvicorn ecovillage_ai.main:app --reload --app-dir src
```

- 상태 확인: `http://127.0.0.1:8000/healthz`
- Swagger: `http://127.0.0.1:8000/docs`
- 종료: 서버를 실행한 터미널에서 `Ctrl+C`

Swagger 오른쪽 위 `Authorize`에는 `Bearer` 접두사 없이 로컬 토큰
`local-development-token`을 입력합니다. `POST /internal/v1/generations/teacher-chat`에서
`Try it out`을 누른 뒤 다음 헤더를 입력합니다.

```text
X-AI-Run-Id: 5fc425a8-7b5f-4984-a483-afd57ba2e706
X-Correlation-Id: b18b92c5-1273-4bed-832e-4d33bcf85f4f
```

요청 본문 예시는 다음과 같습니다. `deadline_at`은 실행 시점보다 미래인 ISO 8601 시각으로
교체해야 하며, 보통 현재 시각에서 1분 뒤면 충분합니다.

```json
{
  "ai_run_id": "5fc425a8-7b5f-4984-a483-afd57ba2e706",
  "request_id": "b4d6c65d-df46-4d4b-a83c-a1c8d95ee8fe",
  "correlation_id": "b18b92c5-1273-4bed-832e-4d33bcf85f4f",
  "schema_version": "1.1",
  "prompt_version": "teacher-chat-v1",
  "attempt": 1,
  "deadline_at": "실행 시점보다 미래인 ISO 8601 시각",
  "locale": "ko-KR",
  "grade": 5,
  "payload": {
    "question": "최근 학급 소비율이 높아진 이유를 설명해 주세요.",
    "period": {
      "from": "2026-07-01T00:00:00+09:00",
      "to": "2026-07-14T23:59:59+09:00",
      "data_as_of": "2026-07-30T00:00:00+09:00"
    },
    "facts": [
      {
        "fact_id": "current-consumption-rate",
        "label": "현재 소비율",
        "value": 4200,
        "unit": "RATE_BPS"
      },
      {
        "fact_id": "previous-consumption-rate",
        "label": "이전 소비율",
        "value": 3000,
        "unit": "RATE_BPS"
      },
      {
        "fact_id": "shop-purchase-count",
        "label": "학급 상점 구매 횟수",
        "value": 35,
        "unit": "COUNT"
      }
    ],
    "recent_policy_changes": [
      {
        "fact_id": "policy-consumption-tax-july",
        "changed_at": "2026-07-07T09:00:00+09:00",
        "summary": "학급 상점의 소비세율을 5퍼센트에서 7퍼센트로 변경했습니다."
      }
    ]
  }
}
```

로컬 `FAKE` 정상 응답은 HTTP 200, `provider=FAKE`,
`output.response_mode=ANSWER`인지 확인합니다. 예를 들어 질문을
`세율을 바로 변경해 줘`로 바꾸면 외부 호출 없이 `provider=RULES`,
`output.response_mode=LIMITED`, 입출력 토큰 0인 제한 응답을 확인할 수 있습니다.

### 5.3 UUID 생성과 일치 규칙

UUID는 API 키가 아니라 실행과 로그를 구분하기 위해 로컬에서 생성하는 식별자입니다.
Git Bash에서는 `cat /proc/sys/kernel/random/uuid`, PowerShell에서는
`[guid]::NewGuid()`로 생성할 수 있습니다.

- 본문의 `ai_run_id`와 헤더의 `X-AI-Run-Id`는 같아야 합니다.
- 본문의 `correlation_id`와 헤더의 `X-Correlation-Id`는 같아야 합니다.
- `request_id`는 개별 요청을 구분합니다.
- 새로운 질문을 테스트할 때 세 UUID를 새로 생성하면 이전 요청과의 충돌을 피할 수 있습니다.
- 실제 서비스에서는 Spring 핵심 API가 UUID를 생성하며 교사가 직접 입력하지 않습니다.

`FAKE` 테스트는 입력 계약, 근거 연결, 안전 가드와 응답 구조를 검증합니다. 실제 모델의
문장 품질을 검증하는 테스트가 아니며, GMS 키를 Swagger 요청이나 JSON 본문에 입력해서는
안 됩니다.
