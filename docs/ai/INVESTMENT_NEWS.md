# 단기·장기 투자 AI 이야기

- 기준일: 2026-08-04
- API: `POST /internal/v1/generations/economic-event`
- 템플릿: `template_type=INVESTMENT_NEWS`
- 프롬프트 버전: `economic-event-v1`

## 1. 운영 모드

교사는 투자 모듈을 활성화할 때 `investment_mode`를 하나만 선택합니다.

| 모드 | 학생 행동 | 정산 | AI 이야기 |
| --- | --- | --- | --- |
| `SHORT_TERM` | 오늘 투자 또는 건너뛰기 | 다음 날 해당 회차를 반드시 전량 정산 | `investment-news-short-term` 프롬프트가 다음 날 결과 방향과 맞는 가상 기업 단서 이야기를 생성 |
| `LONG_TERM` | 매일 더 투자·보유·매도 | 서비스 마지막 날 남은 보유분 전량 정산 | `investment-news-long-term` 프롬프트가 다음 단계 방향과 맞는 가상 기업의 하루 소식을 생성 |

학생 화면은 교사가 선택한 모드 하나만 보여 줍니다. 모드는 `classroom_modules.config_json`의
`investment_mode`에 저장하고, 활성 시나리오의 모드와 다르면 뉴스 생성을 거절합니다.

## 2. 가격 경로와 AI 책임

가격 결과와 학생용 이야기를 분리합니다.

1. Spring이 현재 공개 단계와 숨은 바로 다음 가격 단계를 읽습니다.
2. 다음 단계의 정확한 가격 대신 `target_direction=RISE|FALL|FLAT`만 FastAPI의 신뢰 입력으로 전달합니다.
3. AI는 방향과 자연스럽게 맞는 사업·수요·비용·품질 단서를 이야기로 만듭니다.
4. 학생용 출력에는 다음 가격, 방향, 수익 보장, 매수·매도 추천을 넣지 않습니다.
5. 이야기는 현재 공개 단계의 `price_step_id`에 연결하고 결과는 별도의
   `target_price_step_id`에 연결합니다. 따라서 학생은 결과가 공개되기 전에 단서를 읽을 수 있습니다.

가상 기업의 정체성은 제품 데이터가 제공하는 `asset_name`, `asset_symbol`,
`fictional_company_profile`을 사용합니다. AI는 다른 회사나 실제 상장사를 새로 만들지 않습니다.

## 3. 장기 투자 역사 지수 기준

장기 투자는 `scenario_source=HISTORICAL_INDEX`를 우선합니다. 시나리오에는 다음 출처 메타데이터를
고정합니다.

- `series_code`
- `event_name`
- `source_name`
- `source_url`
- `period_start`, `period_end`

승인된 제품 데이터가 실제 지수 구간을 학급 가격 단위로 정규화해 `investment_price_steps`에
저장합니다. 가격 흐름은 저장된 지수 경로를 따르고 AI는 매일 그 다음 방향과 사건의 분위기에 맞는
가상 이야기를 작성합니다. 실제 지수명·실제 회사명·기준 기간은 학생용 문구에 노출하지 않습니다.

초기 기준 출처로 FRED의 S&P 500 일별 종가 설명을 사용할 수 있습니다. 이 자료는 S&P Dow Jones
Indices의 저작권·재배포 조건이 있으므로, 원시 값을 저장하거나 배포하기 전에 제품이 사용 권한을
확인해야 합니다. 권한이 확인되지 않거나 데이터가 없으면 `scenario_source=AI_GENERATED`로
명시적으로 폴백합니다. 폴백 시에도 가격 단계는 먼저 고정하고 AI가 매 호출마다 가격을 바꾸게 하지
않습니다.

## 4. 내부 계약

`investment_news_context`의 핵심 필드는 다음과 같습니다.

- 현재 연결: `scenario_version_id`, `asset_id`, `price_step_id`, `current_step_index`
- 숨은 결과 연결: `target_price_step_id`, `target_step_index`, `target_direction`
- 모드와 정산: `investment_mode`, `scenario_source`, `settlement_rule`
- 가상 기업: `asset_name`, `asset_symbol`, `fictional_company_profile`
- 공개 사실: `public_price_facts` — 첫 단계부터 현재 단계까지만 포함
- 장기 역사 기준: `historical_index_reference` — 역사 지수 모드에서만 필수

`investment_news_binding`은 방향과 정확한 가격을 제외한 연결 필드를 그대로 반환합니다. Spring과
FastAPI는 하나라도 바뀌면 `INVESTMENT_NEWS_BINDING_MISMATCH` 또는 폴백으로 처리합니다.

모드별 선택지 키도 고정합니다.

- 단기: `INVEST`, `SKIP`
- 장기: `INVEST_MORE`, `HOLD`, `SELL`

## 5. 안전 검증

| 금지 내용 | 검증 코드 |
| --- | --- |
| 입력에 자유 문장으로 미래 결과 주입 | `INVESTMENT_FUTURE_INFORMATION_INPUT` |
| 입력 프로필 밖의 새 회사 생성 | `INVESTMENT_COMPANY_GENERATION` |
| 정확한 가격 생성 | `INVESTMENT_PRICE_GENERATION` |
| 수익 보장·매수·매도 추천 | `INVESTMENT_TRADING_ADVICE` |
| 다음 가격·상승·하락 직접 노출 | `INVESTMENT_FUTURE_INFORMATION` |
| 실제 회사·실제 지수명 학생 문구 노출 | `INVESTMENT_REFERENCE_LEAK` |
| 모드와 다른 선택지 | `INVESTMENT_MODE_CHOICES_MISMATCH` |
| 입력과 다른 연결 ID·모드·단계 | `INVESTMENT_NEWS_BINDING_MISMATCH` |
| 이미 승인·공개됐다고 표현 | `APPROVAL_BOUNDARY_VIOLATION` |

모든 응답은 `REVIEW_REQUIRED`이며 승인과 학생 공개는 Spring과 교사의 책임입니다.

## 6. 평가

기존 4개 가상 자산 × 8개 상황의 총 32개 평가 세트를 유지하고, 별도 단위 테스트로 다음을
추가 검증합니다.

- 단기 모드가 전용 프롬프트와 `INVEST|SKIP` 선택지를 사용
- 단기에 역사 지수나 장기 정산 규칙을 넣으면 공급자 호출 전에 거절
- 현재 단계와 숨은 다음 단계의 ID·번호가 바뀌면 출력 거절
- 장기 역사 지수의 출처·기간 교차 필드 검증

```powershell
.venv\Scripts\python.exe -m pytest tests/test_investment_news.py -q
```
