# 정식 선택 모듈 카탈로그

- 기준일: 2026-08-02
- 실행 정본: `backend/src/main/java/com/b201/ecovillage/classroom/domain/ModuleCatalog.java`
- 범위: 방 생성, 코스 추천, 모듈 활성화, 자율시간, 교사 웹과 OpenAPI의 module code

## 1. 저장·응답 규칙

- 신규 DB 행과 모든 API 응답은 아래 canonical code만 사용합니다.
- 입력 호환 별칭 `INCOME`과 `SALARY`는 `ROLE_SALARY`, `TAX`는 `INCOME_TAX`로 정규화한 뒤 저장합니다.
- `WALLET`은 선택 모듈이 아닙니다. 금액 기능이 실제로 지갑이나 원장을 처음 사용할 때 기술 인프라로 지연 생성합니다.
- 알 수 없는 코드와 `WALLET` 선택 요청은 `422 UNSUPPORTED_MODULE_CODE`로 거절합니다.
- 같은 요청에 별칭과 canonical code를 함께 보내 정규화 후 중복되면 유효성 오류로 거절합니다.

## 2. Canonical catalog

`금액 사용`은 모듈이 금액 값이나 원장 흐름을 사용할 수 있다는 뜻이며, 모듈 활성화만으로 빈 지갑을 미리 만든다는 뜻이 아닙니다.

| Canonical code | 표시명 | 금액 사용 | 운영 선수 모듈 | 활성화 시 초기 정책 |
| --- | --- | --- | --- | --- |
| `ROLE_SALARY` | 역할·급여 | 예 | 없음 | `SALARY` |
| `INCOME_TAX` | 소득세 | 예 | `ROLE_SALARY` | `INCOME_TAX_RATE` |
| `STORE` | 상점·소비 | 예 | 없음 | 없음 |
| `BUDGET` | 예산 관리 | 예 | 없음 | 없음 |
| `PAYSLIP` | 급여명세서 | 예 | 없음 | 없음 |
| `PUBLIC_FUND` | 공동기금 | 예 | 없음 | 없음 |
| `PUBLIC_BUDGET` | 공공예산·투표 | 예 | 없음 | 없음 |
| `DEPOSIT` | 정기예금 | 예 | 없음 | `DEPOSIT_RATE` |
| `SAVINGS` | 정기적금 | 예 | 없음 | 없음 |
| `CREDIT` | 신용 기초 | 아니요 | 없음 | 없음 |
| `LOAN` | 대출·상환 | 예 | `DEPOSIT` | `LOAN_RATE` |
| `INVESTMENT` | 가상 투자 | 예 | 없음 | 없음 |
| `RISK_MANAGEMENT` | 투자 위험관리 | 아니요 | 없음 | 없음 |
| `MARKET_PRICE` | 가격 변화 | 예 | 없음 | 없음 |
| `TRADE` | 개인거래 | 예 | 없음 | 없음 |
| `PHYSICAL_STORE` | 실제 상품 | 예 | 없음 | 없음 |
| `CHARACTER` | 캐릭터 | 예 | 없음 | 없음 |
| `QUIZ_GAME` | 게임형 퀴즈 | 예 | 없음 | 없음 |
| `QUIZ_TEST` | 평가형 퀴즈 | 아니요 | 없음 | 없음 |
| `AI_EVENT` | AI 경제 이벤트 | 예 | 없음 | 없음 |
| `CUMULATIVE_REPORT` | 누적 리포트 | 아니요 | 없음 | 없음 |

코스 추천의 `prerequisites`는 학습 순서를 설명하는 더 넓은 관계입니다. API 활성화를 차단하는 운영 선수 관계는 위 표의 `INCOME_TAX → ROLE_SALARY`, `LOAN → DEPOSIT` 두 관계입니다.

## 3. 이전 데이터

이미 적용된 `V1_12__seed_course_recommendations.sql`은 Flyway checksum을 보호하기 위해 수정하지 않습니다. `V1_15__canonical_module_catalog.sql`이 다음을 원자적으로 수행합니다.

1. 추천 코스와 학급·자율시간의 `INCOME`, `SALARY`, `TAX`를 canonical code로 변환합니다.
2. 별칭과 canonical 행이 함께 있으면 활성 상태·설정·버전을 보존해 하나로 병합합니다.
3. 추천 모듈과 선수 목록, 학급 선택 목록에서 `WALLET`을 제거합니다.
4. 세 저장 테이블에 canonical code 제약을 추가해 이후 임의 문자열 저장을 차단합니다.
