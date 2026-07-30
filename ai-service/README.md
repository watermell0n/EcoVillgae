# 이코빌리지 AI 서비스

Spring 핵심 API가 권한·집계·정책 범위를 확정한 뒤 호출하는 내부 FastAPI 생성
서비스입니다. 현재 단계에서는 실제 모델 대신 결정적 fake provider를 사용해 계약,
안전 경계, 오류 처리를 검증합니다.

내부 계약은 schema v1.1입니다. `balancing` 한 경로에서 시작 전 설정 점검,
교사 요청 분석, 자동 모니터링을 모드로 구분하고, 경제 이벤트는 교사 요청과 모니터링
제안 경로 및 가상 자산 가격 단계 연결을 지원합니다. 퀴즈 초안은 난이도와 개념을 입력과
출력에서 함께 검증합니다.

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

## 테스트

```bash
pytest
ruff check .
```

계약 v1.1의 대표 성공 12건과 실패 7건은
[`tests/fixtures/contract_v1_1_cases.json`](tests/fixtures/contract_v1_1_cases.json)에
정리되어 있으며 테스트가 이 목록을 직접 실행합니다.

## 현재 경계

- 학생 원본·개인 식별 정보와 사용자 토큰을 받지 않습니다.
- 데이터베이스, 외부 URL, 파일, 브라우저와 업무 API를 호출하지 않습니다.
- 금액·확률·경제 효과를 새로 결정하거나 결과를 직접 승인·게시·적용하지 않습니다.
- 429/503 재시도는 Spring이 소유합니다. 이 서비스는 한 요청에서 공급자를 한 번만
  호출하고 재시도 가능 여부만 표준 오류에 표시합니다.
- Bedrock 등 실제 공급자 연동은 다음 구현 단계에서 추가합니다.
