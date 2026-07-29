# AI 경제 지표와 임계값

- 버전: `economic-metrics-v1`
- 기준일: 2026-07-30
- 상태: MVP 구현 기준선
- 적용 기능: `API-AI-002`, `API-AI-003`, `API-READ-027`

이 문서는 AI가 경제 수치를 추측하지 않도록 Spring 규칙 엔진이 계산할 입력 지표와
조정 허용 범위를 정의합니다. FastAPI는 전달받은 지표의 의미를 설명하고 우선순위를
정하지만 원장 집계, 임계값 판정, 금액 계산과 정책 적용은 하지 않습니다.

## 1. 분석 구간과 공통 단위

- 현재 구간은 요청의 `[period_from, period_to)`이고 비교 구간은 바로 앞의 같은 길이
  구간입니다.
- `data_as_of` 이후 커밋된 거래와 변경된 정책은 해당 실행에 포함하지 않습니다.
- 거래 지표는 `COMMITTED` 원장 거래만 포함하고 거절·진행 중 거래는 제외합니다.
- 복구 거래는 원거래와 함께 순액으로 계산합니다. 같은 경제 활동을 원거래와 복구
  거래로 두 번 세지 않습니다.
- 학생 수는 `data_as_of` 시점에 학급 참여 상태가 활성인 학생을 기준으로 합니다.
- 금액과 수량은 정수, 비율은 1만분율 정수인 `RATE_BPS`로 전달합니다.
  `1% = 100 bps`, `100% = 10,000 bps`입니다.
- 분모가 0이거나 필요한 이력을 복원할 수 없으면 값을 0으로 만들지 않고
  `availability=UNAVAILABLE`, `value=null`, `state=UNAVAILABLE`, 원인 코드를
  전달합니다.

Spring은 원장 거래 유형을 다음 표준 경제 흐름으로 먼저 분류합니다. 이 분류표는
애플리케이션 코드의 버전 관리 대상이며 프롬프트에만 숨겨 두지 않습니다.

| 흐름 코드 | 의미 | 대표 사례 |
| --- | --- | --- |
| `ISSUANCE` | 시스템에서 학생 지갑으로 새 화폐가 유입 | 급여, 활동·퀴즈 보상, 대출 실행 |
| `RECOVERY` | 학생 지갑에서 시스템 회수 계정으로 화폐가 이동 | 세금, 상점 결제, 대출 상환 |
| `STUDENT_TRANSFER` | 학생 지갑 사이 이동 | 송금, 개인 거래 |
| `FINANCIAL_RECLASSIFICATION` | 학생 소유 자산 사이 이동 | 예금·적금 납입과 해지 |
| `REVERSAL` | 선행 거래 복구 | 원거래 취소·보상 거래 |

`STUDENT_TRANSFER`와 `FINANCIAL_RECLASSIFICATION`은 학급 전체 발행·회수액에 넣지
않습니다. 대출 실행은 학생 가용 화폐를 늘리므로 발행 흐름에, 대출 상환은 가용
화폐를 회수하므로 회수 흐름에 포함하되 별도 금융 지표에서도 구분해 보여 줍니다.

## 2. 데이터 충분성

다음 둘 중 하나라도 충족하지 못하면 모델을 호출하지 않고 AI 실행을
`INSUFFICIENT_DATA`로 종료합니다.

| 판정 항목 | 실행 가능 조건 | 실패 코드 |
| --- | ---: | --- |
| 활성 학생 수 | 5명 이상 | `ACTIVE_STUDENTS_LT_5` |
| 현재 구간 관련 거래 수 | 10건 이상 | `RELATED_TRANSACTIONS_LT_10` |

관련 거래는 요청한 지표를 계산하는 데 실제 사용된 중복 제거 후 `COMMITTED`
거래 그룹입니다. 전체 거래 수가 10건 이상이어도 선택한 지표의 관련 거래가 10건
미만이면 데이터 부족입니다. 직전 구간은 추세 비교에만 쓰므로 현재 구간의 최소
표본 판정을 대체하지 않습니다.

데이터 부족 응답에는 학생 수, 관련 거래 수, 부족 코드, 필요한 최소값만 포함하며
학생별 원문이나 잔액은 포함하지 않습니다.

## 3. 핵심 지표 계산식

아래 식에서 `safe_rate(n, d)`는 `d > 0`일 때 반올림한 `n / d * 10,000`이고,
그렇지 않으면 `UNAVAILABLE`입니다. 변화율은 `safe_rate(종료값 - 시작값,
max(abs(시작값), 1))`로 계산합니다.

| 지표 코드 | 단위 | 계산식 | 해석 |
| --- | --- | --- | --- |
| `LIQUID_MONEY_CHANGE_RATE_BPS` | `RATE_BPS` | 전체 활성 학생 지갑의 구간 시작·종료 합계 변화율 | 가용 화폐 팽창·수축 |
| `ISSUANCE_AMOUNT` | `AMOUNT` | 현재 구간 `ISSUANCE` 순액 합계 | 새로 공급된 화폐 |
| `RECOVERY_AMOUNT` | `AMOUNT` | 현재 구간 `RECOVERY` 순액 합계 | 시스템으로 회수된 화폐 |
| `ISSUANCE_RECOVERY_RATE_BPS` | `RATE_BPS` | `safe_rate(RECOVERY_AMOUNT, ISSUANCE_AMOUNT)` | 발행 대비 회수 균형 |
| `WALLET_AVERAGE_CHANGE_RATE_BPS` | `RATE_BPS` | 학생별 지갑 잔액 평균의 구간 시작 대비 종료 변화율 | 학급 전체 평균 유동성 |
| `WALLET_MEDIAN_CHANGE_RATE_BPS` | `RATE_BPS` | 학생별 종료 지갑 잔액 중앙값의 시작 대비 변화율 | 극단값 영향을 줄인 체감 유동성 |
| `ASSET_CONCENTRATION_TOP20_BPS` | `RATE_BPS` | 종료 시점 상위 `ceil(활성 학생 수×0.2)`명의 양수 지갑 잔액 / 전체 양수 지갑 잔액 | 지갑 자산 집중도 |
| `CONSUMPTION_RATE_BPS` | `RATE_BPS` | `safe_rate(상점 결제+비금융 소비 회수액, 시작 지갑 합계+ISSUANCE_AMOUNT)` | 가용 자금 중 소비된 비율 |
| `PRODUCT_SELL_THROUGH_RATE_BPS` | `RATE_BPS` | 상품별 `safe_rate(판매 수량, 기초 판매가능 재고+구간 중 입고)` | 상품 판매 속도 |
| `PRODUCT_STOCKOUT_SHARE_BPS` | `RATE_BPS` | `safe_rate(data_as_of 시점 재고 0인 판매 상품 수, 전체 판매 상품 수)` | 현재 품절 상품 비중 |
| `FINANCIAL_PARTICIPATION_RATE_BPS` | `RATE_BPS` | 상품별 `safe_rate(활성 계좌 학생 수, 가입 가능 활성 학생 수)` | 예금·적금·대출 참여 정도 |
| `PAYMENT_MISS_RATE_BPS` | `RATE_BPS` | `safe_rate(미납·연체 회차 수, 구간 내 납기 도래 회차 수)` | 적금 미납·대출 연체 부담 |
| `INVESTMENT_PARTICIPATION_RATE_BPS` | `RATE_BPS` | `safe_rate(구간 내 투자 체결 학생 수, 투자 가능 활성 학생 수)` | 가상 투자 참여 정도 |
| `INVESTMENT_MEDIAN_RETURN_BPS` | `RATE_BPS` | 원가가 있는 참여자별 실현·평가 손익률의 중앙값 | 규칙 기반 시나리오의 결과 분포 |
| `INVESTMENT_LOSS_PARTICIPANT_RATE_BPS` | `RATE_BPS` | `safe_rate(손익률이 음수인 참여자 수, 원가가 있는 참여자 수)` | 손실 경험의 분포 |
| `REWARD_ISSUANCE_SHARE_BPS` | `RATE_BPS` | `safe_rate(활동+퀴즈 보상 발행액, ISSUANCE_AMOUNT)` | 보상 발행 의존도 |

상품 판매 지표는 상품별 값과 학급 전체 가중 합계를 함께 생성합니다. 판매 중지 상품,
삭제 상품과 아직 공개되지 않은 상품은 대상에서 제외합니다. 단순 평균 때문에 판매량이
없는 상품이 큰 상품과 같은 비중을 갖지 않도록 학급 전체 판매율은 공급 수량 가중치로
계산합니다.

`PRODUCT_SELL_THROUGH_RATE_BPS`의 기초 재고와 입고량은 `store_stocks`의 현재값만으로
추측하지 않습니다. 상품 주문 차감과 교사 재고 조정 때 남긴 append-only 감사 요약의
변경 전후 수량으로 복원합니다. 구현 시 해당 감사 이벤트가 없는 과거 구간은
`STOCK_HISTORY_UNAVAILABLE`로 표시하고 판매율 제안을 만들지 않습니다.

## 4. 임계값과 상태 판정

임계값 묶음 버전은 `economic-thresholds-v1`입니다. 이 값은 MVP 초기 휴리스틱이며
운영 평가 결과로 새 버전을 추가할 수 있지만, 이미 완료된 AI 실행의 버전과 판정은
바꾸지 않습니다.

| 지표 | 관찰 상태 | 경고 후보 상태 |
| --- | --- | --- |
| `LIQUID_MONEY_CHANGE_RATE_BPS` | `-1500..1500` | `< -1500` 수축, `> 1500` 팽창 |
| `ISSUANCE_RECOVERY_RATE_BPS` | `5000..12000` | `< 5000` 회수 부족, `> 12000` 순회수 과다 |
| `ASSET_CONCENTRATION_TOP20_BPS` | `<= 5000` | `> 5000` 집중 |
| `CONSUMPTION_RATE_BPS` | `2000..8000` | `< 2000` 소비 정체, `> 8000` 소비 과열 |
| `PRODUCT_SELL_THROUGH_RATE_BPS` | `1000..8000` | `< 1000` 판매 정체, `> 8000` 빠른 소진 |
| `PRODUCT_STOCKOUT_SHARE_BPS` | `<= 2000` | `> 2000` 품절 상품 편중 |
| `PAYMENT_MISS_RATE_BPS` | `<= 2000` | `> 2000` 미납·연체 주의 |
| `REWARD_ISSUANCE_SHARE_BPS` | `<= 3000` | `> 3000` 보상 발행 편중 |

단일 구간이 임계값을 벗어나면 `OBSERVE`입니다. 현재 구간과 바로 전 같은 길이 구간이
같은 방향으로 임계값을 벗어났을 때만 `WARN`으로 올립니다. 이전 구간이 데이터 부족,
`UNAVAILABLE` 또는 반대 방향이면 `WARN`이 될 수 없습니다.

```text
INSUFFICIENT_DATA  모델 미호출
NORMAL             임계값 안쪽
OBSERVE            현재 구간만 임계값 초과
WARN               현재·직전 두 구간이 같은 방향으로 연속 초과
```

경고가 없어도 교사가 분석을 요청할 수 있지만, 조정 제안은 `WARN` 지표 또는 교사가
명시적으로 선택한 지표와 직접 연결되어야 합니다. 모델은 존재하지 않는 경고 코드를
만들 수 없습니다.

## 5. 조정 제안 허용 범위

Spring은 현재 정책과 절대 정책 한도를 읽어 아래 상대 한도를 적용하고, 각 조정 후보를
`action_id`, `target_type`, `target_id`, `current_value`, `min_value`, `max_value`,
`unit`으로 만들어 FastAPI에 전달합니다. FastAPI는 `action_id`와 범위 안의 정수
`proposed_value`만 선택할 수 있습니다.

| 대상 | 1회 상대 조정 한도 | 단위 |
| --- | ---: | --- |
| 상품 가격 | 현재값의 `±10%` | `AMOUNT` |
| 상품 재고 | 현재값의 `±20%` | `COUNT` |
| 활동·게임·퀴즈 보상 | 현재값의 `±10%` | `AMOUNT` |
| 세율 | `±100 bps` | `RATE_BPS` |
| 물가 정책 | `±500 bps` | `RATE_BPS` |
| 예금·적금·대출 금리 | `±100 bps` | `RATE_BPS` |

최종 `min_value`와 `max_value`는 상대 한도, 학년 정책, 상품 불변식과 DB의 절대
한도 중 가장 좁은 범위입니다. 현재값이 0인 가격·보상·재고는 상대 비율로 복구할 수
없으므로 Spring이 별도의 절대 기본 범위를 제공하지 않으면 제안 대상에서 제외합니다.

다음 변경은 AI 제안으로 만들지 않습니다.

- 학생별 잔액·신용·보상·세금 변경
- 이미 커밋된 거래나 금융상품 계약의 소급 변경
- 학생 지갑을 음수로 만드는 변경
- 금리 상품의 기존 계약 조건 변경
- 학급 모드, 권한, 승인 절차 또는 AI 사용 설정 변경
- 경제 이벤트의 숫자 효과 직접 생성

## 6. 제안 적용과 재검증

1. 제안에는 기준 `policy_version`, 지표·임계값 버전, `data_as_of`, 근거 지표 코드를
   저장합니다.
2. 교사는 원제안 범위 안에서만 값을 편집할 수 있습니다. 범위를 바꾸려면 새 분석을
   요청해야 합니다.
3. `API-AI-003` 적용 시 Spring은 최신 정책 버전, 대상 존재 여부, 현재값, 허용 범위와
   교사 권한을 다시 검증합니다.
4. 하나라도 달라졌으면 전체 적용을 중단하고 `POLICY_VERSION_CONFLICT` 또는
   `PROPOSAL_EXPIRED`를 반환합니다. 부분 적용은 허용하지 않습니다.
5. 승인된 값만 새 정책 버전으로 원자적으로 커밋하고 교사 결정과 변경 전후 값을
   감사 로그에 기록합니다.

## 7. AI 전달 예시

```json
{
  "metric_version": "economic-metrics-v1",
  "threshold_version": "economic-thresholds-v1",
  "period": {
    "from": "2026-07-13T00:00:00Z",
    "to": "2026-07-20T00:00:00Z",
    "data_as_of": "2026-07-20T00:00:00Z"
  },
  "sample": {
    "active_students": 23,
    "related_transactions": 148,
    "status": "SUFFICIENT"
  },
  "metrics": [
    {
      "code": "CONSUMPTION_RATE_BPS",
      "availability": "AVAILABLE",
      "value": 8450,
      "unit": "RATE_BPS",
      "state": "WARN",
      "direction": "HIGH",
      "previous_value": 8210,
      "unavailable_reason": null
    }
  ],
  "allowed_actions": [
    {
      "action_id": "product-price-01",
      "target_type": "PRODUCT_PRICE",
      "target_id": "7ea3e20e-bb13-494f-a4de-e783d5fb7b31",
      "current_value": 1000,
      "min_value": 900,
      "max_value": 1100,
      "unit": "AMOUNT"
    }
  ]
}
```

학생별 지표를 모델 입력에 넣지 않습니다. `target_id`는 정책 변경 대상을 다시 찾기
위한 불투명 식별자이며 학생을 식별할 수 없어야 합니다.
