# AI 프론트엔드 계약 통합 점검

- 기준일: 2026-08-01
- 작업 이슈: `S15P11B201-259`
- 기준 브랜치: `feat/ai-features` (`develop` 최신 병합 상태)

## 이번 단계에서 연결한 범위

1. 교사 웹의 공통 API 호출을 `NEXT_PUBLIC_API_BASE_URL`, JSON envelope, Bearer/cookie 인증,
   `Idempotency-Key` 규칙에 맞췄다.
2. 방 생성은 `POST /classrooms`의 실제 필드와 모듈 코드를 사용한다. 선택 모듈은 0개 이상이며,
   응답의 `classroom_id`, `classroom_code`, `state_version`을 이후 호출에 사용한다.
3. 백엔드 실패 시 로컬 성공 상태를 만들던 mock fallback을 제거했다. 오류 코드와 메시지를 교사
   화면에 남기며, 성공 응답을 받은 경우에만 다음 상태로 이동한다.
4. 방 생성 직후 `CLASSROOM_SETUP`, 정책 모듈 활성화 전 `MODULE_ACTIVATION` 시작 전 점검을
   요청한다. 교사는 제안값, 추천 구간, 허용 구간, `PASS/WARN/BLOCK`, fallback 여부를 확인한다.
5. 추천 구간 밖의 값은 경고하고 허용 구간 밖의 값과 `BLOCK` 결과는 적용을 막는다. 승인한
   제안은 별도 이중 활성화 요청 없이 AI 제안 `APPLY` 명령으로 초기 정책 저장과 모듈 활성화를
   함께 수행한다. 정책이 없는 모듈만 활성화 명령을 직접 사용한다.
6. 역할·급여 모듈이 활성 상태가 아니면 학생 명단에서 역할·급여 입력, 급여 요약과 지급 UI를
   숨기고 `role_code`, `salary_roles`, `expected_policy_version`을 보내지 않는다.

## 현재 구현이 없어 제외한 범위

| 제외 항목 | 필요한 구현 | 현재 영향 |
|---|---|---|
| 교사 OIDC 로그인 | 로그인 화면에서 `id_token` 교환 후 세션 저장 | 현재 로그인 UI는 화면 전환만 한다. 실제 보호 API는 별도 인증 세션 없이는 `AUTH_REQUIRED`가 정상이다. |
| 모듈·정책 상태 조회 | `GET /classrooms/{C}/modules-and-policies` (`API-READ-006`) | 새로고침 후 `ACTIVE/DISABLED`, 모듈·정책 버전을 복원할 수 없어 활성화 상태는 현재 세션에만 유지한다. |
| AI 실행·제안 조회 | `GET /classrooms/{C}/ai/runs-and-proposals` (`API-READ-027`) | 현재 Spring은 `202` 응답에 완료 결과를 함께 주므로 표시 가능하지만, 향후 비동기화 시 polling·재진입을 붙일 수 없다. |
| 학급/코스 설정 변경 저장 | 코스 구성 및 학급 설정 수정 command API | 운영 계획 추천은 로컬 미리보기만 제공하며 저장 성공으로 표시하지 않는다. |
| 모듈 사후 추가·중지·정책 수정 | `API-MOD-002`, `API-POL-001` 및 선택 모듈 추가 API | 방 생성 때 등록하지 않은 모듈을 나중에 임의 생성하거나 활성 모듈을 중지하지 않는다. |
| 상품 가격 추천 적용 | 상점 상품 command controller와 상품 ID·버전 조회 | `PRODUCT_DRAFT` 추천을 실제 상품에 안전하게 저장할 대상과 버전이 없다. |
| GAME 보상 추천 적용 | AI 제안의 퀴즈 세션 target/version 결속과 프론트 세션 draft | 최신 Spring에는 퀴즈 세션 command·조회가 구현됐지만 `QUIZ_SESSION_DRAFT` 제안을 특정 세션에 승인 적용하는 라우팅이 없다. |
| 학생 Expo 앱 모듈 메뉴 필터 | `API-READ-006` 또는 학생용 활성 모듈 조회 | Expo 앱에서 선택한 모듈만 노출하는 완료 기준은 아직 검증할 수 없다. 현재 Vite UI는 이전 참고용이다. |
| 명단 재조회·급여 지급 | `GET /classrooms/{C}/roster-and-salaries` (`API-READ-004`) | 저장 후 학생 UUID를 알 수 없어 잘못된 지급을 막기 위해 급여 지급 연결은 제외했다. |
| OpenAPI 생성 타입 | Expo·교사 웹에 생성 타입 또는 단일 응답 mapper 적용 | Spring Controller/DTO와 OpenAPI 차이 검출 및 CI 기준선은 `S15P11B201-279`에서 추가했다. 실제 클라이언트 타입 전환은 Expo·교사 프론트 작업에서 진행한다. |

## 통합 시 주의할 예외

- 소득세는 역할·급여가, 대출은 예금이 먼저 `ACTIVE`여야 한다. 화면과 백엔드가 모두 검사한다.
- `STATE_VERSION_CONFLICT`, `POLICY_VERSION_CONFLICT`가 발생하면 자동 재시도하면 안 된다. 최신
  상태를 다시 읽고 교사에게 재검토를 요청해야 하지만, 현재는 위 조회 API 구현 전이라 오류만
  표시한다.
- `fallback_used=true`는 실패가 아니라 Spring 규칙 엔진의 결정값을 사용했다는 뜻이다. 숫자와
  차단 판정은 유효하고, AI 설명만 fallback 문장이다.
- `WARN`은 추천 구간 밖의 교사 확정을 허용하지만 `BLOCK`과 허용 구간 밖 값은 적용할 수 없다.
- 같은 사용자 동작 재전송에는 같은 멱등키를 써야 한다. 현재 클라이언트는 새 클릭마다 새 키를
  만들므로 네트워크 타임아웃 자동 재시도는 아직 넣지 않았다.
- 역할·급여 활성화 시 현재 화면은 첫 설정용 기본 역할 하나를 추천 입력으로 사용한다. 여러 역할
  편집 UI와 명단의 실제 학생 UUID 조회가 준비되면 역할별 인원·가중치를 다시 연결해야 한다.
- 방 생성 서비스는 `ONE_SESSION`에서 퀴즈·게임 외 모듈을 차단하지만 `RULE_ENGINE_SPEC`은
  `ONE_SHOT` 라벨만으로 역할·급여를 금지하지 않는다고 명시한다. 현재 화면은 실제 백엔드
  동작을 따라 퀴즈만 허용한다. 두 정책 중 하나로 팀 합의 후 명세나 백엔드를 맞춰야 한다.
- 코스 추천 seed에는 `WALLET`, `BUDGET`, `INCOME`, `PUBLIC_BUDGET` 등의 세분 코드가 있지만
  현재 모듈 활성화 정책은 `ROLE_SALARY`, `INCOME_TAX`, `DEPOSIT`, `LOAN`을 중심으로 한다.
  프론트 전체에 쓰일 canonical module catalog가 아직 하나로 고정되지 않았다.
- 상점·승인·수업 제어의 일부 화면은 기존 프론트 placeholder를 유지한다. 이번 작업에서 실제
  성공처럼 처리하는 API fallback은 제거했지만, 해당 기능의 완전한 계약 통합 범위는 아니다.

## 후속 권장 순서

1. 교사 OIDC 로그인과 `API-READ-006`을 먼저 구현해 새로고침 가능한 통합 기준선을 만든다.
2. `API-READ-004`로 학생 UUID와 정책 버전을 복원한 뒤 명단 수정·급여 지급을 연결한다.
3. 상점 상품 command를 완성하고 상품·퀴즈 proposal target/version 및 APPLY 라우팅을 구현한 뒤 두 draft 추천을 붙인다. 퀴즈 세션 command·조회 자체는 최신 Spring에 구현되어 있다.
4. `API-READ-027`과 WebSocket 알림을 연결해 `202` 비동기 실행과 제안 이력을 처리한다.
5. `S15P11B201-279`의 OpenAPI-Spring 계약 diff 기준선을 유지하고, `S15P11B201-277`에서 Expo 생성 타입 또는 단일 mapper를 적용해 수동 DTO를 제거한다.

## 검증 결과

- 교사 웹: `pnpm run typecheck`, `pnpm run build` 통과
- 학생 앱: `pnpm run build` 통과
- Spring 집중 계약 테스트: 24건 통과, 실패·오류·skip 0건
  - `ClassroomCreationApiTest` 6건
  - `ModuleActivationApiTest` 7건
  - `ClassroomRosterApiTest` 7건
  - `AiSetupExecutionApiTest` 4건
- 브라우저 점검: 로그인 → 방 생성 → 기간 → 모듈 선택 → 0개 모듈 개설 확인 화면 렌더링,
  OIDC/school ID 미연결 시 최종 생성 차단, 브라우저 콘솔 오류 0건
