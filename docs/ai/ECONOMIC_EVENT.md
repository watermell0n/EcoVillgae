# AI 경제 이벤트 초안

- 기준일: 2026-07-30
- 상태: 독립 AI MVP 9단계 구현 완료
- API: `POST /internal/v1/generations/economic-event`
- 프롬프트 버전: `economic-event-v1`

## 1. 역할과 경계

Spring 규칙 엔진이 템플릿과 효과를 결정하면 AI 서비스는 교사가 검토할 제목·이야기·
선택지·진행 문구만 만듭니다. 출력은 항상 `REVIEW_REQUIRED`이며 AI가 금액·비율·확률을
계산하거나 이벤트를 승인·실행·공개하지 않습니다.

일곱 템플릿의 공통 이벤트 생성과 안전 검증을 제공하며, `INVESTMENT_NEWS`의 자산·단계
연결과 전용 안전 검증은 [가상 투자 단계별 AI 뉴스](INVESTMENT_NEWS.md)에서 정의합니다.

## 2. 템플릿과 학년 정책

| 템플릿 | 허용 학년 | 설명 범위 |
| --- | --- | --- |
| `PRICE` | 초4~초6 | 물가 변화 |
| `SUPPLY` | 초4~초6 | 상품·재료 공급 변화 |
| `PUBLIC_FUND` | 초4~초6 | 공동기금 사용 논의 |
| `TAX` | 초5~초6 | 세금과 공동 서비스 |
| `INTEREST_RATE` | 초5~초6 | 초5 예금·적금, 초6 대출 포함 |
| `INVESTMENT_NEWS` | 초5~초6 | 고정 가상 자산의 현재 공개 단계 뉴스 |
| `REWARD` | 초6 | 학급 전체 활동 보상 |

`course_scale=ONE_SHOT`은 추천 문맥이며 그 값만으로 템플릿을 금지하지 않습니다. 교사가
관련 모듈을 선택하고 실제 일정·정책이 유효하면 사용할 수 있습니다. 학년에서 허용하지
않는 템플릿과 초5 `LOAN` 금리 이벤트는 `GRADE_POLICY_VIOLATION`으로 거절합니다.

## 3. 두 진입 경로

- `TEACHER_REQUEST`: `source_proposal_id=null`, `source_evidence_codes=[]`
- `MONITOR_RECOMMENDATION`: 원본 `source_proposal_id`와 하나 이상의
  `source_evidence_codes` 필수

응답의 `source_type`, 원본 제안 ID, 근거 코드, 템플릿과 금리 범위는 입력과 정확히
일치해야 합니다. 출처를 바꾸거나 근거를 추가·삭제한 출력은 전체를 거절합니다.

## 4. 결정적 안전 검사

공급자 호출 전에는 교사 문맥·효과 설명·금지 효과에서 이메일·전화번호·개인 UUID,
비밀번호·토큰·API 키와 프롬프트 주입을 검사합니다.

생성 후에는 다음을 검사합니다.

- 숫자·비율·금액·확률과 입력의 금지 효과를 새로 출력하지 않는지
- 특정 학생 손익, 강제 대출, 개인 잔액·계좌 직접 변경과 음수 잔액이 없는지
- 템플릿·출처·근거·금리 범위와 투자 단계 연결이 입력과 같은지
- 두 개 이상의 중복되지 않은 선택지와 학년별 문장 길이를 지키는지
- 조롱·혐오·폭력 표현, 실제 금융 조언과 미래 가격 예측이 없는지
- 교사 승인 없이 실행·공개·게시·적용됐다는 표현이 없는지

## 5. 42개 평가 세트

[`economic_event_evaluation_v1.json`](../../ai-service/tests/fixtures/economic_event_evaluation_v1.json)은
일곱 템플릿마다 다음 여섯 상황을 실행합니다.

1. 교사 요청 성공
2. 모니터링 추천 성공
3. 학년 또는 안전하지 않은 입력 사전 거절
4. 금지 효과 출력 거절
5. 출처 문맥 변조 거절
6. 승인 경계 우회 거절

합계는 성공 14개, 공급자 호출 전 거절 7개, 잘못된 출력 거절 21개로 총 42개입니다.
추가 경계 검사를 포함한 전용 테스트는 55개입니다.

```bash
.venv/Scripts/python.exe -m pytest tests/test_economic_event.py -q
```

`55 passed`가 나오면 42개 평가와 추가 입력·출력 경계 검사가 모두 통과한 것입니다.

## 6. Swagger에서 직접 실행

Git Bash:

```bash
export ECOVILLAGE_AI_PROVIDER=FAKE
.venv/Scripts/python.exe -m uvicorn ecovillage_ai.main:app --reload --app-dir src
```

PowerShell:

```powershell
$env:ECOVILLAGE_AI_PROVIDER='FAKE'
.venv\Scripts\python.exe -m uvicorn ecovillage_ai.main:app --reload --app-dir src
```

`http://127.0.0.1:8000/docs`에서 `POST /internal/v1/generations/economic-event`를 열고
Authorize에 `local-development-token`을 입력합니다. 요청 헤더의 `X-Correlation-Id`와
`X-AI-Run-Id`는 본문의 해당 UUID와 같게 입력합니다.

```json
{
  "ai_run_id": "11111111-1111-4111-8111-111111111111",
  "request_id": "22222222-2222-4222-8222-222222222222",
  "correlation_id": "33333333-3333-4333-8333-333333333333",
  "schema_version": "1.2",
  "prompt_version": "economic-event-v1",
  "attempt": 1,
  "deadline_at": "2099-12-31T23:59:59+09:00",
  "locale": "ko-KR",
  "grade": 5,
  "payload": {
    "course_scale": "ONE_MONTH",
    "source_type": "TEACHER_REQUEST",
    "source_proposal_id": null,
    "source_evidence_codes": [],
    "template_type": "TAX",
    "interest_rate_scope": null,
    "effect_summary": ["규칙 엔진이 정한 세금 효과를 학습용 이야기로 설명한다."],
    "prohibited_effects": ["학생 계좌 직접 변경", "특정 학생 손익"],
    "teacher_context": "세금과 공동 서비스의 관계를 생각하게 한다.",
    "investment_news_context": null
  }
}
```

성공하면 `review_status=REVIEW_REQUIRED`와 입력과 같은 템플릿·출처, 제목·이야기,
두 개 이상의 선택지와 진행 문구가 반환됩니다. `MONITOR_RECOMMENDATION`을 시험하려면
유효한 `source_proposal_id`와 하나 이상의 대문자 근거 코드를 함께 넣습니다.

`FAKE` 실행에는 API 키가 필요하지 않습니다. 실제 Bedrock·OpenAI 문장 품질 검사는
10단계의 opt-in smoke에서 별도로 실행합니다.
