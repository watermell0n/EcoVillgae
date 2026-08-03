# AI 프롬프트·공통 생성 런타임

- 기준일: 2026-07-30
- 상태: 독립 AI MVP 10단계 구현 완료
- 적용 계약: 내부 AI schema v1.2

## 1. 목적

모든 AI 기능이 같은 방식으로 승인된 프롬프트를 선택하고 공급자를 호출하며 결과를
검증하게 합니다. 이 런타임은 실제 Bedrock·OpenAI 연결과 무관하며 공급자가 바뀌어도
서비스·검증 계층은 유지됩니다.

## 2. 프롬프트 선택

프롬프트 키는 기능, 실행 모드, 학년, 프롬프트 버전으로 결정합니다.

| 기능 | 모드 선택 기준 | 요청 버전 |
| --- | --- | --- |
| 설정 점검 | `SETUP_PRECHECK` | `setup-precheck-v1` |
| 교사 요청 밸런싱 | `ON_DEMAND` | `balancing-v1` |
| 자동 모니터링 | `AUTO_MONITOR` | `balancing-v1` |
| 교사용 챗봇 | 답변 모드 | `teacher-chat-v1` |
| 경제 이벤트·투자 뉴스 | 7종 `template_type` | `economic-event-v1` |
| 퀴즈 초안 | `category`와 `question_type` 조합 | `quiz-draft-v1` |

학년 4·5·6은 각각 별도 선택 키와 고정 학년 지침을 사용합니다. 등록되지 않은 조합이나
요청 버전 불일치는 공급자 호출 전에 `AI_RUN_CONTEXT_MISMATCH`로 거절합니다.

프롬프트 본문은
[`ai-service/src/ecovillage_ai/prompts/v1`](../../ai-service/src/ecovillage_ai/prompts/v1)에
보관합니다. 제품 정책과 수치 규칙의 기준은 계속 `docs/AI_DIRECTION.md`와 규칙 엔진이며,
프롬프트 파일만 바꿔 정책을 변경하지 않습니다.

## 3. 프롬프트 버전과 동결 규칙

`prompt_version`은 파일 이름이 아니라 **여러 프롬프트 파일을 묶는 계약 식별자**입니다.
하나의 버전이 파일 여러 개를 가리킬 수 있습니다.

| 버전 | 프롬프트 파일 |
| --- | --- |
| `setup-precheck-v1` | `v1/balancing/setup-precheck.txt` |
| `balancing-v1` | `v1/balancing/on-demand.txt`, `v1/balancing/auto-monitor.txt` |
| `teacher-chat-v1` | `v1/teacher-chat.txt` |
| `economic-event-v1` | `v1/economic-event/` 아래 7개 템플릿 파일 |
| `quiz-draft-v1` | `v1/quiz-draft.txt` |

버전은 Spring 요청에 실려 오고 `ai_runs.prompt_version`에 영구 기록되며 출시 평가
매니페스트가 목록을 고정 검사합니다. 즉 **"이 실행이 어떤 지시문으로 만들어졌는지"를
사후에 추적하는 유일한 단서**입니다.

### 3.1 동결 시점

프롬프트 튜닝 중에는 v1 파일을 자유롭게 수정합니다. **실제 수업에 처음 사용하는 시점부터
v1을 동결하고, 그 이후의 모든 지시문 변경은 v2로 올립니다.**

동결 전에는 아직 추적해야 할 실행 기록이 없으므로 같은 v1 안에서 문구를 보강해도
재현성 문제가 생기지 않습니다. 동결 후에는 같은 버전 값이 서로 다른 지시문을 가리키게
되어 `ai_runs` 기록만으로 결과를 설명할 수 없습니다.

### 3.2 버전을 올릴 때 함께 바꿀 곳

| 대상 | 내용 |
| --- | --- |
| 프롬프트 디렉터리 | `prompts/v2/` 신설 |
| `prompting.py` | `SUPPORTED_PROMPT_VERSIONS`와 `_selection_parts`의 경로 |
| Spring 계약 상수 | `SetupPrecheckContract`, `BalancingContract`, `TeacherChatContract`, `EconomicEventContract`, `QuizDraftContract`의 `PROMPT_VERSION` |
| 출시 매니페스트 | `tests/fixtures/ai_mvp_release_v1.json`의 `prompt_versions` |
| 평가 fixture | 기능별 평가 파일의 `prompt_version` |
| 이 문서 | 2장 표와 위 표 |

요청 버전과 선택된 프롬프트가 어긋나면 공급자 호출 전에 `PROMPT_VERSION_MISMATCH`로
거절하므로, 위 목록 중 하나만 빠뜨려도 실행이 실패합니다.

### 3.3 동결 전 변경 기록

동결 전 변경은 버전을 올리지 않는 대신 커밋 메시지에 무엇을 왜 바꿨는지 남깁니다.
실제 공급자 전환 과정에서 `strict` 구조화 출력이 배열 최소 개수를 강제하지 못해
프롬프트가 그 규칙을 직접 명시하도록 보강한 사례가 있습니다.

## 4. 공급자 생성 명령

공급자는 다음 네 요소가 분리된 `GenerationCommand`만 받습니다.

1. `system_instruction`: 저장소에 승인된 정적 시스템 지침과 고정 학년 지침
2. `trusted_input_json`: 입력 모델에서 최소화한 학년·언어·payload와 필요한 실행 모드
3. `output_schema`: 기능별 Pydantic 출력 모델에서 생성한 JSON Schema
4. `prompt_key`와 `prompt_version`: 실행에 사용한 결정적 프롬프트 식별자

`ai_run_id`, `request_id`, `correlation_id`, deadline과 인증 정보는 모델 입력에 넣지
않습니다. 교사 질문을 포함한 자유 텍스트는 `trusted_input_json`의 데이터로만 직렬화하고
`system_instruction`에 문자열로 결합하지 않습니다.

공급자는 JSON 문자열 또는 JSON object를 반환할 수 있습니다. 공통 파서는 잘못된 JSON,
object가 아닌 최상위 값, 지원하지 않는 콘텐츠 형식과 Pydantic 스키마 위반을 모두
`AI_OUTPUT_SCHEMA_INVALID`로 변환합니다.

## 5. 결정적 검증 순서

공급자 출력은 다음 순서에서 첫 실패 단계까지만 평가합니다.

| 순서 | 단계 | 검사 예시 |
| ---: | --- | --- |
| 1 | `SCHEMA` | JSON 파싱, 추가 필드, enum, 길이·개수 |
| 2 | `CONTEXT` | 출력 종류, 기간, 템플릿, 투자 단계, 퀴즈 문맥 |
| 3 | `EVIDENCE` | 입력 metric·fact·reason·concept 참조 여부 |
| 4 | `RANGE` | action 존재, 후보값 보존, `min..max` 범위 |
| 5 | `GRADE_AND_EFFECT` | 학년 템플릿과 이벤트 금지 효과·수치 |
| 6 | `CONTENT_SAFETY` | 개인정보, 비밀, 개인 분석, 금융 조언, 미래 가격 |
| 7 | `APPROVAL_BOUNDARY` | 승인·게시·직접 적용 우회 표현 |

뒤 단계의 위반이 함께 있어도 앞 단계 오류를 먼저 반환하므로 회귀 결과가 결정적입니다.

교사용 챗봇은 공통 검증 전에 기능 전용 입력 가드를 적용합니다. 민감한 입력은 오류로
종료하고, 개인 분석·현실 금융 조언·미래 결과·직접 변경·프롬프트 탈취 요청은 외부 모델을
호출하지 않는 `RULES / teacher-chat-guard-v1` 제한 답변으로 처리합니다. 허용 질문만
선택된 생성 공급자에 전달되며 세부 계약은 [교사용 챗봇](TEACHER_CHAT.md)을 따릅니다.

퀴즈 초안은 공급자 호출 전에 주제·개념·학습목표의 민감정보, 비밀값, 프롬프트 주입과
초등 범위 밖 요청을 검사합니다. 출력은 공통 검증 순서에서 요청 조합, 개념 태그,
중복 문항·선택지, OX 의미, 숫자 정답, 해설 일치, 학년별 길이와 유해 표현을 검사합니다.
세부 계약과 실행법은 [교사용 AI 퀴즈 초안](QUIZ_DRAFT.md)을 따릅니다.

설정 점검은 후보 표시 이름과 규칙 메시지의 민감정보·비밀값·프롬프트 주입을 공급자 호출
전에 차단합니다. 출력은 후보 대상·표시 이름·값·범위·단위·근거 코드, 점검 심각도·근거·
관련 action과 전체 심각도를 입력에 대조합니다. `PASS`의 불필요한 수정 안내와
`WARN·BLOCK`의 수정 안내·주의점 누락도 차단하며, 세부 계약과 실행법은
[경제 설정 추천·시작 전 점검](SETUP_PRECHECK.md)을 따릅니다.

운영 밸런싱은 상품·지표 표시 이름의 민감정보·비밀값·프롬프트 주입, 표본 부족,
사용 가능한 지표 없음, 자동 모니터링 NORMAL을 공급자 호출 전에 종료합니다. 출력은
실행 모드·트리거·심각도·지표 버전·현재와 이전 구간, 원인·제안 근거와 허용 action의
대상·값·범위·단위를 입력에 대조합니다. NORMAL의 조정 제안, 조작된 근거·action·범위와
자동 적용 표현을 차단하며 실행·평가 방법은 [경제 지표·임계값](METRICS_AND_THRESHOLDS.md)의
10장을 따릅니다.

경제 이벤트는 일곱 템플릿별 프롬프트를 선택하고 교사 요청·모니터링 추천 출처와 근거를
출력까지 보존합니다. 학년 정책 위반과 민감정보·비밀값·프롬프트 주입은 공급자 호출 전에
차단합니다. `ONE_SHOT`은 추천 문맥으로만 사용합니다. 출력에서는 숫자 효과·특정 학생 손익·강제 대출·잔액 직접 변경,
출처 변조와 승인 우회를 순서대로 검사하며 세부 계약과 실행법은
[AI 경제 이벤트 초안](ECONOMIC_EVENT.md)을 따릅니다.

`INVESTMENT_NEWS`는 같은 런타임의 전용 모드입니다. 입력 시나리오·자산·목표 가격 단계와
자산명·기호를 출력까지 보존하고, 기업·재무정보·가격·수익률·매매 추천·다음 단계 정보와
승인 전 공개 표현을 결정적으로 차단합니다. 단계별 사전 생성과 32개 평가는
[가상 투자 단계별 AI 뉴스](INVESTMENT_NEWS.md)를 따릅니다.

## 6. 구조화 실행 로그

성공과 실패 모두 `SafeRunEvent` 하나로 기록합니다.

- 실행·요청·상관관계 ID
- 기능, schema·프롬프트 버전과 프롬프트 키
- 공급자, 모델, 시도 번호, 학년
- 지연시간, 입력·출력 토큰 수
- 성공·실패 결과, 오류 코드, 첫 실패 단계와 검증 코드

질문·답변, 시스템 프롬프트, 신뢰 입력 JSON, 전체 payload와 공급자 원문은 로그 필드에
존재하지 않습니다. 로그 sink 장애도 생성 성공·실패 결과를 바꾸지 않습니다.

## 7. 공급자 경계

`FakeGenerationProvider`, `BedrockGenerationProvider`, `OpenAIGenerationProvider`는 모두
같은 `GenerationCommand`와 `ProviderResult` 계약을 사용합니다. 기능별 라우터가 공급자와
모델을 선택하고 실제 선택 결과를 응답 메타데이터와 `SafeRunEvent`에 남깁니다.

SDK 내부 자동 재시도는 비활성화합니다. 한 FastAPI 요청에서 공급자를 한 번만 호출하고
429·503에 대한 재시도 가능 여부만 상위 호출자에게 반환합니다. 환경변수, 구조화 출력 방식,
오류 변환과 opt-in smoke 절차는 [실제 공급자 어댑터](PROVIDER_ADAPTERS.md)를 따릅니다.
