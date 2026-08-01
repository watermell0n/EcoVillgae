# AI 경제 설정 추천·시작 전 점검

- 프롬프트 버전: `setup-precheck-v1`
- 평가 세트: `setup-precheck-evaluation-v1`
- 상태: Spring 규칙 엔진·FastAPI 계약 v1.2 실행 흐름 통합

## 1. 역할과 책임

Spring 규칙 엔진이 선택한 모듈의 급여·세율·금리·가격·보상 후보와 추천·허용 범위, 시작 전
`PASS·WARN·BLOCK` 판정을 계산합니다. AI 서비스는 그 결과를 교사가 이해하기 쉬운 말로
설명할 뿐, 수치와 판정을 계산·변경·저장·적용하지 않습니다.

규칙 엔진이 이 후보값·허용 범위·판정을 어떤 기준값·공식으로 계산하는지는
[규칙 엔진 추천·판정 상세 명세](RULE_ENGINE_SPEC.md)에서 확정합니다.

| 설정 문맥 | 규칙 엔진이 전달하는 후보 |
| --- | --- |
| `CLASSROOM_SETUP` | 수치 후보 없음. 통화명·실제 운영기간 등 모든 방의 공통값만 점검 |
| `MODULE_ACTIVATION` | 활성화하려는 역할 급여, 소득세율, 예금금리 또는 대출금리 |
| `PRODUCT_DRAFT` | 교사가 등록 중인 상품 가격 |
| `QUIZ_SESSION_DRAFT` | `GAME` 퀴즈 보상 |

필수 선택 모듈은 없으며 역할·급여도 선택형입니다. 비활성 모듈의 후보를 전달하지 않고,
그 모듈의 값이나 학생 직업이 없다는 이유로 경고하지 않습니다. `TEST` 퀴즈에는 보상 후보를
전달하지 않습니다. 모든 성공 결과는 `REVIEW_REQUIRED`이며 교사가 검토하기 전에는 어떤
설정도 반영되지 않습니다. 초기 정책이 필요한 모듈은 검토된 값 저장과 활성화를 원자적으로
처리합니다.

## 2. 입력·출력 보존 규칙

요청은 `POST /internal/v1/generations/balancing`에
`analysis_mode=SETUP_PRECHECK`, `trigger_type=TEACHER_REQUEST`로 보냅니다.

- 후보마다 `action_id`, `target_type`, `label`, `proposed_value`,
  `recommended_min_value`, `recommended_max_value`, `allowed_min_value`,
  `allowed_max_value`, `unit`, `reason_codes`를 전달합니다.
- `CLASSROOM_SETUP`에는 수치 후보가 없습니다. 모듈 활성화 후보의 `target_id`는
  `context.module_id`와 같아야 하고 상품·퀴즈 후보도 대상 UUID가 필요합니다.
- 점검마다 `check_id`, `severity`, `reason_code`, `subject_action_ids`, `message`를 전달합니다.
- 응답의 후보 값·범위·단위·대상·표시 이름·근거 코드는 입력과 정확히 같아야 합니다.
- 응답의 점검 심각도·근거 코드·대상 action 목록도 입력과 정확히 같아야 합니다.
- `overall_severity`는 `BLOCK > WARN > PASS` 순으로 가장 높은 입력 점검 심각도입니다.
- `PASS`에는 수정 안내가 없어야 하고, `WARN·BLOCK`에는 수정 안내가 있어야 합니다.
- `WARN·BLOCK`이 가리키는 후보에는 주의점이 있어야 합니다.

후보나 점검을 누락·추가하거나 값을 변조하면 전체 응답을 실패 처리합니다. 자동 저장·승인·
적용을 지시하는 표현도 성공 결과로 반환하지 않습니다.

## 3. 공급자 호출 전 검사

상품 표시 이름과 규칙 엔진 메시지에 다음 내용이 섞이면 모델을 호출하지 않고 422로
종료합니다.

- UUID·이메일·전화번호 같은 개인 식별 가능 정보
- 비밀번호·토큰·API key 같은 비밀값
- 이전 지시 무시, 시스템 프롬프트 공개 같은 프롬프트 주입

문맥 조합도 Pydantic 계약에서 먼저 검사합니다. `TEST`에 보상 후보가 있거나,
학급 기본 설정·모듈 활성화·개별 대상 UUID의 관계가 맞지 않으면 모델을 호출하지 않습니다.

## 4. 자동 평가

평가 fixture는
[`setup_precheck_evaluation_v1.json`](../../ai-service/tests/fixtures/setup_precheck_evaluation_v1.json)에
있습니다.

- 성공 36개: 3개 학년 × 12개 기본 상황
- 실패 12개: 3개 학년 × 값 변경·범위 변경·심각도 변경·자동 적용 우회 4종
- 합계 48개

기본 상황에는 모듈 없음 `PASS`, 모듈 활성화 `PASS·WARN·BLOCK`, 상품 가격
`PASS·WARN·BLOCK`, GAME 보상 `PASS·WARN·BLOCK`, 역할 없는 학생, TEST 무보상이 포함됩니다.

Windows PowerShell 또는 Git Bash에서 AI 서비스 폴더로 이동한 뒤 실행합니다.

```bash
./.venv/Scripts/python.exe -m pytest tests/test_setup_precheck.py -q -p no:cacheprovider
```

전체 테스트가 통과하면 48개 평가 사례와 추가 입력·출력 안전 검사가 모두 통과한 것입니다.

## 5. Swagger에서 직접 테스트

### 5.1 서버 실행

Git Bash에서는 `python` 명령 대신 가상 환경의 실행 파일을 직접 사용합니다.

```bash
cd /c/Users/SSAFY/Desktop/S15P11B201/ai-service
export ECOVILLAGE_AI_PROVIDER=FAKE
./.venv/Scripts/python.exe -m uvicorn ecovillage_ai.main:app --reload --app-dir src
```

PowerShell에서는 환경변수 설정만 다음처럼 바꿉니다.

```powershell
$env:ECOVILLAGE_AI_PROVIDER = 'FAKE'
./.venv/Scripts/python.exe -m uvicorn ecovillage_ai.main:app --reload --app-dir src
```

브라우저에서 `http://127.0.0.1:8000/docs`를 열고
`POST /internal/v1/generations/balancing`의 **Try it out**을 누릅니다.

### 5.2 인증과 헤더

Swagger의 **Authorize**에는 `local-development-token`만 입력합니다. 요청 헤더에는 서로
다른 UUID를 넣되, `X-AI-Run-Id`는 본문 `ai_run_id`, `X-Correlation-Id`는 본문
`correlation_id`와 같게 입력합니다. UUID는 직접 정한 임의 숫자가 아니라 표준 형식의
고유 식별자이며 PowerShell에서 `[guid]::NewGuid()`로 만들 수 있습니다.

### 5.3 요청 예시

`deadline_at`은 요청 시점보다 5분 뒤의 UTC 시각으로 바꿉니다. PowerShell에서는
`(Get-Date).ToUniversalTime().AddMinutes(5).ToString('o')`로 만들 수 있습니다.

```json
{
  "ai_run_id": "11111111-1111-4111-8111-111111111111",
  "request_id": "22222222-2222-4222-8222-222222222222",
  "correlation_id": "33333333-3333-4333-8333-333333333333",
  "schema_version": "1.2",
  "prompt_version": "setup-precheck-v1",
  "attempt": 1,
  "deadline_at": "2026-07-30T15:00:00.000Z",
  "locale": "ko-KR",
  "grade": 5,
  "analysis_mode": "SETUP_PRECHECK",
  "trigger_type": "TEACHER_REQUEST",
  "payload": {
    "context": {
      "context_type": "PRODUCT_DRAFT",
      "course_scale": "ONE_MONTH",
      "expected_students": 24,
      "operation_period_days": 30,
      "active_modules": ["SHOP"],
      "module_code": null,
      "module_id": null,
      "quiz_mode": null
    },
    "candidates": [
      {
        "action_id": "product-price-default",
        "target_type": "PRODUCT_PRICE",
        "target_id": "44444444-4444-4444-8444-444444444444",
        "proposed_value": 300,
        "recommended_min_value": 255,
        "recommended_max_value": 345,
        "allowed_min_value": 1,
        "allowed_max_value": 500,
        "unit": "AMOUNT",
        "reason_codes": ["SALARY_PRICE_BALANCE"],
        "label": "학급 상점 연필 가격"
      }
    ],
    "checks": [
      {
        "check_id": "required-values",
        "severity": "PASS",
        "reason_code": "REQUIRED_VALUES_PRESENT",
        "subject_action_ids": ["product-price-default"],
        "message": "필수 설정값이 준비되었습니다."
      }
    ]
  }
}
```

정상 응답은 HTTP 200이며 `review_status=REVIEW_REQUIRED`,
`context_type=PRODUCT_DRAFT`, `overall_severity=PASS`를 반환합니다. 후보의 300, 추천
255~345, 허용 1~500, `AMOUNT`, `SALARY_PRICE_BALANCE`도 입력과 같아야 하며 PASS 점검의
`correction_guidance`는 `null`입니다.

`severity`를 `WARN`으로 바꾸고 경고용 `reason_code`와 `message`를 넣으면
`overall_severity=WARN`, 수정 안내와 후보 주의점이 포함되는 흐름을 확인할 수 있습니다.
FAKE 공급자는 실제 API key나 토큰을 소비하지 않습니다. 실제 Bedrock·OpenAI 문장 품질
확인은 자격증명을 연결한 뒤 별도 opt-in smoke 테스트로 수행합니다.

## 6. Spring 통합 동작

Spring의 `POST /classrooms/{classroom_id}/ai/balancing-analysis-operations`는 규칙 엔진
결과를 먼저 확정한 뒤 FastAPI에 설명을 요청합니다. FastAPI 응답의 후보값, 추천·허용
범위, 단위, 판정, 대상과 사유 코드가 원본과 다르면 응답 전체를 폐기하고 Spring 규칙
결과로 fallback 설명을 만듭니다. timeout, 인증 실패, 429·5xx와 잘못된 출력도 같은
fallback 경계로 처리하므로 설명 서비스 장애가 설정 점검 숫자를 잃게 만들지 않습니다.

실행 결과는 `async_operations`, `ai_runs`, `ai_artifacts`, `ai_proposals`에 저장됩니다.
교사는 제안을 확인·거절·적용할 수 있지만 확인만으로 정책은 바뀌지 않습니다. 적용할 때는
Spring이 최신 정책 버전, 후보 허용 범위, 모듈 상태와 규칙 엔진 action을 다시 검증하고
기존 모듈 활성화 서비스가 초기 정책 저장과 `DISABLED → ACTIVE`를 같은 트랜잭션에서
수행합니다. 같은 멱등 키의 실행·결정 요청은 기존 결과를 재생합니다.
