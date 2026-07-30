# 가상 투자 단계별 AI 뉴스

- 기준일: 2026-07-30
- 상태: 독립 AI MVP 9단계 구현 완료
- API: `POST /internal/v1/generations/economic-event`
- 모드: `template_type=INVESTMENT_NEWS`
- 프롬프트 버전: `economic-event-v1`

## 1. 무엇을 생성하는가

MVP의 투자 대상은 실제 회사나 주식이 아니라, 서비스가 미리 정한 가상 자산입니다.
가격 경로도 AI가 아니라 규칙 기반 투자 시뮬레이터가 정합니다. AI는 자산 하나와 목표
가격 단계 하나를 입력받아 다음 검토용 초안만 만듭니다.

- 입력 자산명 또는 기호를 포함한 제목과 뉴스 이야기
- 학생이 생각을 나눌 수 있는 두 개 이상의 선택지와 진행 문구
- 뉴스가 어떤 입력 범위로 만들어졌는지 알려 주는 교사용 설명
- 시나리오·자산·가격 단계·자산명·기호를 그대로 보존한 연결 정보

연결 정보는 `scenario_version_id`, `asset_id`, `price_step_id`, `asset_name`,
`asset_symbol`, `current_step_index` 여섯 필드로 고정합니다.

모든 응답은 `REVIEW_REQUIRED`입니다. 승인·저장·학생 공개는 Spring과 교사의 책임이며
AI 서비스는 뉴스가 이미 승인 또는 공개됐다고 표현하지 않습니다.

## 2. 단계별 입력 원칙

`current_step_index=N`인 한 요청은 첫 단계부터 N단계까지의
`public_price_facts`만 가질 수 있습니다. 단계 번호는 빠짐없이 오름차순이어야 하고
`revealed_at`도 단계 순서대로 증가해야 합니다. N+1 이후 사실을 함께 보내면 공급자를
호출하기 전에 요청을 거절합니다.

시장 시작 전 초안을 준비할 때도 별도 배치 API를 만들지 않습니다. Spring 작업자가 각
단계를 목표 단계로 삼아 같은 API를 순서대로 호출합니다.

```text
1단계 초안 호출: 1단계 사실만 입력
2단계 초안 호출: 1~2단계 사실만 입력
3단계 초안 호출: 1~3단계 사실만 입력
4단계 초안 호출: 1~4단계 사실만 입력
```

각 호출은 목표 단계 이후의 가격을 보지 않습니다. 사전 생성된 초안은 학생에게 곧바로
공개되지 않으며, Spring이 단계별 승인 상태와 공개 시점을 별도로 관리합니다.

## 3. 생성 금지와 검증 코드

| 금지 내용 | 대표 검증 코드 |
| --- | --- |
| 실제 회사·기업·업종·매출·재무정보 생성 | `INVESTMENT_COMPANY_GENERATION` |
| 가격·시세·금액 생성 | `INVESTMENT_PRICE_GENERATION` |
| 수익률·수익 보장·매수·매도 추천 | `INVESTMENT_TRADING_ADVICE` |
| 다음 단계·미공개 정보·미래 방향 예측 | `INVESTMENT_FUTURE_INFORMATION` |
| 입력과 다른 연결 ID·자산명·기호·단계 | `INVESTMENT_NEWS_BINDING_MISMATCH` |
| 입력 자산명이나 기호가 없는 뉴스 | `INVESTMENT_ASSET_REFERENCE_MISSING` |
| 승인·공개·게시가 끝났다는 표현 | `APPROVAL_BOUNDARY_VIOLATION` |

자산명이나 교사 요청에 실제 기업 생성 또는 미래정보 요청이 들어와도 외부 공급자를
호출하기 전에 거절합니다. 입력의 가격 사실은 연결과 맥락 검증에만 사용하며 AI 출력의
자연어 가격으로 복사하지 않습니다.

## 4. 32개 평가 세트

[`investment_news_evaluation_v1.json`](../../ai-service/tests/fixtures/investment_news_evaluation_v1.json)은
합성 가상 자산 4종에 다음 8개 상황을 적용합니다.

1. 현재 단계 한 건 정상 생성
2. 시장 시작 전 네 단계 초안 생성
3. 목표 단계 이후 가격 사실 입력 거절
4. 기업·재무정보 출력 거절
5. 가격 출력 거절
6. 미래 단계·가격 방향 출력 거절
7. 수익 보장·매매 추천 출력 거절
8. 승인 전 공개 경계 위반 거절

합계는 정상 8개, 공급자 호출 전 거절 4개, 잘못된 출력 거절 20개로 총 32개입니다.
연결 자산명·기호 변조와 단계 누락·시각 역전 검사도 별도 경계 테스트로 실행합니다.

```bash
.venv/Scripts/python.exe -m pytest tests/test_investment_news.py -q
```

## 5. Swagger에서 직접 테스트

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
Authorize에 `local-development-token`을 입력합니다. `X-Correlation-Id`와
`X-AI-Run-Id` 헤더는 아래 본문의 같은 UUID를 입력합니다.

```json
{
  "ai_run_id": "11111111-1111-4111-8111-111111111111",
  "request_id": "22222222-2222-4222-8222-222222222222",
  "correlation_id": "33333333-3333-4333-8333-333333333333",
  "schema_version": "1.1",
  "prompt_version": "economic-event-v1",
  "attempt": 1,
  "deadline_at": "2099-12-31T23:59:59+09:00",
  "locale": "ko-KR",
  "grade": 6,
  "payload": {
    "course_scale": "ONE_MONTH",
    "source_type": "TEACHER_REQUEST",
    "source_proposal_id": null,
    "source_evidence_codes": [],
    "template_type": "INVESTMENT_NEWS",
    "interest_rate_scope": null,
    "effect_summary": ["목표 가격 단계와 연결된 가상 자산 소식이다."],
    "prohibited_effects": ["실제 기업 생성", "가격 생성", "매매 추천"],
    "teacher_context": "현재 확인 가능한 사실만 쉬운 뉴스로 설명한다.",
    "investment_news_context": {
      "scenario_version_id": "44444444-4444-4444-8444-444444444444",
      "asset_id": "55555555-5555-4555-8555-555555555555",
      "price_step_id": "66666666-6666-4666-8666-666666666666",
      "asset_name": "푸른별",
      "asset_symbol": "BLUE",
      "current_step_index": 2,
      "public_price_facts": [
        {
          "step_index": 1,
          "price": 100,
          "change_rate_bps": 0,
          "revealed_at": "2026-07-01T09:00:00+09:00"
        },
        {
          "step_index": 2,
          "price": 105,
          "change_rate_bps": 500,
          "revealed_at": "2026-07-08T09:00:00+09:00"
        }
      ]
    }
  }
}
```

성공 응답에서 `review_status=REVIEW_REQUIRED`인지, 제목이나 본문에 `푸른별` 또는
`BLUE`가 있는지, `investment_news_binding`의 여섯 필드가 입력과 같은지 확인합니다.
`FAKE` 실행에는 API 키가 필요하지 않습니다.
