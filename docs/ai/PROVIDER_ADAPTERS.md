# AI 실제 공급자 어댑터

- 기준일: 2026-07-30
- 상태: 독립 AI MVP 3단계 구현 완료
- 적용 계약: 내부 AI schema v1.2

## 1. 선택 원칙

- 로컬·테스트의 기본 공급자는 외부 호출이 없는 `FAKE`입니다.
- 운영 환경에서 공급자를 생략하면 `BEDROCK`을 선택합니다.
- `OPENAI`는 `ECOVILLAGE_AI_OPENAI_ENABLED=true`를 명시한 경우에만 선택합니다.
- 기능별 경로가 없으면 전체 기본 공급자와 모델을 사용합니다.
- 모델 ID는 코드 기본값으로 고정하지 않고 배포 환경에서 명시합니다.

기능별 경로는 `공급자:모델ID` 형식입니다. 모델 ID 안의 추가 콜론은 그대로 보존됩니다.

| 기능 | 환경변수 |
| --- | --- |
| 경제 설정·밸런싱 | `ECOVILLAGE_AI_ROUTE_BALANCING` |
| 교사용 챗봇 | `ECOVILLAGE_AI_ROUTE_TEACHER_CHAT` |
| 경제 이벤트·투자 뉴스 | `ECOVILLAGE_AI_ROUTE_ECONOMIC_EVENT` |
| 퀴즈 초안 | `ECOVILLAGE_AI_ROUTE_QUIZ_DRAFT` |

## 2. 환경변수

| 변수 | 의미 | 적용 조건 |
| --- | --- | --- |
| `ECOVILLAGE_AI_PROVIDER` | 전체 기본 `FAKE·BEDROCK·OPENAI` | 선택 |
| `ECOVILLAGE_AI_MODEL_ID` | 전체 기본 모델 ID | 선택 |
| `ECOVILLAGE_AI_BEDROCK_MODEL_ID` | Bedrock 기본 모델 ID | Bedrock 사용 시 |
| `AWS_REGION` 또는 `AWS_DEFAULT_REGION` | Bedrock Runtime 리전 | Bedrock 사용 시 |
| `ECOVILLAGE_AI_OPENAI_ENABLED` | OpenAI 사용 허용 플래그 | OpenAI 사용 시 `true` |
| `ECOVILLAGE_AI_OPENAI_MODEL_ID` | OpenAI 기본 모델 ID | OpenAI 사용 시 |
| `OPENAI_API_KEY` | OpenAI SDK 인증 | OpenAI 사용 시 |
| `ECOVILLAGE_AI_PROVIDER_TIMEOUT_SECONDS` | SDK 연결·응답 제한시간 상한 | 기본 10초 |
| `ECOVILLAGE_AI_MAX_OUTPUT_TOKENS` | 공급자 최대 출력 토큰 | 기본 4096 |
| `ECOVILLAGE_AI_ENV_FILE` | 시작할 때 읽을 `.env` 경로 지정 | 선택 |
| `ECOVILLAGE_AI_SKIP_ENV_FILE` | `.env`를 무시하고 셸 값만 사용 | 선택, `true`일 때 |

### `.env` 적용 범위

Docker Compose는 `.env`를 스스로 읽어 컨테이너 환경변수로 치환합니다. 앱을 직접
띄울 때도 같은 설정을 쓰도록, 애플리케이션을 만들 때 저장소 루트의 `.env`(없으면
`ai-service/.env`)를 한 번 읽습니다. 우선순위는 아래와 같습니다.

| 순위 | 출처 | 비고 |
| --- | --- | --- |
| 1 | 셸·컨테이너 환경변수 | 항상 우선하며 `.env`가 덮어쓰지 않습니다 |
| 2 | `.env` 파일 | `ECOVILLAGE_AI_ENV_FILE`로 경로를 바꿀 수 있습니다 |
| 3 | 코드 기본값 | 로컬 `FAKE`, 운영 `BEDROCK` |

평가 게이트 `python -m ecovillage_ai.evaluation`은 이 파일을 읽지 않고 `FAKE`를 유지하므로,
`.env`에 실제 공급자를 지정해도 출시 게이트가 크레딧을 쓰지 않습니다.

Bedrock 자격증명은 코드나 별도 AI 환경변수에 저장하지 않고 AWS SDK의 표준 자격증명
체인과 운영 역할을 사용합니다. OpenAI 키는 설정 객체, 응답, 안전 실행 로그에 복사하지
않고 SDK가 `OPENAI_API_KEY`에서 직접 읽습니다.

교사용 챗봇의 금지 요청은 제공자 어댑터를 호출하기 전에 내부 안전 가드가 차단합니다.
이 응답은 관측 메타데이터에 `RULES` / `teacher-chat-guard-v1`으로 기록하지만 선택 가능한
외부 제공자는 아니며, 네트워크 요청과 토큰 사용이 발생하지 않습니다.

## 3. 구조화 생성

### Bedrock

`BedrockGenerationProvider`는 Bedrock Runtime Converse API를 호출합니다.

- `system`에는 승인된 시스템 지시문만 전달합니다.
- 사용자 역할의 `messages`에는 최소화된 신뢰 입력 JSON만 전달합니다.
- `outputConfig.textFormat`에 JSON Schema와 스키마 이름을 전달합니다.
- 응답의 텍스트 블록과 `inputTokens·outputTokens`를 공통 결과로 변환합니다.

구조화 출력 형식은 [AWS Bedrock 공식 구조화 출력 문서](https://docs.aws.amazon.com/bedrock/latest/userguide/structured-output.html)를 기준으로 합니다.

### OpenAI

`OpenAIGenerationProvider`는 OpenAI Responses API를 호출합니다.

- `instructions`와 `input`을 분리합니다.
- `text.format`에 `strict=true` JSON Schema를 전달합니다.
- 공급자 저장을 요청하지 않도록 `store=false`를 전달합니다.
- `output_text`와 `input_tokens·output_tokens`를 공통 결과로 변환합니다.

구조화 출력 형식은 [OpenAI 공식 Structured Outputs 문서](https://developers.openai.com/api/docs/guides/structured-outputs)를 기준으로 합니다.

두 공급자가 지원하는 공통 JSON Schema 부분집합을 사용하기 위해 길이·수치 범위 같은
생성 단계 제약은 공급자용 스키마에서 제거합니다. 제거된 제약은 응답 후 기존 Pydantic과
7단계 검증 파이프라인에서 반드시 다시 검사합니다.

## 4. 오류와 재시도 경계

| 공급자 상태 | 내부 결과 |
| --- | --- |
| 429·호출 제한·할당량 제한 | `AI_PROVIDER_RATE_LIMITED` |
| 공급자·네트워크 일시 장애 | `AI_PROVIDER_UNAVAILABLE` |
| 공급자 또는 SDK timeout | `AI_DEADLINE_EXCEEDED` |
| 인증·권한·모델·요청 설정 오류 | 재시도 불가 `AI_PROVIDER_UNAVAILABLE` |

공급자 원문 오류, 요청 본문, 자격증명은 API 오류에 포함하지 않습니다. OpenAI SDK는
`max_retries=0`, Bedrock SDK는 `total_max_attempts=1`로 설정합니다. 따라서 FastAPI 요청
한 건은 공급자를 한 번만 호출하며, 429·503의 최대 1회 재시도 판단은 상위 핵심 API가
소유합니다.

## 5. 실제 공급자 smoke

기본 테스트는 실제 공급자를 호출하지 않습니다. 다음 플래그와 선택한 공급자의 필수
환경변수를 모두 설정한 경우에만 교사용 챗봇 fixture 한 건으로 실제 구조화 출력과 공통
검증을 확인합니다.

```powershell
$env:ECOVILLAGE_AI_RUN_PROVIDER_SMOKE='true'
pytest -m provider_smoke -q
```

10단계 통합 실행기에서는 `--provider-smoke` 자체가 명시적 실행 동의이므로 플래그를
내부 smoke 프로세스에 전달합니다.

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation --provider-smoke
```

이 테스트는 실제 사용량과 비용이 발생할 수 있으므로 CI 기본 작업에는 포함하지 않습니다.
