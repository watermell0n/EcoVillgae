# API·스키마 필드 정합성 검토

- 기준일: 2026-07-30
- 대상: `contracts/openapi.yaml`, `contracts/api-field-mapping.json`, HTTP·데이터 모델·물리 스키마, Flyway `V1_1`~`V1_11`
- 관련 이슈: `S15P11B201-160`
- 판정: **PASS — MVP와 필수 지원 API의 DTO·저장 경계를 확정하고 후속 API를 명시적으로 분리함**

## 1. 완료 기준과 범위 결정

검토 대상 106개 HTTP 작업을 모두 없애거나 임의로 구현 범위에 넣지 않고 다음처럼 분류했습니다.

| 분류 | 수 | 계약 수준 |
| --- | ---: | --- |
| `mvp-contract` | 65 | RTM의 `MVP` 요구사항과 로그인·세션·현재 사용자·비동기 상태 조회 등 필수 지원 작업. 요청·응답 최상위 DTO와 저장·조회 경계를 확정 |
| `planned` | 41 | 소유권 이전, 학교 지급 기기, 플랫폼 설정·자격증명 등 후속 작업. 경로·권한·추적성은 유지하되 구현 스프린트에서 DTO 확정 |

이 분류는 OpenAPI 각 operation의 `x-implementation-status`, `x-schema-closure`,
`x-storage-targets`에 기록됩니다. 기계 판독 기준은
[`api-field-mapping.json`](../../contracts/api-field-mapping.json)이며 각 필드의 이름, 필수 여부,
자료형, 저장·projection 대상, 변환, NULL 의미와 공개 범위를 포함합니다.

## 2. 주요 필드 매핑

| API 필드 | 저장·조회 위치 | 변환·검증 |
| --- | --- | --- |
| `classroom_id`, `*_id` | 각 테이블 `uuid` PK·FK | OpenAPI `string(uuid)` ↔ PostgreSQL `uuid` |
| `classroom_code` | `classrooms.classroom_code` | 서버 생성 전역 유일 8자, 입력은 대문자 정규화, 생성 후 불변 |
| `currency_name` | `classrooms.currency_name` | NFKC·공백 제거 후 1~10자와 금칙어 검사, 생성 후 불변 |
| `student_count` | `classrooms.planned_student_count` | API 명칭을 예상 학생 수 컬럼에 매핑, 1 이상 |
| `attendance_number` | `classroom_students.roster_number` | 이름 변환, 1 이상 |
| `course_size` | `classrooms.scale_label` | 규모 enum 이름 변환 |
| `starts_on`, `ends_on` | `actual_starts_on`, `actual_ends_on` | 생성 API에서는 서비스 필수, 기존 데이터 호환 때문에 DB는 nullable |
| `timezone` | `classrooms.time_zone` | 이름 변환, 기본 `Asia/Seoul` |
| `role_code` | `salary_roles.role_code` | 학급 내 유일·안정 코드, 표시명과 분리 |
| 일정의 `module_codes` | `autonomous_schedule_modules.module_code` | 일정·모듈 양쪽을 학급 복합 FK로 검증 |
| 금액·가격·원금 | `bigint` 금액 컬럼 | OpenAPI `integer(int64)`, 부동소수점 금지 |
| 세율·금리·비율 | `integer` basis point 컬럼 | 허용 범위 검사 |
| `expected_*_version` | 현재 버전과 서비스에서 비교 | 성공 시 별도 저장하지 않음 |
| `meta.*` | 요청 컨텍스트 | 응답 시 생성, 업무 DB 비저장 |

## 3. 새로 보강한 저장 모델

Flyway [`V1_11__mvp_schema_alignment.sql`](../../backend/src/main/resources/db/migration/V1_11__mvp_schema_alignment.sql)에 다음을 추가했습니다. 이미 공유된 `V1_1`~`V1_10`은 수정하지 않았습니다.

1. `classrooms.planned_student_count`: 개설 시 예상 학생 수와 실제 명단 수를 분리합니다.
2. `salary_roles.role_code`: 학생 배정 DTO가 이름 대신 참조하는 학급 내 안정 코드입니다.
3. `autonomous_schedule_modules`: 자율시간과 활성 모듈의 다대다 연결을 정규화합니다.
4. `school_ai_setting_versions`: 학교 AI 활성 여부·사유를 불변 버전 이력으로 보관합니다.

DBT-019는 양의 예상 학생 수, 역할 코드 유일성, 일정-모듈 학급 격리, 학교별 현재 AI 설정 1개를 검증합니다.

## 4. 인증·공개정보 경계

[임시 인증정보 저장 계약](../EPHEMERAL_AUTH_STORAGE.md)에 Redis 키, TTL, SHA-256 키 해시,
원자 소비와 감사 범위를 고정했습니다.

- 비밀번호 재설정 요청: 10분, 검증 실패 5회 또는 성공 시 폐기
- WebSocket 연결 티켓: 60초, 연결 성립 시 1회 소비
- 공용 화면 세션: 활동 종료+5분과 발급 후 8시간 중 빠른 시각, 종료·해제 시 즉시 폐기
- `new_password`는 Argon2id 해시만 `student_credentials.password_hash`에 저장
- 원문 비밀번호·토큰·검증값은 DB·로그·감사 이벤트에 남기지 않음

공개 화면은 승인된 네 projection만 사용합니다. GAME은 교사가 공개한 1~3위의 닉네임·장착
캐릭터만 노출하며 점수는 노출하지 않습니다. TEST 결과·순위·보상은 공개 DTO와 공개 View에
포함하지 않습니다.

## 5. 후속 범위로 남긴 항목

다음 41개 `planned` 작업은 누락이 아니라 의도적으로 구현 범위 밖에 둔 계약입니다.

- 소유권 이전 요청의 만료·취소 모델
- 학생과 아직 연결되지 않은 학교 지급 기기 모델
- 플랫폼 전역 AI 설정
- 서비스 자격증명 메타데이터와 외부 Secrets Manager 연동
- 중학생·장기 코스 등 후속 운영 기능의 세부 DTO

이 작업을 MVP로 승격할 때는 먼저 `HTTP_API_SPEC.md`와 RTM 출처를 갱신하고, 생성된
매핑에서 `planned`가 `mvp-contract`로 바뀌며 명시적 DTO가 없으면 검증이 실패하게 합니다.

## 6. 자동 검증 결과

`scripts/validate-api-schema-consistency.mjs`는 다음을 검사합니다.

- 작업 106개가 중복 없이 매핑됨
- MVP·지원 작업 65개는 요청이 명시적이며 요청·응답이 strict 또는 본문 없음
- 후속 작업 41개는 `planned`로 명시됨
- 모든 MVP 작업에 PostgreSQL·View·Redis·외부 저장 경계가 존재함
- V1_11의 4개 저장 보강과 임시 인증정보 키·TTL 규칙이 존재함

현재 결과는 `106 operations, 65 strict MVP/support contracts, 41 planned contracts`로 PASS입니다.
