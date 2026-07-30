# 이코빌리지 AI 서비스

Spring 핵심 API가 권한·집계·정책 범위를 확정한 뒤 호출하는 내부 FastAPI 생성
서비스입니다. 로컬·테스트에서는 결정적 fake provider로 계약을 재현하고, 운영에서는
Bedrock을 기본 실제 공급자로 사용하며 명시적으로 허용한 경우 OpenAI를 선택할 수 있습니다.

내부 계약은 schema v1.1입니다. `balancing` 한 경로에서 시작 전 설정 점검,
교사 요청 분석, 자동 모니터링을 모드로 구분하고, 경제 이벤트는 교사 요청과 모니터링
제안 경로 및 가상 자산 가격 단계 연결을 지원합니다. 퀴즈 초안은 난이도와 개념을 입력과
출력에서 함께 검증합니다.

기능·모드·학년별 프롬프트는 `src/ecovillage_ai/prompts`에서 버전으로 관리합니다.
공급자는 승인된 시스템 지침, 최소 신뢰 JSON, 출력 JSON Schema가 분리된 공통
`GenerationCommand`만 받습니다. 출력은 스키마부터 승인 경계까지 고정 순서로 검증하고
실행 로그에는 ID·버전·지연시간·토큰·검증 코드만 남깁니다. 상세 구조는
[`docs/ai/PROMPT_RUNTIME.md`](../docs/ai/PROMPT_RUNTIME.md)를 기준으로 합니다.
실제 공급자 설정과 오류·재시도 경계는
[`docs/ai/PROVIDER_ADAPTERS.md`](../docs/ai/PROVIDER_ADAPTERS.md)를 따릅니다.

## 제공 API

- `GET /healthz`
- `POST /internal/v1/generations/balancing`
- `POST /internal/v1/generations/teacher-chat`
- `POST /internal/v1/generations/economic-event`
- `POST /internal/v1/generations/quiz-draft`

모든 생성 API는 `Authorization: Bearer ...`, `X-Correlation-Id`,
`X-AI-Run-Id` 헤더가 필요합니다. 상세 계약은
[`contracts/ai-internal-openapi.yaml`](../contracts/ai-internal-openapi.yaml)을
기준으로 합니다.

## 로컬 실행

```bash
python -m venv .venv
.venv/Scripts/activate
python -m pip install -e ".[dev]"
uvicorn ecovillage_ai.main:app --reload --app-dir src
```

PowerShell에서는 가상 환경 활성화 명령으로
`.\.venv\Scripts\Activate.ps1`을 사용합니다. 기본 로컬 토큰은
`local-development-token`이며 운영에서는 반드시
`ECOVILLAGE_AI_INTERNAL_SERVICE_TOKEN`을 별도로 설정해야 합니다.

## 공급자 설정

- 로컬·테스트 기본값: `ECOVILLAGE_AI_PROVIDER=FAKE`
- 운영 기본값: `BEDROCK`; 모델 ID와 AWS 리전 필수
- OpenAI: `ECOVILLAGE_AI_OPENAI_ENABLED=true`, 모델 ID와 `OPENAI_API_KEY` 필수
- 기능별 경로: `ECOVILLAGE_AI_ROUTE_TEACHER_CHAT=OPENAI:모델ID` 형식

모델 ID, 전체 환경변수와 실제 공급자 smoke 실행법은
[`docs/ai/PROVIDER_ADAPTERS.md`](../docs/ai/PROVIDER_ADAPTERS.md)에 정리되어 있습니다.

## 테스트

```bash
pytest
ruff check .
```

계약 v1.1의 대표 성공 12건과 실패 7건은
[`tests/fixtures/contract_v1_1_cases.json`](tests/fixtures/contract_v1_1_cases.json)에
정리되어 있으며 테스트가 이 목록을 직접 실행합니다.

교사용 챗봇은 근거가 붙은 `ANSWER`와 근거를 만들지 않는 `LIMITED`를 구분합니다.
금지 질문은 외부 공급자 호출 전에 결정적 가드가 처리하며, 20개 기본 상황 × 3개 학년의
60개 평가 사례는
[`tests/fixtures/teacher_chat_evaluation_v1.json`](tests/fixtures/teacher_chat_evaluation_v1.json)에
정리되어 있습니다. 상세 동작은
[`docs/ai/TEACHER_CHAT.md`](../docs/ai/TEACHER_CHAT.md)를 따릅니다.

## 현재 경계

- 학생 원본·개인 식별 정보와 사용자 토큰을 받지 않습니다.
- 데이터베이스, 외부 URL, 파일, 브라우저와 업무 API를 호출하지 않습니다.
- 금액·확률·경제 효과를 새로 결정하거나 결과를 직접 승인·게시·적용하지 않습니다.
- 429/503 재시도는 Spring이 소유합니다. 이 서비스는 한 요청에서 공급자를 한 번만
  호출하고 재시도 가능 여부만 표준 오류에 표시합니다.
- 실제 공급자 SDK의 자동 재시도도 비활성화하며 공급자 원문 오류와 자격증명을 응답이나
  안전 실행 로그에 남기지 않습니다.
