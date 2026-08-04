# 이코빌리지 HTTP API 명세

- 버전: v0.8
- 기준일: 2026-07-31
- 상태: 화면 행동 69건 HTTP 매핑·AI v1.2 선택형 모듈 계약·OpenAPI 3.1·API 필드 저장 매핑 완료
- 기준: [API 공통 규칙](API_COMMON_RULES.md), [화면 ID·행동 명세](SCREEN_SPEC.md), [역할·권한 매트릭스](PERMISSION_MATRIX.md), [핵심 상태 머신](STATE_MACHINES.md)

## 1. 범위와 읽는 법

이 문서는 학생 앱, 교사·관리자 웹과 내부 서비스가 사용하는 HTTP 작업을 정의합니다. 모든 경로의 기본 prefix는 `/api/v1`이며 표에는 prefix를 생략합니다.

- `STU`: 학생
- `OWN`: 학급 소유 교사
- `COT`: 공동 담당 교사
- `SAD`: 학교 관리자
- `POP`: 플랫폼 운영자
- `SYS`: 내부 작업자
- `DSP`: 공용 화면 세션
- `C`: `classroom_id`
- `S`: `student_id`
- `R`: 대상 리소스 ID

모든 변경 API는 [API 공통 규칙](API_COMMON_RULES.md)에 따라 `Idempotency-Key`를 받고, 상태 보유 리소스는 `expected_state_version`을 검증합니다. 표의 입력은 공통 필드를 제외한 도메인 필드만 표시합니다.

`PM · SM` 열의 `SM —`는 상태 머신이 필요한 도메인 전이가 아니라 인증 절차 또는 버전형 설정 변경임을 뜻합니다. 이 경우에도 정책·설정 버전 충돌은 검사합니다.

## 2. HTTP 작업 규칙

### 2.1 경로

- 학급 범위: `/classrooms/{classroom_id}/...`
- 학교 관리자 범위: `/schools/{school_id}/...`
- 플랫폼 운영 범위: `/platform/...`
- 인증 전·현재 세션: `/auth/...`
- 비동기 작업 조회: `/operations/{operation_id}`

### 2.2 명령과 설정

- 새 업무 리소스 생성은 `POST`를 사용합니다.
- 전체 설정·정책 저장은 `PUT`을 사용하고 현재 `policy_version` 또는 `state_version`을 검증합니다.
- 상태 전이는 `POST .../commands`와 명시적인 `command` 열거형을 사용합니다.
- 완료 거래·원장·감사 기록을 수정하거나 삭제하는 API는 만들지 않습니다.
- 명령 응답은 변경된 리소스 ID, 상태, `state_version`, 관련 거래번호 또는 `operation_id`를 반환합니다.

### 2.3 공통 명령 예시

```json
{
  "command": "APPROVE",
  "expected_state_version": 4,
  "reason": "거래 내용과 소유권을 확인함",
  "screen_id": "SCR-TCH-009",
  "ui_action_id": "UIA-TRD-004",
  "client_occurred_at": "2026-07-29T06:00:00.000Z"
}
```

`role`, `actor_id`, 현재 가격·세율·잔액·소유권은 본문으로 받지 않습니다. 서버가 토큰, 소속, 현재 정책과 원장에서 다시 판정합니다.

## 3. 인증·세션 API

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-AUTH-001 · UIA-AUTH-001 | `POST /auth/student-sessions` | 인증 전 STU | `classroom_code, attendance_number, nickname, password, device_id` → 토큰·학생 세션·유효 진입 화면 | PM-AUTH-001 · SM — | `INVALID_CREDENTIALS`, `CLASS_NOT_ACTIVE`, `RATE_LIMITED` | AUTH-001·AUTH-003·AUTH-005·AUTH-007 |
| API-AUTH-002 · UIA-AUTH-002 | `POST /auth/student-password-reset-requests` | 인증 전 STU | 기존 본인 확인 입력 → `reset_request_id, next_step` | PM-AUTH-002 · SM — | `IDENTITY_VERIFICATION_FAILED`, `RESOURCE_NOT_FOUND` | AUTH-006 |
| API-AUTH-003 · UIA-AUTH-003 | `POST /classrooms/{C}/students/{S}/password-reset-commands` | OWN·COT | `command=COMPLETE, reset_request_id, new_password, reason` → 원문 비밀번호 비저장·일회용 재설정 결과 | PM-AUTH-002 · SM — | `CLASS_NOT_ASSIGNED`, `INVALID_RESET_REQUEST`, `VALIDATION_ERROR` | AUTH-006 |
| API-AUTH-008 · UIA-AUTH-004 | `POST /auth/teacher-sessions` | 인증 전 교사 | OIDC Authorization Code + PKCE 완료 후 받은 `id_token` → 등록 교사 확인, 본문의 단기 액세스 토큰과 HttpOnly 갱신 쿠키, 교사 다중 브라우저 세션 | PM-AUTH-003 · SM — | `INVALID_ID_TOKEN`, `TEACHER_NOT_REGISTERED`, `ACCOUNT_INACTIVE`, `AUTH_PROVIDER_UNAVAILABLE` | AUTH-002·AUTH-008 |

### 3.1 세션 지원 API

| API ID | 메서드·경로 | 설명 |
| --- | --- | --- |
| API-AUTH-004 | `POST /auth/token-refreshes` | STU는 SecureStore의 갱신 토큰을 `X-Refresh-Token`으로 보내고 새 액세스·갱신 토큰을 본문으로 받는다. TCH는 HttpOnly 쿠키를 보내고 새 액세스 토큰만 본문으로 받으며 갱신 쿠키는 회전된다. 두 전송 수단을 함께 보내면 거절한다 |
| API-AUTH-005 | `DELETE /auth/sessions/current` | 현재 앱 또는 웹 세션 폐기 |
| API-AUTH-006 | `GET /auth/me` | 현재 세션·사용자·역할·학교·담당 학급과 액세스 토큰 만료 조회 |
| API-AUTH-007 | `POST /auth/realtime-tickets` | 인증 세션과 요청 채널 범위로 60초·1회용 WebSocket 접속 티켓 발급 |

비밀번호·토큰·재설정 증명값은 응답 이후 다시 조회할 수 없고 감사 로그에도 저장하지 않습니다.

## 4. 경제교실·운영 API

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-CRS-001 | `GET /course-recommendations?grade={4..6}&course_size={label}` | 인증 교사 | 학년·규모를 함께 지정하면 해당 조합, 둘 다 생략하면 전체 12개 → 버전·난이도·학습 중점·핵심/선택/기반 모듈·선수개념·제외 영향 | PM-CLS-005 · SM — | `AUTH_REQUIRED`, `ROLE_NOT_ALLOWED`, `VALIDATION_ERROR`, `COURSE_VERSION_MISSING` | CRS-003·CRS-004·CRS-005 |
| API-CLS-001 · UIA-CLS-001 | `POST /classrooms` | OWN | 학교, 학년, 규모 라벨, 실제 기간, 인원, 0개 이상의 선택 `module_codes`, 자율시간, 필수 `currency_name` → 서버 생성 전역 유일 `classroom_code`, `ACTIVE` 학급, 선택 모듈의 `DISABLED` 등록 상태 | PM-CLS-001·PM-CLS-005·PM-CLS-007 · SM-CLS-001·SM-MOD-001 | `INVALID_CURRENCY_NAME`, `CLASSROOM_CODE_GENERATION_FAILED`, `VALIDATION_ERROR`, `POLICY_VIOLATION` | AUTH-007·CLS-001·CLS-010·CRS-001·CRS-002 |
| API-CLS-002 · UIA-CLS-002 | `PUT /classrooms/{C}/course-configuration` | OWN | 학년·규모·기간·코스·난이도 → 새 설정 버전, 학급 코드·화폐 단위명 변경 불가 | PM-CLS-005 · SM-CLS-001 | `IMMUTABLE_CLASSROOM_FIELD`, `CLASS_NOT_ACTIVE`, `ACTIVITY_IN_PROGRESS`, `POLICY_VERSION_CONFLICT` | CRS-001·CRS-003·CRS-004·CRS-005 |
| API-CLS-003 · UIA-CLS-003 | `PUT /classrooms/{C}/roster-and-salaries` | OWN | 학생 번호·닉네임, 역할·급여 모듈 활성 시 선택형 역할·역할급여 → 검증된 명단·정책 버전 | PM-CLS-004·PM-CLS-005 · SM-CLS-001·SM-MOD-001 | `DUPLICATE_ATTENDANCE_NUMBER`, `DUPLICATE_NICKNAME`, `MODULE_NOT_ACTIVE`, `CLASS_NOT_ACTIVE` | CLS-001·ECO-004 |
| API-CLS-004 · UIA-CLS-004 | `POST /classrooms/{C}/co-teacher-commands` | OWN | `command=INVITE\|REMOVE, teacher_id, reason` → 담당 교사 목록 | PM-CLS-002 · SM-CLS-001 | `TEACHER_NOT_IN_SCHOOL`, `OWNER_REQUIRED`, `LAST_OWNER_REQUIRED` | AUTH-002 |
| API-CLS-005 · UIA-CLS-005 | `POST /classrooms/{C}/ownership-transfer-commands` | OWN·대상 COT | `command=REQUEST\|ACCEPT\|CANCEL, transfer_id?, target_teacher_id?` → OWN·COT 원자 교환 | PM-CLS-003 · SM-CLS-001 | `TARGET_NOT_CO_TEACHER`, `TRANSFER_EXPIRED`, `INVALID_STATE_TRANSITION` | AUTH-002 |
| API-CLS-006 · UIA-CLS-006 | `PUT /classrooms/{C}/autonomous-schedules` | OWN | 시간대, 요일·현지 시작/종료, 사용 모듈 → 일정 버전 | PM-CLS-007 · SM-APP-001 | `INVALID_TIME_RANGE`, `POLICY_VERSION_CONFLICT` | CLS-004·CLS-005 |
| API-CLS-007 · UIA-CLS-007 | `POST /classrooms/{C}/access-mode-commands` | OWN·COT | `command=OPEN\|CLOSE\|EXTEND, duration_minutes?, preflight_run_id?, preflight_warning_acknowledgements?` → 첫 OPEN은 유효한 `CLASSROOM_SETUP` 실행 ID가 필수이고 WARN의 모든 `check_id` 확인 후 경제 설정 점검과 계산된 앱 모드·종료시각 반환 | PM-CLS-008 · SM-APP-001·SM-AIR-001 | `PREFLIGHT_CHECK_REQUIRED`, `PREFLIGHT_WARNING_ACKNOWLEDGEMENT_REQUIRED`, `PREFLIGHT_BLOCKED`, `GUIDED_MODE_ACTIVE`, `CLASS_NOT_ACTIVE`, `STATE_VERSION_CONFLICT` | CLS-003·CLS-004·CLS-005·CLS-006·AI-009 |
| API-CLS-008 · UIA-CLS-008 | `POST /classrooms/{C}/closure-operations` | OWN | 재인증 증명, 영향 확인, 사유 → `202 operation_id`, 학급 `CLOSING` | PM-CLS-006·PM-FIN-009 · SM-CLS-001 | `REAUTHENTICATION_REQUIRED`, `CLOSURE_ALREADY_RUNNING`, `INVALID_STATE_TRANSITION` | CLS-007·CLS-008·FIN-012 |

### 4.1 모듈·정책

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-MOD-001 · UIA-MOD-001 | `POST /classrooms/{C}/modules/{module_code}/activation-commands` | OWN | `expected_state_version, confirm_prerequisites=true, initial_policy_values?, recommendation_action_ids?` → 조건부 선행 관계·초기 정책 검증 후 정책 저장과 `DISABLED → ACTIVE` 원자 처리 | PM-MOD-001·PM-AI-003 · SM-MOD-001·SM-AIR-001 | `PREREQUISITE_MODULE_REQUIRED`, `INITIAL_POLICY_REQUIRED`, `POLICY_VIOLATION`, `GRADE_POLICY_VIOLATION`, `STATE_VERSION_CONFLICT`, `INVALID_STATE_TRANSITION` | CLS-002·CRS-005·CRS-006·AI-009 |
| API-MOD-002 · UIA-MOD-002 | `POST /classrooms/{C}/modules/{module_code}/stop-commands` | OWN | `reason` → `ACTIVE → STOPPING`, 정리 작업 | PM-MOD-001 · SM-MOD-001 | `LIVE_ACTIVITY_EXISTS`, `MODULE_NOT_ACTIVE`, `SETTLEMENT_REQUIRED` | CLS-002·TRD-004 |
| API-POL-001 · UIA-POL-001 | `PUT /classrooms/{C}/economic-policy` | OWN | 가격·재고·보상·세율·물가·예금·대출 금리·한도 → 새 정책 버전 | PM-POL-001 · SM-CLS-001 | `POLICY_VERSION_CONFLICT`, `POLICY_VIOLATION` | FIN-011·AI-003 |
| API-POL-002 · UIA-POL-002 | `PUT /classrooms/{C}/public-fund-policy` | OWN | 세금·기금 기본/커스텀 사용처와 예산 항목 → 정책 버전 | PM-POL-002 · SM-CLS-001 | `INVALID_BUDGET_ALLOCATION`, `POLICY_VERSION_CONFLICT` | ECO-008·ECO-009 |

### 4.2 지도 활동·공용 화면

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-ACT-001 · UIA-ACT-001 | `POST /classrooms/{C}/guided-activities/{R}/start-commands` | OWN·COT | `participant_ids, public_fields` → `READY → LIVE`, GUIDED 계산 | PM-ACT-001 · SM-ACT-001·SM-APP-001 | `PARTICIPANT_INVALID`, `ACTIVITY_ALREADY_LIVE`, `STATE_VERSION_CONFLICT` | CLS-009 |
| API-ACT-002 · UIA-ACT-002 | `POST /classrooms/{C}/guided-activities/{R}/finish-commands` | OWN·COT | `command=END\|CANCEL, reason?` → `FINALIZING` 또는 `CANCELED` | PM-ACT-001 · SM-ACT-001·SM-APP-001 | `INVALID_STATE_TRANSITION`, `FINALIZATION_REQUIRED` | CLS-009 |
| API-DSP-001 · UIA-DSP-001 | `POST /classrooms/{C}/guided-activities/{R}/display-session-commands` | OWN·COT | `command=CONNECT\|DISCONNECT, display_session_id?` → 단기 공개 연결 토큰 또는 폐기 | PM-DSP-001 · SM-ACT-001 | `PUBLIC_DATA_ONLY`, `ACTIVITY_NOT_LIVE`, `SESSION_REVOKED` | AUTH-004·CLS-009 |

## 5. 지갑·상점·개인거래 API

### 5.1 원장·급여

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-TXN-001 · UIA-TXN-001 | `POST /classrooms/{C}/payouts` | OWN·COT | 지급 유형, 대상 학생, 급여 회차 키(`period_key`), 정책 기준액, 사유 → 원장 거래 그룹·명세서 | PM-TXN-002 · SM-TXN-001 | `DUPLICATE_PAYOUT`, `POLICY_VIOLATION`, `CLASS_NOT_ACTIVE` | ECO-001·ECO-004 |
| API-TXN-002 · UIA-TXN-002 | `POST /classrooms/{C}/ledger-transactions/{R}/recoveries` | OWN·COT | 복구 유형, 금액·아이템 조정, 필수 사유 → 원거래 연결 복구 거래 | PM-TXN-003 · SM-TXN-001 | `ORIGINAL_TRANSACTION_NOT_COMMITTED`, `RECOVERY_ALREADY_EXISTS`, `VALIDATION_ERROR` | ECO-002·ECO-006·QZ-009 |

### 5.2 상점·캐릭터

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-SHP-001 · UIA-SHP-001 | `POST /classrooms/{C}/store/product-commands` | OWN | `expected_state_version, expected_policy_version`, 수정 시 `expected_product_version`, `command=CREATE\|UPDATE\|PUBLISH\|UNPUBLISH`, 상품·가격·부가세·재고·유형 → `product_id, status, state_version, version` | PM-SHP-001 · SM-CLS-001 | `STATE_VERSION_CONFLICT`, `POLICY_VERSION_CONFLICT`, `PRODUCT_VERSION_CONFLICT`, `PRICE_OUT_OF_RANGE`, `INVALID_STOCK`, `INVALID_TAX_RATE`, `CHARACTER_NOT_APPROVED` | ECO-005·ECO-007 |
| API-SHP-002 · UIA-SHP-002 | `POST /classrooms/{C}/store/orders` | STU | `product_id, quantity, expected_product_version` → 결제·세금·지급 또는 수령 대기 주문 | PM-SHP-002 · SM-SHP-001·SM-TXN-001 | `INSUFFICIENT_BALANCE`, `OUT_OF_STOCK`, `PRICE_CHANGED`, `APP_MODE_BLOCKED` | ECO-001·ECO-005·ECO-006 |
| API-SHP-003 · UIA-SHP-003 | `POST /classrooms/{C}/store/orders/{R}/ready-for-pickup-commands` | OWN·COT | 준비 완료 → `PAID → READY_FOR_PICKUP` | PM-SHP-003 · SM-SHP-001 | `INVALID_STATE_TRANSITION`, `ORDER_NOT_PHYSICAL` | ECO-005 |
| API-SHP-004 · UIA-SHP-004 | `POST /classrooms/{C}/store/orders/{R}/receipt-confirmations` | OWN·COT | 학생 확인 정보 → `READY_FOR_PICKUP → RECEIVED` | PM-SHP-003 · SM-SHP-001 | `INVALID_STATE_TRANSITION`, `STUDENT_MISMATCH` | ECO-005 |
| API-ITM-001 · UIA-ITM-001 | `POST /classrooms/{C}/inventory-items/{R}/commands` | STU | `command=EQUIP\|UNEQUIP` → `AVAILABLE ↔ EQUIPPED` | PM-ITM-001 · SM-ITM-001 | `RESOURCE_NOT_OWNED`, `ITEM_LOCKED`, `INVALID_STATE_TRANSITION` | ECO-007·TRD-002 |
| API-CNT-001 · UIA-CNT-001 | `POST /platform/character-sources/{R}/review-commands` | POP | `command=APPROVE\|REJECT\|PUBLISH\|UNPUBLISH`, 출처·지역·권리·검토일·증적 → 전역 게시 상태 | PM-CNT-001 · SM — | `RIGHTS_EVIDENCE_REQUIRED`, `REGION_NOT_REVIEWED` | ECO-010 |
| API-CNT-002 · UIA-CNT-002 | `PUT /classrooms/{C}/character-catalog/{R}` | OWN | 전역 캐릭터 ID, 학급 표시명·판매 여부 → 학급 카탈로그 항목 | PM-CNT-001·PM-SHP-001 · SM-CLS-001 | `CHARACTER_NOT_PUBLISHED`, `OWNER_REQUIRED` | ECO-007·ECO-010 |

### 5.3 학생 개인거래

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-TRD-001 · UIA-TRD-001 | `POST /classrooms/{C}/trades` | STU | 상대 학생, 주고받을 금액·아이템, 설명 → `AWAITING_COUNTERPART`, 아이템 잠금 | PM-TRD-001 · SM-TRD-001·SM-ITM-001 | `SELF_TRADE_FORBIDDEN`, `ITEM_LOCKED`, `RESOURCE_NOT_OWNED` | TRD-001·TRD-002·TRD-005 |
| API-TRD-002 · UIA-TRD-002 | `POST /classrooms/{C}/trades/{R}/proposer-commands` | 제안 STU | `command=CANCEL` → 취소·아이템 잠금 해제 | PM-TRD-001 · SM-TRD-001·SM-ITM-001 | `INVALID_STATE_TRANSITION`, `RESOURCE_NOT_OWNED` | TRD-001·TRD-002 |
| API-TRD-003 · UIA-TRD-003 | `POST /classrooms/{C}/trades/{R}/counterpart-decisions` | 상대 STU | `decision=ACCEPT\|REJECT, reason?` → 교사 대기 또는 취소·잠금 해제 | PM-TRD-002 · SM-TRD-001·SM-ITM-001 | `NOT_TRADE_COUNTERPART`, `INVALID_STATE_TRANSITION` | TRD-001·TRD-002 |
| API-TRD-004 · UIA-TRD-004 | `POST /classrooms/{C}/trades/{R}/teacher-decisions` | OWN·COT | `decision=APPROVE\|REJECT, reason` → 세금·금액·아이템 원자 이전 또는 취소 | PM-TRD-003 · SM-TRD-001·SM-ITM-001·SM-TXN-001 | `APPROVAL_REQUIRED`, `INSUFFICIENT_BALANCE`, `OWNERSHIP_CHANGED`, `POLICY_VERSION_CONFLICT` | TRD-001·TRD-002·TRD-003·TRD-005 |

## 6. 금융 API

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-FIN-001 · UIA-FIN-001 | `POST /classrooms/{C}/financial-product-commands` | OWN | `command=CREATE\|UPDATE\|PUBLISH\|STOP`, 예금·적금 조건 → 상품 버전 | PM-FIN-001 · SM-CLS-001 | `INVALID_RATE`, `INVALID_SCHEDULE`, `ACTIVE_ACCOUNT_POLICY_IMMUTABLE` | FIN-001·FIN-002·FIN-011 |
| API-FIN-002 · UIA-FIN-002 | `POST /classrooms/{C}/deposit-accounts` | STU | `product_id, principal, expected_product_version` → `ACTIVE` 계좌·출금 원장 | PM-FIN-002 · SM-DEP-001·SM-TXN-001 | `INSUFFICIENT_BALANCE`, `PRODUCT_NOT_ACTIVE`, `APP_MODE_BLOCKED` | FIN-001 |
| API-FIN-003 · UIA-FIN-003 | `POST /classrooms/{C}/deposit-accounts/{R}/commands` | STU | `command=EARLY_TERMINATE` → 지급 원장·`EARLY_TERMINATED` | PM-FIN-002 · SM-DEP-001·SM-TXN-001 | `INVALID_STATE_TRANSITION`, `RESOURCE_NOT_OWNED` | FIN-001 |
| API-FIN-004 · UIA-FIN-004 | `POST /classrooms/{C}/savings-accounts` | STU | `product_id, installment_amount` → 계좌·2~12회 일정·첫 납입 | PM-FIN-002 · SM-SAV-001·SM-SAV-002·SM-TXN-001 | `INSUFFICIENT_BALANCE`, `INVALID_SCHEDULE`, `PRODUCT_NOT_ACTIVE` | FIN-002·FIN-004 |
| API-FIN-005 · UIA-FIN-005 | `POST /classrooms/{C}/savings-accounts/{R}/installments/{installment_id}/commands` | STU | `command=RETRY_PAYMENT` → `MISSED → PAID`와 원장 | PM-FIN-002 · SM-SAV-002·SM-TXN-001 | `RETRY_WINDOW_EXPIRED`, `INSUFFICIENT_BALANCE`, `INVALID_STATE_TRANSITION` | FIN-003·FIN-004 |
| API-FIN-006 · UIA-FIN-006 | `POST /classrooms/{C}/savings-accounts/{R}/commands` | STU | `command=EARLY_TERMINATE` → 지급 원장·`EARLY_TERMINATED` | PM-FIN-002 · SM-SAV-001·SM-TXN-001 | `INVALID_STATE_TRANSITION`, `RESOURCE_NOT_OWNED` | FIN-004 |
| API-FIN-007 · UIA-FIN-007 | `PUT /classrooms/{C}/investment-market/scenario` | OWN | `investment_mode=SHORT_TERM\|LONG_TERM, prefer_historical_index` → 서버가 가상자산 4종과 숨은 가격 단계를 생성한 DRAFT 시나리오. 단기는 AI 생성 2단계 흐름, 장기는 FRED S&P 500 역사 흐름을 우선 조회하고 실패하면 고정 AI 생성 흐름으로 폴백하며 출처·기간을 판본에 보존 | PM-FIN-001 · SM-IVT-001 | `MARKET_NOT_DRAFT`, `INVALID_PRICE_STEP`, `INVALID_INVESTMENT_MODE`, `INVESTMENT_MODE_MISMATCH` | FIN-005·FIN-007·AI-011 |
| API-FIN-008 · UIA-FIN-008 | `POST /classrooms/{C}/investment-market/commands` | OWN·COT | 6단계 연결 범위의 `command=START, expected_state_version` → 시나리오 게시·첫 가격 공개·시장 OPEN. 후속 `ADVANCE_DAY\|RESUME\|PAUSE\|CLOSE`와 정산은 다음 단계 범위 | PM-FIN-004 · SM-IVT-001·SM-TXN-001 | `INVALID_STATE_TRANSITION`, `STATE_VERSION_CONFLICT`, `SETTLEMENT_FAILED` | FIN-005·FIN-007·FIN-012 |
| API-FIN-009 · UIA-FIN-009 | `POST /classrooms/{C}/investment-orders` | STU | 6단계 연결 범위의 `side=BUY, asset_id, quantity, expected_price_version` → 현재 공개 가격 재검증 후 학생 지갑·투자 청산 계정 원장과 주문·보유량을 원자 체결. SELL과 자동 정산은 다음 단계 범위 | PM-FIN-003 · SM-IVT-002·SM-TXN-001 | `MARKET_NOT_OPEN`, `PRICE_CHANGED`, `INSUFFICIENT_BALANCE`, `LIMIT_EXCEEDED` | FIN-006·FIN-007 |
| API-FIN-010 · UIA-FIN-010 | `PUT /classrooms/{C}/loan-policy` | OWN | 한도·기간금리·회차·유예·신용 조정 → 신규 신청 정책 버전 | PM-FIN-001 · SM-LON-001·SM-CRD-001 | `INVALID_RATE`, `POLICY_VERSION_CONFLICT` | FIN-008·FIN-009·FIN-010·FIN-011 |
| API-FIN-011 · UIA-FIN-011 | `POST /classrooms/{C}/loan-application-commands` | STU | `command=APPLY\|CANCEL, application_id?, amount?, installments?` → `APPLIED` 또는 취소 | PM-FIN-005 · SM-LON-001 | `CREDIT_RESTRICTED`, `ACTIVE_LOAN_LIMIT`, `INVALID_STATE_TRANSITION` | FIN-008·FIN-010 |
| API-FIN-012 · UIA-FIN-012 | `POST /classrooms/{C}/loan-applications/{R}/teacher-decisions` | OWN·COT | `decision=APPROVE\|REJECT, reason` → 승인 시 원금·계좌 원자 생성 | PM-FIN-006 · SM-LON-001·SM-TXN-001 | `CREDIT_CHANGED`, `ACTIVE_LOAN_LIMIT`, `POLICY_VERSION_CONFLICT` | FIN-008·FIN-010 |
| API-FIN-013 · UIA-FIN-013 | `POST /classrooms/{C}/loans/{R}/repayment-commands` | STU | `command=RETRY_DUE_PAYMENT\|PREPAY_ALL` → 상환 원장·상태·신용 재계산 | PM-FIN-007 · SM-LON-001·SM-CRD-001·SM-TXN-001 | `INSUFFICIENT_BALANCE`, `GRACE_WINDOW_EXPIRED`, `INVALID_STATE_TRANSITION` | FIN-009·FIN-010 |

## 7. 퀴즈·투표 API

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-QZ-001 · UIA-QZ-001 | `POST /classrooms/{C}/quiz-question-commands` | OWN·COT | `command=CREATE\|UPDATE`, 유형·문항·선택지·정답·해설 → DRAFT 또는 새 버전 | PM-QZ-001 · SM-QST-001 | `QUESTION_ALREADY_PUBLISHED`, `AMBIGUOUS_ANSWER`, `VALIDATION_ERROR` | QZ-001·QZ-002·QZ-007·QZ-008 |
| API-QZ-002 · UIA-QZ-002 | `POST /classrooms/{C}/ai/quiz-draft-operations` | OWN·COT | 학년·주제·ECONOMY/NONSENSE·객관식/OX·개수 → `202 operation_id, ai_run_id, question_ids`, REVIEW_REQUIRED 초안 | PM-QZ-001·PM-AI-002 · SM-AIR-001·SM-QST-001 | `AI_DISABLED`, `AI_DAILY_LIMIT_EXCEEDED`, `AI_TIMEOUT` | QZ-006·QZ-007·AI-001 |
| API-QZ-003 · UIA-QZ-003 | `POST /classrooms/{C}/quiz-questions/{R}/commands` | OWN·COT | `command=APPROVE\|PUBLISH\|RETIRE, reason?` → 허용 상태 전이 | PM-QZ-001 · SM-QST-001 | `REVIEW_REQUIRED`, `INVALID_STATE_TRANSITION`, `CONTENT_POLICY_VIOLATION` | QZ-002·QZ-007·QZ-008 |
| API-QZ-004 · UIA-QZ-004 | `POST /classrooms/{C}/quiz-sessions/{R}/commands` | OWN·COT | `command=START\|UPDATE_REWARD\|CLOSE_SUBMISSIONS`, 문제 버전·참가자·GAME 보상·공개범위 → `OPEN` 또는 `SCORING`. `UPDATE_REWARD`는 `OPEN` GAME과 일치하는 `expected_state_version`에서만 허용 | PM-QZ-002 · SM-QZS-001·SM-ACT-001 | `QUESTION_NOT_PUBLISHED`, `INVALID_REWARD_POLICY`, `FORBIDDEN_REWARD_CONTEXT`, `STATE_VERSION_CONFLICT`, `INVALID_STATE_TRANSITION` | QZ-001·QZ-003·QZ-004·QZ-005 |
| API-QZ-005 · UIA-QZ-005 | `POST /classrooms/{C}/quiz-sessions/{R}/game-answers` | STU | `question_id, option_id, client_answered_at` → 서버 접수시각·답안 상태 | PM-QZ-002 · SM-QZS-001 | `SUBMISSION_CLOSED`, `NOT_PARTICIPANT`, `ANSWER_ALREADY_SUBMITTED` | QZ-003 |
| API-QZ-006 · UIA-QZ-006 | `PUT /classrooms/{C}/quiz-sessions/{R}/test-answer-sheet` | STU | 문항별 답안과 저장 버전 → 개인 답안지 버전·제출 상태 | PM-QZ-002 · SM-QZS-001 | `SUBMISSION_CLOSED`, `NOT_EXAMINEE`, `STATE_VERSION_CONFLICT` | QZ-004 |
| API-QZ-007 · UIA-QZ-007 | `POST /classrooms/{C}/quiz-sessions/{R}/result-commands` | OWN·COT | `command=CONFIRM\|PUBLISH\|CLOSE, leaderboard_public?` → 결과 버전·보상 원장·공개 상태. `leaderboard_public=true`는 GAME에서만 허용하고 TEST는 항상 `false` | PM-QZ-002 · SM-QZS-001·SM-TXN-001 | `SCORING_NOT_FINISHED`, `INVALID_STATE_TRANSITION`, `PUBLIC_DATA_ONLY` | QZ-003·QZ-004·QZ-005 |
| API-QZ-008 · UIA-QZ-008 | `POST /classrooms/{C}/quiz-sessions/{R}/question-issue-reports` | STU | `question_id, reason_code, description` → `UNDER_REVIEW` 신고 | PM-QZ-003 · SM-QAD-001 | `NOT_EXAMINEE`, `DUPLICATE_REPORT`, `VALIDATION_ERROR` | QZ-009 |
| API-QZ-009 · UIA-QZ-009 | `POST /classrooms/{C}/quiz-sessions/{R}/regrade-operations` | OWN·COT | 무효 문항, 판정 사유 → `202 operation_id`, 새 결과 버전·복구 거래 | PM-QZ-002·PM-QZ-003·PM-TXN-003 · SM-QAD-001·SM-QZS-001·SM-TXN-001 | `QUESTION_NOT_UNDER_REVIEW`, `REGRADING_ALREADY_RUNNING` | QZ-009 |
| API-VOT-001 · UIA-VOT-001 | `POST /classrooms/{C}/votes/{R}/commands` | OWN·COT | `command=CREATE\|OPEN\|CLOSE\|CONFIRM_RESULT, title?, description?, options?[], participant_ids?[], public_display_enabled?, selected_use?` → 예산안 생성·익명 투표 진행·기금 집행 | PM-ACT-002 · SM-ACT-001·SM-TXN-001 | `INVALID_STATE_TRANSITION`, `VOTE_COUNT_MISMATCH`, `BUDGET_EXCEEDS_FUND`, `INSUFFICIENT_BALANCE`, `NOT_PARTICIPANT` | ECO-008 |
| API-VOT-002 · UIA-VOT-002 | `POST /classrooms/{C}/votes/{R}/ballots` | STU | `option_id` → 익명 투표 접수 영수증 | PM-ACT-001 · SM-ACT-001 | `VOTE_NOT_LIVE`, `ALREADY_VOTED`, `NOT_PARTICIPANT` | ECO-008 |

## 8. AI·데이터 API

### 8.1 교사용 AI

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-AI-001 · UIA-AI-001 | `POST /classrooms/{C}/ai/chat-requests` | OWN·COT | `question, period, metric_scopes, include_policy_history?` → `ai_run_id, answer, period, data_as_of, evidence, recommendations` | PM-AI-001·PM-DAT-001 · SM-AIR-001 | `AI_DISABLED`, `AI_DAILY_LIMIT_EXCEEDED`, `AI_TIMEOUT`, `INDIVIDUAL_ANALYSIS_FORBIDDEN` | AI-001·AI-005·AI-006 |
| API-AI-002 · UIA-AI-002 | `POST /classrooms/{C}/ai/balancing-analysis-operations` | OWN·COT | `analysis_mode=SETUP_PRECHECK\|ON_DEMAND\|AUTO_MONITOR, trigger_context?, period?, metric_scopes?` → `202 operation_id`, 규칙 기반 추천·점검 또는 운영 제안 목록 | PM-AI-003 · SM-AIR-001 | `INSUFFICIENT_ANALYSIS_DATA`, `AI_DISABLED`, `ANALYSIS_ALREADY_RUNNING`, `INVALID_RECOMMENDATION_CONTEXT` | AI-001·AI-003·AI-004·AI-009·AI-010 |
| API-AI-003 · UIA-AI-003 | `POST /classrooms/{C}/ai/balancing-proposals/{R}/commands` | OWN, COT는 확인만 | `command=APPLY\|REJECT\|ACKNOWLEDGE, edited_values?, reason, expected_policy_version?` → 설정 점검 제안은 기존 모듈·상품·퀴즈 명령에, 운영 밸런싱 제안은 허용 범위와 최신 대상값을 재검증한 뒤 상품 가격·재고와 예금·적금·대출 금리의 새 버전에 원자 적용하고 `proposal_id, ai_run_id, decision_status, policy_version?, applied_values, state_version` 반환 | PM-AI-004·PM-POL-001 · SM-AIR-001 | `OWNER_REQUIRED`, `POLICY_VERSION_CONFLICT`, `STATE_VERSION_CONFLICT`, `STALE_PROPOSAL`, `PROPOSAL_EXPIRED`, `ALREADY_ACKNOWLEDGED`, `FORBIDDEN_REWARD_CONTEXT` | AI-002·AI-003·AI-009·AI-010·FIN-011 |
| API-AI-004 · UIA-AI-004 | `POST /classrooms/{C}/ai/economic-event-draft-operations` | OWN·COT | `source_type=TEACHER_REQUEST\|MONITOR_RECOMMENDATION, source_proposal_id?, template_type=PRICE\|SUPPLY\|TAX\|INTEREST_RATE\|INVESTMENT_NEWS\|REWARD\|PUBLIC_FUND, interest_rate_scope?, teacher_context?, investment_news_targets?` → `202 operation_id` | PM-AI-002 · SM-AIR-001 | `FORBIDDEN_EVENT_EFFECT`, `GRADE_POLICY_VIOLATION`, `AI_DISABLED`, `DUPLICATE_EVENT_RECOMMENDATION`, `INVALID_INVESTMENT_STEP_BINDING` | AI-001·AI-007·AI-011 |
| API-AI-005 · UIA-AI-005 | `PUT /classrooms/{C}/ai-settings` | OWN | `enabled=true\|false, reason` → 학급 AI 설정 버전 | PM-AI-005 · SM-AIR-001 | `OWNER_REQUIRED`, `AI_APPLICATION_IN_PROGRESS`, `POLICY_VERSION_CONFLICT` | AI-001 |
| API-AI-006 · UIA-AI-004 | `POST /classrooms/{C}/ai/economic-events/{R}/decisions` | OWN·COT | `decision=APPROVE\|REJECT, edits?, reason, expected_event_version` → 일반 이벤트는 게시 상태, 투자 뉴스는 연결 가격 단계의 공개 대기 상태 | PM-AI-002 · SM-AIR-001·SM-ACT-001·SM-IVT-001 | `FORBIDDEN_EVENT_EFFECT`, `GRADE_POLICY_VIOLATION`, `PROPOSAL_EXPIRED`, `INVALID_INVESTMENT_STEP_BINDING` | AI-001·AI-002·AI-007·AI-011 |

`SETUP_PRECHECK`는 학생·거래 최소 표본 규칙을 적용하지 않습니다. 개설 마법사에서는 공통 필수값만 점검하고, 역할·급여·소득세·예금·대출은 해당 모듈 활성화 문맥에서 초기 정책을 추천합니다. 상품 작성 문맥에서는 가격, 퀴즈 세션 작성 문맥에서는 GAME 보상만 추천합니다. `ON_DEMAND`와 `AUTO_MONITOR`만 운영 지표와 AI-004 데이터 충분성 규칙을 사용합니다. 추천 수치는 Spring 규칙 엔진이 계산하며 AI 실패 시에도 수치·정적 점검 결과는 반환합니다.

`AUTO_MONITOR`는 사용자 호출이 아니라 서버 스케줄러가 같은 애플리케이션 서비스를 실행하는 트리거입니다. `NORMAL`은 제안을 만들지 않고 실행만 완료하며 `OBSERVE·WARN`은 교사 확인 대상을 만듭니다. 설정·정책은 `API-AI-003 command=APPLY` 전까지 변경되지 않습니다.

### 8.2 데이터·내보내기

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-DAT-001 · UIA-DAT-001 | `POST /classrooms/{C}/export-operations` | OWN·COT | `format=PDF\|CSV, report_type, period, student_scope?, reason?` → `202 operation_id` | PM-DAT-003 · SM-CLS-001 | `RETENTION_PERIOD_EXPIRED`, `EXPORT_SCOPE_FORBIDDEN`, `ASYNC_QUEUE_UNAVAILABLE` | DAT-001·DAT-003 |
| API-DAT-002 · UIA-DAT-002 | `POST /classrooms/{C}/template-copy-operations` | OWN·COT | 원본 학급, 코스·모듈·승인 퀴즈 선택 → `202 operation_id`, 새 초안 | PM-DAT-004 · SM-CLS-001 | `STUDENT_DATA_COPY_FORBIDDEN`, `SOURCE_NOT_ACCESSIBLE` | DAT-002·DAT-003 |

## 9. 학교 관리자·플랫폼 운영 API

| API ID · UIA | 메서드·경로 | 역할 | 핵심 입력 → 결과 | PM · SM | 대표 오류 | RTM |
| --- | --- | --- | --- | --- | --- | --- |
| API-ADM-001 · UIA-ADM-001 | `PUT /schools/{school_id}/teachers/{teacher_id}` | SAD | 계정 상태·학교 소속·운영 메타데이터 → 교사 계정 버전 | PM-OPS-001 · SM — | `SCHOOL_SCOPE_VIOLATION`, `TEACHING_ROLE_AUTO_GRANT_FORBIDDEN` | AUTH-002 |
| API-ADM-002 · UIA-ADM-002 | `POST /schools/{school_id}/device-commands` | SAD | `command=LINK\|UNLINK\|MARK_LOST, device_id, student_session_id?` → 기기·세션 상태 | PM-OPS-001 · SM-APP-001 | `SCHOOL_SCOPE_VIOLATION`, `ACTIVE_SESSION_CONFIRMATION_REQUIRED` | AUTH-003·DAT-005 |
| API-ADM-003 · UIA-ADM-003 | `POST /schools/{school_id}/classrooms/{C}/emergency-ownership-transfers` | SAD | 새 OWN, 기존 COT 확인, 필수 사유 → OWN 1명 원자 보장 | PM-CLS-003 · SM-CLS-001 | `TARGET_NOT_CO_TEACHER`, `REASON_REQUIRED`, `SCHOOL_SCOPE_VIOLATION` | AUTH-002 |
| API-ADM-004 · UIA-ADM-004 | `PUT /schools/{school_id}/ai-settings` | SAD | `enabled=false, reason` → 학교 AI 설정 버전 | PM-AI-005 · SM — | `SCHOOL_SCOPE_VIOLATION`, `POLICY_VERSION_CONFLICT` | AI-001·AI-008 |
| API-OPS-001 · UIA-OPS-001 | `PUT /platform/ai-settings` | POP | 공급자·전역 한도·모델 라우팅·승인 참조 → 전역 설정 버전 | PM-AI-006·PM-SVC-001 · SM — | `OPERATION_APPROVAL_REQUIRED`, `CREDENTIAL_REFERENCE_INVALID` | SYS-002·AI-006·AI-008 |
| API-OPS-002 · UIA-OPS-002 | `POST /platform/service-credential-commands` | POP | `command=ISSUE\|REVOKE, service, audience, scopes, expires_at` → 1회 자격증명 또는 폐기 결과 | PM-SVC-001 · SM — | `SERVICE_SCOPE_VIOLATION`, `EXPIRY_OUT_OF_RANGE`, `REASON_REQUIRED` | SYS-002·AUTH-002 |

## 10. 조회 API

조회 API도 인증·역할·학급 범위를 서버에서 판정하며 클라이언트가 요청한 임의 필드를 확장하지 않습니다.

실시간 채널의 초기화·재동기화에 사용하는 조회 응답은 `meta.event_sequence`를 포함합니다.

| API ID | 메서드·경로 | 허용 역할 | 응답 요약 | 연결 화면·요구사항 |
| --- | --- | --- | --- | --- |
| API-READ-001 | `GET /auth/bootstrap` | 인증 사용자 | 세션, 역할별 학급, 유효 시작 화면 | SCR-STU-003·SCR-TCH-002·SCR-WEB-002 / SYS-001·AUTH-002 |
| API-READ-002 | `GET /classrooms/{C}` | STU·OWN·COT·SAD | 역할별 학급 상태·기간·코스·화폐 단위명 요약, 허용 역할에는 학급 코드 포함 | SCR-STU-003·SCR-WEB-003·SCR-ADM-001 / AUTH-007·CLS-001·CLS-010·CRS-001 |
| API-READ-003 | `GET /classrooms/{C}/configuration` | OWN·COT | 읽기 전용 학급 코드·화폐 단위명, 코스·난이도·기간·자율시간·설정 버전 | SCR-WEB-004·SCR-WEB-008 / AUTH-007·CLS-010·CRS-003·CRS-004·CRS-005 |
| API-READ-004 | `GET /classrooms/{C}/roster-and-salaries` | OWN·COT | 학생 번호·닉네임·역할·급여, COT는 제한된 수정 권한 | SCR-WEB-006 / ECO-004 |
| API-READ-005 | `GET /classrooms/{C}/co-teachers` | OWN·COT | 담당 교사·소유권 이전 상태 | SCR-WEB-007 / AUTH-002 |
| API-READ-006 | `GET /classrooms/{C}/modules-and-policies` | STU·OWN·COT | 활성 모듈, 공개 정책, 교사용 전체 정책 버전 | SCR-STU-003·SCR-WEB-005·SCR-WEB-009 / CLS-002·CRS-006·FIN-011 |
| API-READ-007 | `GET /classrooms/{C}/students/me/wallet` | STU | 본인 잔액·최근 거래·정책 기준 | SCR-STU-004 / ECO-001·ECO-003 |
| API-READ-008 | `GET /classrooms/{C}/students/me/payslips` | STU | 본인 총소득·공제·세금·실수령액 | SCR-STU-005 / ECO-004 |
| API-READ-009 | `GET /classrooms/{C}/ledger-transactions` | OWN·COT | 필터·전후잔액·정책 스냅샷·복구 연결 | SCR-WEB-012 / ECO-001·ECO-002·ECO-003 |
| API-READ-010 | `GET /classrooms/{C}/store/catalog` | STU·OWN·COT | 학생은 게시·구매 가능 상품만, 교사는 draft·게시 상태와 최신 상품 버전을 포함한 상품·가격·세금·재고 | SCR-STU-006·SCR-WEB-011 / ECO-005 |
| API-READ-011 | `GET /classrooms/{C}/store/orders` | STU·OWN·COT | 학생은 본인 주문, 교사는 수령 대기·처리 상태 | SCR-STU-007·SCR-TCH-010 / ECO-005·ECO-006 |
| API-READ-012 | `GET /classrooms/{C}/students/me/inventory` | STU | 본인 캐릭터·아이템·장착·잠금 상태 | SCR-STU-008 / ECO-007·TRD-002 |
| API-READ-013 | `GET /classrooms/{C}/trades` | STU·OWN·COT | 학생은 관련 거래, 교사는 담당 학급 승인·기록 | SCR-STU-009·SCR-TCH-008·SCR-WEB-013 / TRD-001·TRD-004 |
| API-READ-014 | `GET /classrooms/{C}/financial-products` | STU·OWN·COT | 예금·적금·대출 상품과 신규 적용 정책 | SCR-STU-013·SCR-WEB-014·SCR-WEB-016 / FIN-001·FIN-002·FIN-008 |
| API-READ-015 | `GET /classrooms/{C}/students/me/deposit-and-savings` | STU | 본인 예금·적금 계좌·납입·만기·해지 예상 | SCR-STU-014·SCR-STU-015 / FIN-001·FIN-002·FIN-003·FIN-004 |
| API-READ-016 | `GET /classrooms/{C}/investment-market` | STU·OWN·COT | 시장 상태·가격 버전·과거 선 그래프·한도·현재 단계까지 승인된 투자 뉴스. 미래 가격·미공개 뉴스 제외 | SCR-STU-016·SCR-WEB-015 / FIN-005·FIN-007·AI-011 |
| API-READ-017 | `GET /classrooms/{C}/students/me/investment-portfolio` | STU | 본인 보유량·체결·손익·수익률 | SCR-STU-018 / FIN-006·FIN-007 |
| API-READ-018 | `GET /classrooms/{C}/students/me/loans-and-credit` | STU | 본인 대출·상환일정·연체·신용 상태 | SCR-STU-019·SCR-STU-020 / FIN-008·FIN-009·FIN-010 |
| API-READ-019 | `GET /classrooms/{C}/loan-approval-queue` | OWN·COT | 최신 신용·활성 대출·정책과 승인 대기 | SCR-TCH-009·SCR-WEB-016 / FIN-008·FIN-010 |
| API-READ-020 | `GET /classrooms/{C}/quiz-questions` | OWN·COT | 문제은행·버전·검토·게시 상태와 최신 선택지·정답·해설·AI 실행 출처 | SCR-WEB-017·SCR-WEB-018 / QZ-001·QZ-002·QZ-006·QZ-008 |
| API-READ-021 | `GET /classrooms/{C}/quiz-sessions/{R}` | STU·OWN·COT | 역할별 진행 상태·문항 스냅샷·제출 상태 | SCR-TCH-005·SCR-STU-021·SCR-STU-022 / QZ-003·QZ-004 |
| API-READ-022 | `GET /classrooms/{C}/quiz-sessions/{R}/results` | STU·OWN·COT | 학생은 본인, 교사는 학급 결과·무효화 상태 | SCR-STU-023·SCR-WEB-019 / QZ-003·QZ-004·QZ-009 |
| API-READ-023 | `GET /classrooms/{C}/votes/{R}` | STU·OWN·COT | 참여 여부·예산 선택지·익명 집계·현재 공동기금·확정 사용처와 연결 원장 거래. 진행 중 학생 화면에는 개인 선택과 선택지별 집계를 숨김 | SCR-STU-012·SCR-TCH-006 / ECO-008 |
| API-READ-024 | `GET /classrooms/{C}/guided-activities/{R}` | STU·OWN·COT | 역할별 활동·참여·GUIDED·공개 설정 | SCR-TCH-004·SCR-STU-021 / CLS-009 |
| API-READ-025 | `GET /public/display-sessions/{display_session_id}/bootstrap` | DSP | 문제·타이머·익명 집계·허용된 공개 결과만 | SCR-PUB-001·SCR-PUB-002·SCR-PUB-003·SCR-PUB-004 / AUTH-004·CLS-009·QZ-003 |
| API-READ-026 | `GET /classrooms/{C}/teacher-dashboard` | OWN·COT | 승인·수령·정산·활동 알림, OBSERVE·WARN 경제 알림과 학급 요약 | SCR-TCH-002·SCR-TCH-012 / AI-010·DAT-003 |
| API-READ-027 | `GET /classrooms/{C}/ai/runs-and-proposals` | OWN·COT | 최신순 cursor, 분석 시작·종료 구간, run/trigger·규칙 버전·provider/fallback·검증 코드와 제안 target ID/current value·허용 범위·근거·예상 효과·부작용·결정 상태, 학생 개인 원문 제외 | SCR-WEB-021·SCR-WEB-022·SCR-WEB-023 / AI-003·AI-005·AI-008·AI-009·AI-010 |
| API-READ-028 | `GET /classrooms/{C}/reports?from={instant}&to={instant}` | OWN·COT | 기간 내 학급 화폐·지갑·소비·예적금 원금·공동기금·활성 학생·퀴즈 참여 집계. 개인 식별자·닉네임·개인 잔액은 반환하지 않음 | SCR-WEB-024 / DAT-001·DAT-003 |
| API-READ-029 | `GET /classrooms/{C}/audit-events` | OWN·COT·SAD | 역할 범위별 감사 이벤트·요청번호·전후상태 | SCR-WEB-025·SCR-ADM-005 / AI-008·DAT-004 |
| API-READ-030 | `GET /operations/{operation_id}` | 요청 실행자·허용 담당자 | `RUNNING/COMPLETED/FAILED`, 진행률·결과 URL·실패 코드·완료/만료 시각 | 내보내기·AI·정산 화면 / SYS-005·DAT-003 |
| API-READ-031 | `GET /schools/{school_id}/teachers` | SAD | 학교 교사 계정·소속·상태 | SCR-ADM-002 / AUTH-002 |
| API-READ-032 | `GET /schools/{school_id}/devices` | SAD | 학교 지급 기기·분실·연결·세션 메타데이터 | SCR-ADM-003 / AUTH-003·DAT-005 |
| API-READ-033 | `GET /platform/ai-settings` | POP | 공급자·모델 라우팅·전역 한도·설정 버전 | SCR-OPS-002 / SYS-002·AI-006 |
| API-READ-034 | `GET /platform/service-credentials` | POP | 자격증명 메타데이터만, 비밀값 제외 | SCR-OPS-002 / SYS-002 |

## 11. 역할별 응답 DTO 경계

| 주체 | 허용 데이터 | 금지 데이터 |
| --- | --- | --- |
| STU | 본인 잔액·거래·금융·신용·평가, 거래 상대의 닉네임과 제안 항목 | 다른 학생의 잔액·신용·평가·전체 거래 |
| OWN·COT | 담당 학급 운영에 필요한 학생·거래·금융·평가 상세 | 미담당 학급, 비밀번호·토큰 |
| SAD | 학교 교사·기기·학급 메타데이터, 사유가 있는 학교 감사·집계 | 학생 금융 상세의 일상 조회, 교사 수업 권한 자동 상속 |
| POP | 서비스 상태·AI 공급자·자격증명 메타데이터·전역 콘텐츠 검토 | 학생·학급 금융 및 평가 내용 |
| AIS | 가명화된 학급 집계·교사가 입력한 최소 질문 | 학생 개인 분석·계좌 변경·원장 쓰기 |
| DSP | 문제·타이머·익명 집계·교사가 승인한 GAME 공개 결과 | 잔액·신용·대출·TEST 결과·개인거래 상세 |

역할마다 별도 DTO 또는 명시적인 projection을 사용합니다. 내부 전체 DTO를 직렬화한 뒤 필드를 제거하는 방식은 사용하지 않습니다.

## 12. 도메인 공통 스키마

### 12.1 금액과 정책 스냅샷

```json
{
  "amount": 1200,
  "currency_name": "에코",
  "tax_amount": 120,
  "total_amount": 1320,
  "rate_bps": 1000,
  "policy_snapshot_id": "9d824b58-b04f-4383-a7cc-1e38658aa0e2"
}
```

클라이언트가 보낸 계산 결과는 미리보기로만 사용하고 서버가 정책 스냅샷으로 다시 계산합니다.
`currency_name`은 학급의 불변 표시 메타데이터이며 거래별 정책 스냅샷이나 계산값으로 취급하지 않습니다.

### 12.2 상태 보유 리소스

```json
{
  "id": "5e811c19-aa72-4a3f-b1ca-44bd5e41920d",
  "status": "ACTIVE",
  "state_version": 7,
  "status_changed_at": "2026-07-29T06:10:15.120Z",
  "status_reason_code": "TEACHER_APPROVED"
}
```

`status`, `state_version`, `status_changed_at`은 집계의 현재 상태 필드입니다. `status_changed_by`와 `status_reason_code`는 필요한 응답에서 최신 `state_transitions`를 조회해 제공하며 모든 집계 테이블에 별도 컬럼으로 중복 저장하지 않습니다.

### 12.3 비동기 작업

```json
{
  "operation_id": "5fc425a8-7b5f-4984-a483-afd57ba2e706",
  "status": "QUEUED",
  "status_url": "/api/v1/operations/5fc425a8-7b5f-4984-a483-afd57ba2e706"
}
```

## 13. 트랜잭션·이벤트 경계

| 작업 | 하나의 트랜잭션으로 확정할 대상 | 커밋 후 이벤트 |
| --- | --- | --- |
| 상점 구매 | 주문·재고·세금·지갑·원장·디지털 아이템 | 주문·잔액·재고 변경 |
| 개인거래 승인 | 거래·아이템 소유권·잠금·금액·세금·원장 | 거래 완료·양측 잔액·아이템 변경 |
| 금융 가입·상환 | 계좌·일정·지갑·원장·정책 스냅샷 | 계좌·잔액·상환 상태 변경 |
| 투자 체결 | 주문·보유량·지갑·원장·가격 스냅샷 | 체결·포트폴리오 변경 |
| 퀴즈 결과 확정 | 결과 버전·보상 거래 그룹·원장 | 결과 공개·보상 완료 |
| 경제교실 종료 | 정산 조정·최종 스냅샷·학급 상태 | 종료 진행률·READ_ONLY 전환 |

HTTP 성공 응답과 WebSocket 성공 이벤트가 서로 다른 상태를 가리키지 않도록 트랜잭션 커밋 이후 동일한 이벤트 레코드를 발행합니다.

## 14. 커버리지

- 화면 행동: 69개
- HTTP 연결 대상 행동: 69개
- 명령 API ID: 69개
- 세션 지원 API: 4개
- 조회 API: 34개
- 기계 판독 계약: [OpenAPI 3.1](../contracts/openapi.yaml) 109개 작업
- 필드 정합성: [API·스키마 필드 정합성 검토](reviews/API_SCHEMA_FIELD_AUDIT.md) 기준을 확장하여 MVP·지원 78개 strict·후속 31개 planned
- 아직 남은 항목: 서비스·E2E 인수 테스트 ID

## 15. 다음 작업

HTTP API ID의 RTM 역방향 연결은 [RTM v1.4](RTM.md), 실시간 계약은 [WebSocket 명세](WEBSOCKET_SPEC.md), 논리·물리 데이터 연결과 Flyway·DB 무결성 테스트는 [논리 데이터 모델](DATA_MODEL.md)과 [물리 스키마](PHYSICAL_SCHEMA.md)에서 관리합니다. AI v1.2 사람 검토 계약의 기계 동기화 대상은 [OpenAPI 3.1 계약](../contracts/openapi.yaml)과 [API 필드 매핑](../contracts/api-field-mapping.json)입니다.

1. OpenAPI·[필드 매핑](../contracts/api-field-mapping.json) 기반 TypeScript 클라이언트·Spring 인터페이스 생성
2. 역할·상태·오류를 포함한 서비스·E2E 인수 테스트 연결
3. 후속 작업을 MVP로 승격할 때 `planned` 계약을 strict DTO로 전환
