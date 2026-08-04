# AI 설계 산출물

경제 지표·임계값, 평가 시나리오, 프롬프트, 구조화 응답 스키마와 회귀 평가 결과를
저장합니다. 제품 정책은 `docs/AI_DIRECTION.md`, API 경계는
`docs/API_COMMON_RULES.md`를 따릅니다.

## 문서

| 문서 | 역할 |
| --- | --- |
| [내부 AI API 계약](INTERNAL_API_CONTRACT.md) | Spring 핵심 API와 FastAPI 사이의 요청·응답·오류·실행 경계 |
| [경제 지표·임계값](METRICS_AND_THRESHOLDS.md) | 밸런싱 입력 지표·경고·조정 범위, 60개 평가와 Swagger 직접 테스트 |
| [안전·평가 기준](SAFETY_AND_EVALUATION.md) | 입력 최소화, 출력 검사, 회귀 평가와 출시 게이트 |
| [프롬프트·공통 생성 런타임](PROMPT_RUNTIME.md) | 버전 프롬프트 선택, 공급자 명령, 순서형 검증과 안전 로그 |
| [실제 공급자 어댑터](PROVIDER_ADAPTERS.md) | Bedrock·OpenAI 구조화 출력, 라우팅, 환경 설정, 오류와 smoke 검사 |
| [근거 기반 교사용 챗봇](TEACHER_CHAT.md) | 집계 사실 입력, 근거 연결 출력, 사전 제한 가드와 60개 평가 사례 |
| [교사용 AI 퀴즈 초안](QUIZ_DRAFT.md) | 학년·분류·유형·난이도별 생성, 결정적 품질 필터, 48개 평가와 로컬 테스트 |
| [경제 설정 추천·시작 전 점검](SETUP_PRECHECK.md) | 규칙 엔진 후보·판정 보존, 설명 품질 검증, 48개 평가와 Swagger 직접 테스트 |
| [규칙 엔진 추천·판정 상세 명세](RULE_ENGINE_SPEC.md) | 전 모듈 선택형, 모듈 활성화 시 급여·세율·예금·대출금리 추천, 상품 가격·GAME 보상, 1단위 금액, 실제 지급 일정, 보상 포함 발행액 30% 상한과 PASS·WARN·BLOCK 판정 |
| [AI 경제 이벤트 초안](ECONOMIC_EVENT.md) | 7종 템플릿, 두 출처, 학년·코스·효과 안전 검증, 42개 평가와 Swagger 직접 테스트 |
| [가상 투자 단계별 AI 뉴스](INVESTMENT_NEWS.md) | 목표 가격 단계 연결, 기업·가격·미래정보 차단, 32개 평가와 Swagger 직접 테스트 |
| [AI 독립 MVP 통합 평가와 출시 게이트](MVP_RELEASE_EVALUATION.md) | 공통 60개와 전체 350개 평가, 통합 실행기, 사람·공급자·Docker 선택 검사 |
| [단계별 개발 계획](IMPLEMENTATION_PLAN.md) | 전체 MVP AI 기능을 FastAPI 단독으로 완성한 뒤 Spring·DB·웹을 연결하는 실행 순서 |
| [기계 판독 내부 OpenAPI](../../contracts/ai-internal-openapi.yaml) | FastAPI 내부 HTTP 인터페이스의 OpenAPI 3.1 계약 |

프롬프트 본문은 `ai-service/src/ecovillage_ai/prompts`의 버전 디렉터리에서 관리합니다.
회귀 평가 케이스는 이후 기능 완성 단계에서 계속 확장합니다.
제품 정책이나 수치 계산 규칙을 프롬프트에만 숨겨 두지 않고 위 문서와 핵심 API의
규칙 계약을 먼저 변경합니다.
