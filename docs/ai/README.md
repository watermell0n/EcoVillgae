# AI 설계 산출물

경제 지표·임계값, 평가 시나리오, 프롬프트, 구조화 응답 스키마와 회귀 평가 결과를
저장합니다. 제품 정책은 `docs/AI_DIRECTION.md`, API 경계는
`docs/API_COMMON_RULES.md`를 따릅니다.

## 문서

| 문서 | 역할 |
| --- | --- |
| [내부 AI API 계약](INTERNAL_API_CONTRACT.md) | Spring 핵심 API와 FastAPI 사이의 요청·응답·오류·실행 경계 |
| [경제 지표·임계값](METRICS_AND_THRESHOLDS.md) | 밸런싱 입력 지표 계산식, 최소 표본, 경고와 조정 허용 범위 |
| [안전·평가 기준](SAFETY_AND_EVALUATION.md) | 입력 최소화, 출력 검사, 회귀 평가와 출시 게이트 |
| [단계별 개발 계획](IMPLEMENTATION_PLAN.md) | 전체 MVP AI 기능을 FastAPI 단독으로 완성한 뒤 Spring·DB·웹을 연결하는 실행 순서 |
| [기계 판독 내부 OpenAPI](../../contracts/ai-internal-openapi.yaml) | FastAPI 내부 HTTP 인터페이스의 OpenAPI 3.1 계약 |

프롬프트 본문과 회귀 평가 케이스는 독립 AI MVP 구현 단계에서 버전 디렉터리에 추가합니다.
제품 정책이나 수치 계산 규칙을 프롬프트에만 숨겨 두지 않고 위 문서와 핵심 API의
규칙 계약을 먼저 변경합니다.
