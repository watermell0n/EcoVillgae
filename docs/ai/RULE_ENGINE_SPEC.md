# 규칙 엔진 추천·판정 상세 명세

- 규칙 엔진 명세 버전: `rule-engine-v2`
- 기준일: 2026-07-31
- 상태: 정책 검토 결과 반영, Spring 구현 기준
- 대상: 모듈 활성화·상품 등록·퀴즈 세션 등록 시 규칙 기반 추천값과 적용 전 점검을 계산하는 Spring 규칙 엔진

이 문서는 Spring 규칙 엔진이 계산하는 추천값과 `PASS·WARN·BLOCK` 판정을 정의합니다. AI 서비스는 이 값을 바꾸지 않고 교사가 이해하기 쉬운 설명만 생성합니다.

상위 문서: [AI 적용 방향](../AI_DIRECTION.md), [내부 AI API 계약](INTERNAL_API_CONTRACT.md),
[경제 지표·임계값](METRICS_AND_THRESHOLDS.md), [경제 설정 추천·시작 전 점검](SETUP_PRECHECK.md).
값이 충돌하면 `docs` 문서를 정본으로 사용합니다.

## 0. 핵심 원칙

1. **필수 선택 모듈은 없습니다.** 역할(직업)·급여, 소득세, 예금, 대출, 상점, 퀴즈 등 모든 기능 모듈은 교사가 선택합니다.
2. **역할(직업)·급여 모듈을 선택하지 않은 학생은 직업과 급여 없이 수업할 수 있습니다.** 이 상태 자체는 오류가 아닙니다.
3. 지갑·원장 기능은 선택 모듈이 아니라 금액을 다루는 모듈이 필요할 때 자동으로 사용하는 기술 인프라입니다.
4. 추천값은 모듈이 실제로 활성화될 때 계산합니다. 방 생성 시 사용하지 않을 예금·대출·급여 값을 미리 만들지 않습니다.
5. 보상 비중은 **보상을 포함한 전체 발행액 중 모든 보상의 합계**가 30% 이하여야 합니다.
6. 모든 금액은 최소 통화 단위 `1`의 정수입니다. 교사가 벌거나 소비한 결과가 반드시 10의 배수일 필요는 없습니다.

| 용어 | 뜻 |
| --- | --- |
| 기준 급여 `S_base` | 초기 추천을 위한 기준값 `1,000`. 실제 급여가 생긴 뒤에는 현재 급여 구성으로 계산한 `S_ref`를 사용합니다. |
| 현재 급여 기준 `S_ref` | 활성 역할과 학생 배정 현황을 반영한 현재 1회 평균 급여. 역할 모듈이 없을 때는 다른 발행 채널 또는 `S_base`를 참조값으로만 사용합니다. |
| bps | 비율을 정수로 저장하는 단위. `100 bps = 1%`, `10,000 bps = 100%`입니다. |
| 발행 / 회수 | 발행은 급여·보상처럼 시스템이 학생에게 주는 돈, 회수는 세금·상점 결제처럼 시스템이 거둬들이는 돈입니다. |
| 추천 구간 | 교육적으로 권장하는 범위. 벗어나도 허용 구간 안이면 `WARN`입니다. |
| 허용 구간 | 시스템이 저장을 허용하는 절대 범위. 벗어나면 `BLOCK`입니다. |

## 1. 추천 시점과 대상

| 추천값 | `target_type` | 컨텍스트 | `target_id` |
| --- | --- | --- | --- |
| 역할별 급여 | `ROLE_SALARY` | 역할·급여 모듈 활성화 | 모듈 UUID |
| 소득세율 | `INCOME_TAX_RATE` | 소득세 모듈 활성화 | 모듈 UUID |
| 예금 금리 | `DEPOSIT_RATE` | 예금 모듈 활성화 | 모듈 UUID |
| 대출 금리 | `LOAN_RATE` | 대출 모듈 활성화 | 모듈 UUID |
| 상품 가격 | `PRODUCT_PRICE` | 상품 등록·수정 | 상품 UUID |
| GAME 퀴즈 보상 | `GAME_REWARD` | 퀴즈 세션 설정 | 세션·초안 UUID |

외부 모듈 코드는 [정식 선택 모듈 카탈로그](../MODULE_CATALOG.md)를 따릅니다. 호환 입력
`INCOME`·`SALARY`는 `ROLE_SALARY`, `TAX`는 `INCOME_TAX`로 정규화하고 저장·응답에는
별칭을 사용하지 않습니다. 후보 유형은 `ROLE_SALARY`→`ROLE_SALARY`,
`INCOME_TAX`→`INCOME_TAX_RATE`, `DEPOSIT`→`DEPOSIT_RATE`, `LOAN`→`LOAN_RATE`로
고정합니다. 상점처럼 초기 수치 후보가 필요 없는 모듈의 `MODULE_ACTIVATION` 요청은 후보
배열이 비어 있을 수 있습니다.

- `CLASSROOM_SETUP`은 통화명, 실제 운영 기간처럼 모든 방에 공통인 정적 조건만 점검하며 위 6종 수치 후보를 만들지 않습니다.
- 방 생성에서 교사가 고른 모듈은 선택 의도로 `DISABLED` 등록할 수 있습니다. 수치가 필요한 모듈은 이어지는 활성화 단계에서만 추천·확정합니다.
- 모듈 활성화 추천은 `MODULE_ACTIVATION` 컨텍스트에서 해당 모듈에 필요한 후보만 만듭니다.
- 교사가 후보를 확인하거나 수정한 뒤, 최신 상태로 재검증하여 **모듈 활성화와 최초 정책 저장을 한 트랜잭션으로 처리**합니다.
- 추천만 받고 확정하지 않으면 모듈은 `DISABLED` 상태를 유지합니다.
- 재활성화할 때 기존 정책이 현재 허용 범위 안이면 재사용할 수 있습니다. 값이 바뀐 경우에만 새 정책 버전을 만듭니다.

### 1.1 모듈 독립성과 조건부 선행 관계

- 전역 필수 모듈은 없습니다. `active_modules`는 빈 배열일 수 있습니다.
- 역할·급여 모듈이 비활성화되면 역할 생성·배정, 급여명세서 생성, 급여 발행, 급여·소득세 추천을 하지 않습니다. 학생의 `salary_role_id`는 `null`일 수 있습니다.
- 소득세는 급여에 부과하므로 소득세 모듈은 역할·급여 모듈을 선행 조건으로 둡니다.
- 대출 금리의 예대 스프레드를 보장하기 위해 대출 모듈은 예금 모듈과 현재 예금 기준금리를 선행 조건으로 둡니다.
- 상점·보상처럼 돈을 사용하는 모듈은 지갑·원장을 내부적으로 사용하지만, 지갑·원장을 교사가 별도 필수 모듈로 선택하지는 않습니다.
- 아무 모듈도 선택하지 않은 방은 정상입니다. 적용할 경제 점검이 없으면 `NO_ECONOMIC_MODULES_SELECTED`로 `PASS` 처리합니다.

## 2. 입력값

규칙 엔진의 내부 입력은 AI에 보내는 `SetupContext`보다 상세합니다. Spring이 먼저 계산하고, AI에는 계산 결과를 설명용으로 전달합니다.

| 컨텍스트 | 필요한 입력 |
| --- | --- |
| `CLASSROOM_SETUP` | 학년(4·5·6), 코스 라벨, 실제 운영 시작일·종료일, 예상 학생 수, 통화명, 활성 모듈 목록 |
| `MODULE_ACTIVATION` | 위 공통값, `module_code`, 모듈 UUID, 관련 기존 정책, 역할 구성·학생 배정·지급 일정 등 해당 모듈 입력 |
| `PRODUCT_DRAFT` | 상품 종류, 재고, 예상 구매 빈도, 부가세율, 현재 발행 구조와 급여·세율(활성화된 경우) |
| `QUIZ_SESSION_DRAFT` | GAME·TEST 모드, 분류, 문항 수, 예상 참가자 수, 예상 실행 횟수, 현재 비보상 발행액과 기존 보상 발행액 |

### 2.1 운영 기간과 급여 지급 횟수

코스 라벨로 지급 횟수를 추정하지 않습니다. 실제 날짜와 지급 간격으로 계산합니다.

- `operation_starts_on`, `operation_ends_on`: 양 끝 날짜를 포함한 실제 운영 기간
- `pay_interval_days`: 지급 간격, 1 이상의 정수
- `first_pay_on`: 첫 지급일
- `operation_days = operation_ends_on - operation_starts_on + 1`

```
first_pay_on이 운영 기간 안이면
N_pay = floor((operation_ends_on - first_pay_on) / pay_interval_days) + 1
그렇지 않으면
N_pay = 0
```

교사가 값을 지정하지 않았을 때만 다음 기본값을 제안합니다.

```
pay_interval_days = min(7, operation_days)
first_pay_on       = operation_starts_on + pay_interval_days - 1
```

- 7일 운영·7일 간격은 1회, 30일 운영·7일 간격은 4회, 112일 운영·7일 간격은 16회입니다.
- 끝에 남는 불완전 주기는 기본적으로 지급하지 않습니다. 교사가 별도 지급일을 추가하면 그 일정을 사용합니다.
- 운영 기간·첫 지급일·간격이 바뀌면 기존 점검 결과를 만료시키고 다시 계산합니다.
- `ONE_SHOT` 라벨은 급여 지급을 자동 금지하지 않습니다. 역할·급여 모듈을 선택하고 유효한 실제 일정이 있으면 지급할 수 있습니다.

## 3. 확정 상수

### 3.1 급여·격차

| 항목 | 값 |
| --- | --- |
| 초기 기준 급여 `S_base` | 1,000 |
| 역할 가중치 허용 범위 | 초4 0.9~1.2 / 초5 0.8~1.4 / 초6 0.7~1.7 |
| 역할 격차 경고 상한(최고÷최저) | 초4 1.5 / 초5 2.0 / 초6 2.5 |
| 급여 허용 하한 | 100 |

### 3.2 세율·금리

| 항목 | 제안값 | 허용 구간 |
| --- | ---: | ---: |
| 소득세율 초4 | 300 bps | 200~500 bps |
| 소득세율 초5 | 500 bps | 300~800 bps |
| 소득세율 초6 | 1,000 bps | 500~1,500 bps |
| 예금 기준금리 | 300 bps | 100~600 bps |
| 대출 기준금리 | 현재 예금 + 400 bps | 현재 예금 + 200~900 bps, 절대 상한 1,500 bps |

- 금리는 만기 1회 상품 기준이며 연이율이 아닙니다.
- 비율은 1 bps 단위 정수로 저장하고, 규칙 엔진 추천값만 50 bps 단위로 반올림합니다.
- 예금 활성화 시 저장한 값은 새 예금 상품의 기본 참조금리입니다. 상품별 기간 금리는 상품 생성 시 별도 정책으로 만들 수 있습니다.

### 3.3 상품·보상

| 항목 | 값 |
| --- | --- |
| 상품 결제액 비율 | 소모품·이용권 0.15 / 일반 0.30 / 희소·캐릭터 0.50 |
| GAME 경제 보상 기준 | 참조 발행액 1회분 × 0.05 |
| 넌센스 보상 상한 | 같은 조건의 경제 보상 30% 이하 |
| 보상 주의선 | 보상 포함 전체 발행액의 25% 초과 |
| 보상 절대 상한 | 보상 포함 전체 발행액의 30% 이하 |

### 3.4 단위·반올림·범위

- 금액 저장 단위와 추천 단위는 모두 최소 통화 단위 `1`의 정수입니다.
- `roundAmount(x)`는 최소 통화 단위 `1`로 `HALF_UP` 반올림합니다.
- 가격 `0`은 무료 상품, 보상 `0`은 무보상을 뜻합니다. 유료 상품·보상을 활성화하면 최소값은 `1`입니다.
- 급여 하한 `100`은 통화 저장 단위가 아니라 교육적 허용 하한입니다.
- 금리 추천은 `roundRate50(x)`로 50 bps 단위 반올림합니다.
- 급여·상품·보상의 학년별 추천 폭 `w(g)`는 초4 ±10%, 초5 ±15%, 초6 ±20%입니다.

모든 후보는 다음 필드를 가지며 아래 불변식을 만족해야 합니다.

```
allowed_min_value
<= recommended_min_value
<= proposed_value
<= recommended_max_value
<= allowed_max_value
```

- 추천 구간 밖·허용 구간 안: `WARN` `OUT_OF_RECOMMENDED_BAND`
- 허용 구간 밖: `BLOCK` `VALUE_OUT_OF_RANGE`
- `min_value`, `max_value`처럼 추천·허용 의미가 섞인 필드는 사용하지 않습니다.

## 4. 공통 계산 프레임

### 4.1 현재 참조 발행액 `S_ref`

1. 역할·급여 모듈이 활성화되고 학생이 배정되어 있으면 활성 학생의 역할별 현재 급여를 인원수로 가중 평균합니다.
2. 학생은 없지만 역할 급여가 설정되어 있으면 설정된 활성 역할 급여의 평균을 사용합니다.
3. 역할 모듈이 없고 정기적인 비급여 발행 채널이 설정되어 있으면 학생 1인당 1주기 예상 발행액을 사용합니다.
4. 위 값이 모두 없으면 계산 설명을 위한 참조값으로만 `S_base=1,000`을 사용하고 `NO_ISSUANCE_CHANNEL`을 `WARN`으로 냅니다. 이 참조값을 실제 발행액으로 합산하지 않습니다.

역할 모듈이 활성화됐지만 일부 학생이 미배정이면 해당 학생은 현재 기본 역할 급여 또는 `S_base`를 가중 평균에 사용하고 `UNASSIGNED_SALARY_ROLE`을 `WARN`으로 냅니다.

```
effective_tax_bps = 소득세 모듈이 활성화됐으면 현재 세율, 아니면 0
net_ref = roundAmount(S_ref × (1 - effective_tax_bps / 10,000))
```

### 4.2 운영 기간 예산

```
I_nonreward = 운영 기간의 급여 + 기타 모든 비보상 발행액
R_existing  = 후보를 제외한 기존 활동·퀴즈 보상 예상액
I_total     = I_nonreward + R_existing + R_candidate
recovery    = 예상 세금 + 예상 상점 결제 + 기타 회수액
balance     = recovery / I_total
```

- `I_total=0`이고 `recovery=0`이면 예산 균형은 적용 대상이 아닙니다. 모듈이 없다는 이유로 경고하지 않습니다.
- 발행액이 있는데 회수 채널이 하나도 없으면 `NO_RECOVERY_CHANNEL` 경고를 냅니다.
- 회수/발행 권장 구간은 운영 지표와 동일하게 50%~120%입니다.

## 5. 추천 공식

### 5.1 역할별 급여 `ROLE_SALARY`

역할·급여 모듈 활성화 시에만 만듭니다.

```
proposed = max(100, roundAmount(S_base × weight(role)))
recommended_min = max(100, roundAmount(proposed × (1 - w(g))))
recommended_max = roundAmount(proposed × (1 + w(g)))
allowed_min = 100
allowed_max = 시스템 금액 상한과 학년 역할 가중치 허용 범위 중 작은 값
reason = [SALARY_ROLE_WEIGHT, SALARY_GRADE_PROFILE]
```

### 5.2 소득세율 `INCOME_TAX_RATE`

소득세 모듈 활성화 시에만 만듭니다. 역할·급여 모듈이 비활성화됐으면 선행 조건 위반으로 활성화를 차단합니다.

```
proposed = roundRate50(학년 기준 세율)
recommended_min = max(allowed_min, proposed - 100)
recommended_max = min(allowed_max, proposed + 100)
allowed_min, allowed_max = 3.2의 학년별 허용 구간
reason = [TAX_GRADE_PROFILE, TAX_RECOVERY_BALANCE]
```

### 5.3 예금 금리 `DEPOSIT_RATE`

예금 모듈 활성화 시에만 만듭니다.

예금 이자는 만기 1회 단순이자(`SIMPLE_FULL_TERM`, `이자 = 원금 × rate_bps / 10000`)이므로 연이율이 아닙니다. 그래서 기준금리를 **실제 운영 기간**에 따라 조정합니다. 짧은 수업은 예금이 한두 번밖에 만기되지 않아 한 번으로도 이자를 체감해야 하고, 긴 운영은 만기가 반복되며 이자가 순발행으로 누적되므로 낮춰야 합니다.

학년은 사용하지 않습니다.

| 운영 기간(일) | 기준값 |
| --- | --- |
| 7 이하 | 500 |
| 8 ~ 31 | 300 |
| 32 ~ 90 | 200 |
| 91 이상 | 150 |

```
operation_days = 3.1의 운영 일수 (운영 시작일~종료일 포함)
proposed = clamp(roundRate50(기준값(operation_days)), 100, 600)
recommended_min = max(allowed_min, proposed - 100)
recommended_max = min(allowed_max, proposed + 100)
allowed_min, allowed_max = 100, 600
reason = [DEPOSIT_TERM_BASED, DEPOSIT_OPERATION_PERIOD]
```

운영 일수를 계산할 수 없으면(날짜 누락·시작일보다 이른 종료일) 한 달 운영 기준인 300을 씁니다. 이 경우 공통 검증이 이미 `REQUIRED_VALUE_MISSING` 또는 `VALUE_OUT_OF_RANGE`로 차단합니다.

허용 구간 `100 ~ 600`은 5.4 대출 금리 규칙이 전제하는 값입니다. 대출은 현재 예금 금리가 이 범위를 벗어나면 활성화를 차단하므로, 예금 허용 구간을 바꿀 때는 5.4를 함께 바꿔야 합니다.

추천 근거를 교사 화면에 보여 주기 위해 `PASS` 등급 점검 항목 `DEPOSIT_RATE_BASIS`를 함께 남깁니다.

```
"운영 기간 31일을 기준으로 3.00%를 추천했습니다."
```

### 5.4 대출 금리 `LOAN_RATE`

대출 모듈 활성화 시에만 만듭니다. 현재 예금 기준금리가 없으면 활성화를 차단합니다.

```
deposit = 현재 예금 기준금리
proposed = roundRate50(clamp(deposit + 400, deposit + 200, min(deposit + 900, 1500)))
recommended_min = max(deposit + 200, proposed - 100)
recommended_max = min(min(deposit + 900, 1500), proposed + 100)
allowed_min = deposit + 200
allowed_max = min(deposit + 900, 1500)
reason = [LOAN_DEPOSIT_SPREAD, LOAN_GRADE_BAND]
```

### 5.5 상품 가격 `PRODUCT_PRICE`

`freq`는 학생 1명당 한 급여 주기 동안 예상하는 구매 횟수이며 0보다 커야 합니다. 참여율을 포함한 소수일 수 있습니다. 예상 판매 수량은 재고를 넘을 수 없습니다.

```
target_checkout = net_ref × ratio(상품 종류) / freq
scarcity = 재고 < 예상 수요
  ? clamp(1 + 0.2 × (1 - 재고 / 예상 수요), 1.0, 1.2)
  : 1.0
target_checkout = target_checkout × scarcity
pre_tax_price = target_checkout / (1 + vat_bps / 10,000)
proposed = max(1, roundAmount(pre_tax_price))
recommended_min = max(1, roundAmount(proposed × (1 - w(g))))
recommended_max = roundAmount(proposed × (1 + w(g)))
allowed_min = 1
allowed_max = 시스템 금액 상한
reason = [SALARY_PRICE_BALANCE, PRODUCT_KIND_RATIO, PRODUCT_STOCK_SCARCITY]
```

- 추천 비율은 부가세를 포함한 학생의 최종 결제액을 기준으로 합니다. 저장할 세전 가격은 위처럼 역산합니다.
- 무료 상품은 별도 선택으로 `0`을 허용하며 이때 가격 후보를 만들지 않아도 됩니다.

### 5.6 GAME 퀴즈 보상 `GAME_REWARD`

`TEST` 모드에는 보상 후보를 만들지 않습니다.

```
base = 경제 퀴즈 ? S_ref × 0.05 : S_ref × 0.05 × 0.30
base = roundAmount(base)
R_candidate = reward_per_person × expected_participants × expected_runs

(R_existing + R_candidate)
------------------------------------------ <= 0.30
I_nonreward + R_existing + R_candidate

max_candidate_total = floor((3 / 7) × I_nonreward - R_existing)
max_reward_per_person = floor(
  max(0, max_candidate_total) / expected_participants / expected_runs
)
proposed = min(base, max_reward_per_person)
recommended_min = max(0, roundAmount(proposed × (1 - w(g))))
recommended_max = min(max_reward_per_person, roundAmount(proposed × (1 + w(g))))
allowed_min = 0
allowed_max = max_reward_per_person
```

- 기존 보상과 신규 보상을 모두 합쳐 판단합니다. 세션 하나만 따로 30%를 적용하지 않습니다.
- 넌센스 보상은 위 경제 보상 조정값의 30% 이하이며, 최소 통화 단위로 내림합니다.
- 보상 비중이 25%를 초과하면 `WARN`, 30%를 초과하면 `BLOCK`입니다.
- `I_nonreward=0`이면 금전 보상 여력이 없습니다. 추천값은 `0`이며 기존 양수 보상은 `REWARD_ISSUANCE_SHARE_EXCEEDED`로 차단합니다.
- 유효한 예상 참가자 수와 실행 횟수가 없으면 `REQUIRED_VALUE_MISSING`입니다.

## 6. 시작 전 점검

`overall_severity`는 개별 `checks`의 가장 높은 심각도로 **파생**합니다: `BLOCK > WARN > PASS`. Spring이 정본 판정을 만들고 FastAPI도 응답 조립 시 같은 규칙으로 검증합니다.

### 6.1 BLOCK

| 사유 코드 | 조건 |
| --- | --- |
| `REQUIRED_VALUE_MISSING` | 선택한 모듈·작업에 필요한 입력이나 확정값 누락 |
| `VALUE_OUT_OF_RANGE` | 확정값이 허용 구간 밖 |
| `MODULE_PREREQUISITE_MISSING` | 소득세의 역할·급여, 대출의 예금 등 조건부 선행 모듈 누락 |
| `FORBIDDEN_REWARD_CONTEXT` | `TEST`에 금전 보상 존재 |
| `LOAN_DEPOSIT_SPREAD_VIOLATION` | 대출 금리 < 예금 금리 + 200 bps |
| `REWARD_ISSUANCE_SHARE_EXCEEDED` | 보상 포함 전체 발행액 중 모든 보상 합계가 30% 초과 |
| `CURRENCY_NAME_MISSING` | 선택 모듈과 무관하게 방의 통화명 누락 |

### 6.2 WARN

| 사유 코드 | 조건 |
| --- | --- |
| `ISSUANCE_RECOVERY_IMBALANCE` | 발행액이 존재하고 회수/발행 비율이 50%~120% 밖 |
| `OUT_OF_RECOMMENDED_BAND` | 추천 구간 밖이지만 허용 구간 안 |
| `ROLE_SALARY_GAP_EXCESSIVE` | 최고÷최저 급여가 학년 격차 상한 초과 |
| `UNASSIGNED_SALARY_ROLE` | 역할·급여 모듈은 켰지만 일부 활성 학생의 역할 미배정 |
| `REWARD_SHARE_HIGH` | 보상 포함 전체 발행액 중 모든 보상 합계가 25% 초과·30% 이하 |
| `NO_RECOVERY_CHANNEL` | 발행액은 있으나 세금·상점 등 회수 수단이 없음 |
| `NO_ISSUANCE_CHANNEL` | 역할·급여와 다른 정기 발행 채널이 없어 가격·보상에 참조값만 사용 |

### 6.3 PASS 또는 적용 대상 아님

| 사유 코드 | 조건 |
| --- | --- |
| `NO_ECONOMIC_MODULES_SELECTED` | 선택한 경제 모듈이 없어 적용할 경제 점검이 없음 |
| `REQUIRED_VALUES_PRESENT` | 선택한 작업에 필요한 값이 모두 준비됨 |
| `VALUES_WITHIN_RANGE` | 확정값이 허용 구간 안 |
| `BUDGET_BALANCED` | 발행액이 있고 회수/발행 비율이 50%~120% 안 |
| `BUDGET_NOT_APPLICABLE` | 발행액과 회수액이 모두 0 |

역할·급여 모듈을 선택하지 않았다는 사실, 학생에게 직업이 없다는 사실, 예금·대출 모듈이 비활성화됐다는 사실만으로는 `WARN`이나 `BLOCK`을 만들지 않습니다.

## 7. 변경과 재계산

다음 값 중 관련 값이 바뀌면 이전 점검 결과를 만료시키고 재계산합니다.

- 활성 모듈과 모듈 정책 버전
- 운영 시작일·종료일, 급여 지급 간격·첫 지급일
- 예상·활성 학생 수, 역할 배정과 현재 급여
- 세율·예금·대출 금리
- 상품 가격·재고·예상 수요·부가세
- 퀴즈 보상·예상 참가자 수·예상 실행 횟수
- 기타 발행·회수 채널

승인 적용 시에도 proposal 생성 당시의 대상 UUID·버전, 학급 state version, 현재 정책
버전 목록, 활성 모듈과 draft fingerprint를 다시 비교합니다. 일치할 때만 교사 수정값을
`confirmedPrice`, `confirmedRewardPerPerson` 또는 모듈 확정값으로 넣어 같은 규칙 엔진을
재실행합니다. 새 판정이 `BLOCK`이거나 action 대상·규칙 버전이 바뀌면 stale proposal로
거절합니다.

AI 호출이 실패해도 Spring 규칙 엔진의 후보와 점검은 그대로 반환합니다. 설명만 정적 템플릿으로 대체합니다.

## 8. 계산 예시

초5, 30일 운영, 7일 간격, 학생 24명, 역할 3개를 8명씩 배정한다고 가정합니다.

- 지급 횟수: `floor((30 - 7) / 7) + 1 = 4회`
- 역할 가중치 0.8 / 1.0 / 1.3 → 급여 800 / 1,000 / 1,300
- 급여 발행액: `(800 + 1,000 + 1,300) × 8명 × 4회 = 99,200`
- 경제 퀴즈 보상 50, 24명, 8회 → `9,600`
- 보상 포함 전체 발행액: `99,200 + 9,600 = 108,800`
- 보상 비중: `9,600 / 108,800 = 약 8.82%` → 정상

초5 소득세 5%, 일반 상품, 구매 빈도 1, 부가세 10%, 희소성 보정 없음이라면:

```
net_ref = roundAmount(1,000 × 0.95) = 950
target_checkout = 950 × 0.30 = 285
pre_tax_price = roundAmount(285 / 1.10) = 259
```

저장 가격 259와 부가세 반올림액 26의 최종 결제액은 285입니다.

## 9. 경계값 예시

| 상황 | 결과 |
| --- | --- |
| 예금 금리 400 bps | 추천 구간 안 `PASS` |
| 예금 금리 500 bps | 허용 구간 안·추천 구간 밖 `WARN` |
| 예금 금리 601 bps | 허용 구간 밖 `BLOCK` |
| 대출 = 예금 + 200 bps | `PASS` 가능 |
| 대출 = 예금 + 199 bps | `BLOCK` `LOAN_DEPOSIT_SPREAD_VIOLATION` |
| 역할 모듈 미선택, 학생 직업 없음 | 정상, 그 자체로 점검 없음 |
| 소득세 모듈만 활성화 시도 | `BLOCK` `MODULE_PREREQUISITE_MISSING` |
| 보상 비중 정확히 30% | 상한 포함, `WARN` |
| 보상 비중 30% 초과 | `BLOCK` |
| 아무 모듈도 선택하지 않음 | `PASS` `NO_ECONOMIC_MODULES_SELECTED` |

## 10. Spring 단위 테스트 매트릭스

| 축 | 값 |
| --- | --- |
| 모듈 선택 | 없음 / 역할만 / 상점만 / 예금만 / 예금+대출 / 역할+소득세 / 여러 모듈 |
| 추천값 | 급여·세율·예금·대출·상품 가격·GAME 보상 |
| 범위 | 추천 최소·최대, 허용 최소·최대, 경계 ±1 최소 통화 단위 또는 bps |
| 학년 | 초4·초5·초6 |
| 일정 | 7/7=1, 30/7=4, 112/7=16, 첫 지급일 범위 밖, 불완전 마지막 주기 |
| 역할 | 미선택, 역할은 있으나 학생 없음, 일부 미배정, 전체 배정 |
| 보상 | 0%, 25%, 30%, 30% 초과, 기존 보상 합산, 비보상 발행액 0 |
| 판정 | `PASS·WARN·BLOCK` 및 `overall_severity` 파생 |

- 금액 1단위·비율 1 bps 저장 경계와 `HALF_UP` 반올림을 검증합니다.
- 같은 입력은 항상 같은 후보·범위·판정을 내야 합니다.
- 비활성 모듈의 후보가 생성되지 않는지 검증합니다.
- 모듈 활성화와 최초 정책 저장이 원자적으로 성공하거나 함께 롤백되는지 통합 테스트합니다.

## 11. FastAPI 전달 책임

Spring은 `candidates`와 `checks`를 계산해 FastAPI `SETUP_PRECHECK`에 전달합니다. 각 후보는 `proposed_value`, 추천 구간, 허용 구간, 단위, 근거 코드를 포함합니다.

FastAPI는 다음만 수행합니다.

- 후보·점검의 식별자와 수치를 변경하지 않고 교사용 설명 생성
- 모든 입력 후보와 점검이 응답에 보존됐는지 검증
- `overall_severity`를 개별 점검의 최댓값으로 파생·검증
- AI 실패 시 정적 설명으로 대체

최종 값 저장과 모듈 활성화는 Spring 적용 API가 최신 상태·정책 버전으로 다시 검증합니다.
