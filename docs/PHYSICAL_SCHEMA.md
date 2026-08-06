# 이코빌리지 PostgreSQL 물리 스키마

- 버전: v0.6
- 기준일: 2026-07-30
- 개발 기준 브랜치: `develop`
- 입력: [논리 데이터 모델 v0.5](DATA_MODEL.md)
- 상태: MVP 물리 기준선·AI v1.2 선택형 모듈 계약 반영, 관련 Flyway 구현 예정
- 대상: PostgreSQL 17, Flyway migration

## 1. 목적과 범위

이 문서는 논리 ERD의 87개 엔티티를 PostgreSQL 17 테이블로 변환하는 기준입니다. 테이블명, 컬럼, 자료형, NULL, 기본값, 기본키·외래키·유일성, 조회 인덱스와 DB 무결성 규칙을 확정합니다.

실행 가능한 Flyway `V1_1`~`V1_17`와 Testcontainers 무결성 테스트는
[`backend`](../backend/README.md)에 있습니다. DDL은 이 문서의 테이블·제약·인덱스
식별자를 구현하며, 공개 화면용 최소 projection 4개를 함께 제공합니다.

MVP 밖의 중학생 코스, 자리·부동산, 보험, 주거·청약 테이블은 만들지 않습니다.

## 2. 명명·자료형·시간 규칙

### 2.1 명명

- 스키마: 기본 `public`, 운영 분리가 필요하면 `ecovillage`
- 테이블·컬럼·인덱스·제약조건: `snake_case`
- 기본키: `id`
- 외래키: `{참조 단수명}_id`
- 유일 제약: `uq_{table}_{핵심컬럼}`
- 외래키: `fk_{child}_{parent}`
- 검사 제약: `ck_{table}_{rule}`
- 일반 인덱스: `ix_{table}_{핵심컬럼}`
- 부분 유일 인덱스: `ux_{table}_{핵심컬럼}_{조건}`
- 트리거: `trg_{table}_{purpose}`

### 2.2 자료형

| 의미 | PostgreSQL 자료형 | 규칙 |
| --- | --- | --- |
| 식별자 | `uuid` | 애플리케이션에서 UUID v7 생성 |
| 금액 | `bigint` | 최소 화폐 단위 정수, 부동소수점 금지 |
| 세율·금리·비율 | `integer` | basis point, `100=1%`, `10000=100%` |
| 수량·횟수 | `integer` | 음수 금지 |
| 원장·이벤트 순번 | `bigint` | 1부터 단조 증가 |
| 상태·종류·코드 | `varchar(40)` | 대문자 `SNAKE_CASE`, `CHECK` 적용 |
| 이름·표시 문자열 | `varchar(120)` | 닉네임은 `varchar(40)` |
| 긴 설명 | `text` | 길이 제한이 필요한 입력은 애플리케이션도 검증 |
| 서버 시각 | `timestamptz` | UTC 저장, API에서 ISO 8601 출력 |
| 운영 날짜 | `date` | 학급 실제 시작일·종료일 |
| 현지 시각 | `time` | 자율 이용 반복 일정에만 사용 |
| 정책·스냅샷 | `jsonb` | `schema_version` 또는 해시와 함께 저장 |
| 해시 | `varchar(128)` | SHA-256은 64자 hex, 공급자 확장 고려 |
| 비밀번호 | `text` | Argon2id 등 단방향 해시만 저장 |
| 외부 파일 키 | `varchar(512)` | S3 object key, 공개 URL 저장 금지 |

### 2.3 공통 컬럼 집합

아래 표의 `G`, `S`, `I`는 4장의 테이블 사전에서 반복 컬럼을 생략하기 위한 표기입니다. `추가 컬럼`과 합친 결과가 해당 테이블의 전체 컬럼입니다.

#### `G`: 일반 변경 엔티티

| 컬럼 | 자료형 | NULL | 기본값 |
| --- | --- | --- | --- |
| `id` | `uuid` | N | 없음, PK |
| `created_at` | `timestamptz` | N | `now()` |
| `updated_at` | `timestamptz` | N | `now()` |

#### `S`: 학급 범위 상태 엔티티

| 컬럼 | 자료형 | NULL | 기본값 |
| --- | --- | --- | --- |
| `id` | `uuid` | N | 없음, PK |
| `classroom_id` | `uuid` | N | 없음 |
| `status` | `varchar(40)` | N | 없음 |
| `state_version` | `bigint` | N | `0` |
| `status_changed_at` | `timestamptz` | N | `now()` |
| `created_at` | `timestamptz` | N | `now()` |
| `updated_at` | `timestamptz` | N | `now()` |

#### `I`: 학급 범위 불변 이력

| 컬럼 | 자료형 | NULL | 기본값 |
| --- | --- | --- | --- |
| `id` | `uuid` | N | 없음, PK |
| `classroom_id` | `uuid` | N | 없음 |
| `created_at` | `timestamptz` | N | `now()` |

### 2.4 NULL·삭제·갱신 원칙

- `N`은 `NOT NULL`, `Y`는 NULL 허용입니다.
- PK는 항상 `NOT NULL`입니다.
- `created_at`은 수정하지 않습니다.
- 상태 엔티티의 갱신 시 `updated_at`과 `state_version`을 함께 증가시킵니다.
- 불변 이력 `I`에는 `updated_at`을 두지 않습니다.
- 운영 데이터는 기본적으로 물리 삭제하지 않고 상태와 보관기한을 사용합니다.
- `ON DELETE CASCADE`는 게시 전 구성요소와 아직 커밋되지 않은 종속 초안에만 허용합니다.
- 거래·원장·감사·게시 버전은 `ON DELETE RESTRICT`를 사용합니다.

## 3. 학급 격리와 외래키 패턴

### 3.1 학급 범위 복합키

모든 `S`·`I` 테이블과 별도로 `classroom_id`를 갖는 테이블은 다음 키를 추가합니다.

```sql
UNIQUE (classroom_id, id)
```

학급 범위 부모를 참조하는 자식은 단일 FK 대신 다음 복합 FK를 사용합니다.

```sql
FOREIGN KEY (classroom_id, parent_id)
REFERENCES parent_table (classroom_id, id)
ON DELETE RESTRICT
```

이 규칙으로 다른 학급의 학생·거래·상품·금융계좌·활동을 참조하는 오류를 DB에서 차단합니다.

### 3.2 전역 참조

다음 테이블은 학급 밖의 전역 기준정보이므로 단일 UUID FK를 허용합니다.

- `schools`
- `users`
- `course_templates`
- `course_template_versions`
- `character_catalog`

### 3.3 다형 참조

`audit_events.target_id`, `state_transitions.aggregate_id`, `transaction_business_refs.ref_id`처럼 여러 테이블을 가리키는 감사·추적용 참조에는 물리 FK를 만들지 않습니다. 대신 `{type, id}`를 함께 저장하고 서비스·감사 검증 작업에서 존재 여부를 확인합니다.

금액·소유권의 기준 참조에는 다형 FK를 사용하지 않습니다. 예를 들어 `ledger_accounts`는 학생 계정과 시스템 계정을 별도 컬럼과 XOR `CHECK`로 구분합니다.

## 4. 테이블·컬럼 데이터 사전

표기:

- `G`, `S`, `I`: 2.3의 공통 컬럼 전체 포함
- `FK(table)`: 참조 테이블
- `UQ`: 유일성
- `D=value`: 기본값
- 컬럼 뒤 `?`: NULL 허용
- 별도 표시가 없으면 추가 컬럼은 `NOT NULL`

<!-- TABLES:START -->

### 4.1 학교·사용자·학급·인증

| 테이블 | 공통 | 추가 컬럼 | 키·상태 |
| --- | --- | --- | --- |
| `schools` | G | `school_code varchar(40)`, `name varchar(120)`, `status varchar(40) D=ACTIVE` | `school_code UQ`, 상태 `ACTIVE·INACTIVE` |
| `users` | G | `external_subject varchar(128)`, `display_name varchar(120)`, `user_type varchar(40)`, `status varchar(40) D=ACTIVE` | `external_subject UQ`, 학생 계정은 저장하지 않음 |
| `school_memberships` | G | `school_id uuid FK(schools)`, `user_id uuid FK(users)`, `role varchar(40)`, `status varchar(40) D=ACTIVE`, `assigned_at timestamptz D=now()`, `ended_at timestamptz?` | 활성 `(school_id,user_id,role)` UQ |
| `course_templates` | G | `template_code varchar(40)`, `name varchar(120)`, `grade_min smallint`, `grade_max smallint`, `scale_label varchar(20)`, `status varchar(40) D=DRAFT` | `template_code UQ`, 학년 4~6 |
| `course_template_versions` | G | `course_template_id uuid FK(course_templates)`, `version integer`, `difficulty_profile jsonb D={}`, `status varchar(40)`, `published_at timestamptz?`, `retired_at timestamptz?` | `(course_template_id,version) UQ`, 게시 후 불변 |
| `course_template_modules` | G | `course_template_version_id uuid FK(course_template_versions)`, `module_code varchar(40)`, `recommendation_level varchar(40)`, `sort_order integer D=0`, `config_json jsonb D={}` | `(course_template_version_id,module_code) UQ` |
| `classrooms` | S | `school_id uuid FK(schools)`, `course_template_version_id uuid? FK(course_template_versions)`, `classroom_code varchar(8)`, `display_name varchar(120)`, `class_number integer?`, `description varchar(500)?`, `currency_name varchar(10)`, `grade smallint`, `scale_label varchar(20)`, `planned_student_count integer`, `actual_starts_on date?`, `actual_ends_on date?`, `app_mode_override varchar(40)?`, `time_zone varchar(60) D=Asia/Seoul` | `classroom_code UQ`, 예상 학생 수>0, 반 번호 1~99, 코드·단위명 생성 후 불변, SM-CLS-001 |
| `classroom_teachers` | I | `user_id uuid FK(users)`, `role varchar(40)`, `assigned_at timestamptz D=now()`, `ended_at timestamptz?` | 활성 OWNER 1명, 활성 `(classroom_id,user_id,role)` UQ |
| `classroom_students` | S | `salary_role_id uuid? FK(salary_roles)`, `roster_number integer`, `nickname varchar(40)`, `joined_at timestamptz D=now()`, `retired_at timestamptz?` | `(classroom_id,roster_number) UQ`, `(classroom_id,lower(nickname)) UQ` |
| `student_credentials` | 없음 | `classroom_student_id uuid PK FK(classroom_students)`, `classroom_id uuid`, `password_hash text`, `credential_version bigint D=1`, `changed_at timestamptz D=now()` | 학급 복합 FK, 로그인 실패 횟수·잠금 컬럼 없음 |
| `teacher_credentials` | 없음 | `teacher_user_id uuid PK FK(users)`, `email varchar(254)`, `password_hash text`, `credential_version bigint D=1`, `changed_at timestamptz D=now()` | `email UQ`, 비밀번호 원문은 저장하지 않음 |
| `student_sessions` | G | `classroom_id uuid`, `classroom_student_id uuid FK(classroom_students)`, `device_link_id uuid? FK(device_links)`, `token_hash varchar(128)`, `status varchar(40)`, `expires_at timestamptz`, `revoked_at timestamptz?` | `token_hash UQ`, 학생별 ACTIVE 1개 |
| `device_links` | S | `classroom_student_id uuid FK(classroom_students)`, `device_fingerprint_hash varchar(128)`, `device_name varchar(120)?`, `linked_at timestamptz D=now()`, `last_seen_at timestamptz?`, `revoked_at timestamptz?` | `(classroom_student_id,device_fingerprint_hash) UQ` |
| `classroom_modules` | S | `module_code varchar(40)`, `config_json jsonb D={}`, `enabled_at timestamptz?`, `stopped_at timestamptz?` | `(classroom_id,module_code) UQ`, SM-MOD-001 |
| `classroom_policy_versions` | I | `policy_area varchar(40)`, `version integer`, `values jsonb`, `schema_version varchar(20)`, `source varchar(40)`, `status varchar(40)`, `effective_from timestamptz`, `superseded_at timestamptz?` | `(classroom_id,policy_area,version) UQ`, 적용 후 불변 |
| `autonomous_schedules` | S | `day_of_week smallint`, `local_start_time time`, `local_end_time time`, `time_zone varchar(60) D=Asia/Seoul`, `enabled boolean D=true` | `(classroom_id,day_of_week,local_start_time) UQ`, 종료>시작 |
| `autonomous_schedule_modules` | 없음 | `id uuid PK`, `classroom_id uuid`, `autonomous_schedule_id uuid FK(autonomous_schedules)`, `module_code varchar(40) FK(classroom_modules)`, `created_at timestamptz D=now()` | 학급 복합 FK, `(autonomous_schedule_id,module_code) UQ` |
| `salary_roles` | S | `role_code varchar(40)`, `name varchar(80)`, `gross_amount bigint`, `deductions_json jsonb D={}`, `sort_order integer D=0` | `(classroom_id,role_code) UQ`, `(classroom_id,lower(name)) UQ`, 코드 형식, 금액 0 이상 |
| `payslips` | I | `classroom_student_id uuid FK(classroom_students)`, `salary_role_id uuid? FK(salary_roles)`, `period_key varchar(40)`, `gross_amount bigint`, `deduction_amount bigint`, `tax_amount bigint`, `net_amount bigint`, `policy_snapshot_id uuid FK(policy_snapshots)`, `transaction_group_id uuid FK(transaction_groups)`, `issued_at timestamptz D=now()` | `(classroom_student_id,period_key) UQ`, 금액 산식 |
| `school_ai_setting_versions` | 없음 | `id uuid PK`, `school_id uuid FK(schools)`, `version integer`, `enabled boolean`, `reason text`, `effective_from timestamptz`, `superseded_at timestamptz?`, `created_by_user_id uuid FK(users)`, `created_at timestamptz D=now()` | `(school_id,version) UQ`, 학교별 현재 버전 1개, 이력 불변 |

전역 필수 모듈은 없습니다. 역할·급여 모듈이 비활성화된 학급은 `salary_roles`와 `payslips` 행을 만들지 않으며 `classroom_students.salary_role_id`를 `NULL`로 유지할 수 있습니다. 금액 모듈이 처음 활성화될 때 필요한 `ledger_accounts`를 생성하므로 지갑·원장은 교사가 고르는 기능 모듈이나 방 생성의 필수 데이터가 아닙니다. 초기 정책이 필요한 모듈은 `classroom_policy_versions` 저장과 `classroom_modules` 활성 전이를 같은 서비스 트랜잭션에서 처리합니다.

### 4.2 거래 원장·멱등성·상태·이벤트·감사

| 테이블 | 공통 | 추가 컬럼 | 키·상태 |
| --- | --- | --- | --- |
| `ledger_accounts` | S | `owner_type varchar(40)`, `classroom_student_id uuid? FK(classroom_students)`, `system_owner_key varchar(80)?`, `account_type varchar(40)`, `current_balance bigint D=0`, `next_entry_sequence bigint D=1` | 학생/시스템 소유 XOR, 학생 지갑·공동기금 부분 UQ |
| `transaction_groups` | S | `transaction_type varchar(40)`, `actor_type varchar(40)`, `actor_id uuid?`, `policy_snapshot_id uuid? FK(policy_snapshots)`, `recovery_of_group_id uuid? FK(transaction_groups)`, `root_transaction_group_id uuid? FK(transaction_groups)`, `request_id varchar(128)`, `correlation_id varchar(128)`, `reason text?`, `committed_at timestamptz?`, `rejected_at timestamptz?` | SM-TXN-001, 복구 관계 순환 금지 |
| `ledger_entries` | I | `transaction_group_id uuid FK(transaction_groups)`, `ledger_account_id uuid FK(ledger_accounts)`, `account_sequence bigint`, `amount bigint`, `balance_before bigint`, `balance_after bigint` | `(ledger_account_id,account_sequence) UQ`, 전후 잔액 산식 |
| `policy_snapshots` | I | `snapshot_hash varchar(128)`, `schema_version varchar(20)`, `calculated_values jsonb`, `rounding_rule varchar(40)` | `(classroom_id,snapshot_hash) UQ`, 생성 후 불변 |
| `policy_snapshot_sources` | I | `policy_snapshot_id uuid FK(policy_snapshots)`, `source_type varchar(40)`, `source_id uuid`, `source_version integer`, `source_hash varchar(128)` | `(policy_snapshot_id,source_type,source_id) UQ` |
| `transaction_business_refs` | I | `transaction_group_id uuid FK(transaction_groups)`, `ref_type varchar(40)`, `ref_id uuid` | `(transaction_group_id,ref_type,ref_id) UQ` |
| `transaction_inquiries` | S | `transaction_group_id uuid FK(transaction_groups)`, `classroom_student_id uuid FK(classroom_students)`, `question text`, `resolution text?`, `resolved_by_user_id uuid? FK(users)`, `resolved_at timestamptz?` | 상태 `OPEN·ANSWERED·CLOSED` |
| `idempotency_records` | I | `actor_id uuid`, `route_key varchar(160)`, `idempotency_key varchar(128)`, `request_hash varchar(128)`, `response_status integer`, `response_body jsonb`, `transaction_group_id uuid? FK(transaction_groups)`, `expires_at timestamptz` | `(classroom_id,actor_id,route_key,idempotency_key) UQ` |
| `state_transitions` | I | `aggregate_type varchar(40)`, `aggregate_id uuid`, `previous_status varchar(40)?`, `next_status varchar(40)`, `command varchar(80)`, `actor_type varchar(40)`, `actor_id uuid?`, `state_version bigint`, `reason_code varchar(80)?`, `reason text?`, `transaction_group_id uuid? FK(transaction_groups)`, `occurred_at timestamptz D=now()` | `(classroom_id,aggregate_type,aggregate_id,state_version) UQ` |
| `realtime_streams` | S | `stream_type varchar(40)`, `resource_id uuid?`, `next_sequence bigint D=1`, `retention_from_sequence bigint D=1` | `(classroom_id,stream_type,resource_id) UQ |
| `outbox_events` | 없음 | `event_id uuid PK`, `classroom_id uuid`, `realtime_stream_id uuid FK(realtime_streams)`, `channel_sequence bigint`, `event_type varchar(120)`, `aggregate_type varchar(40)`, `aggregate_id uuid`, `state_version bigint`, `payload jsonb`, `occurred_at timestamptz D=now()`, `published_at timestamptz?`, `publish_attempts integer D=0`, `last_error text?` | `(realtime_stream_id,channel_sequence) UQ`, payload 불변 |
| `audit_events` | 없음 | `id uuid PK`, `classroom_id uuid? FK(classrooms)`, `transaction_group_id uuid? FK(transaction_groups)`, `actor_type varchar(40)`, `actor_id uuid?`, `action varchar(120)`, `target_type varchar(40)`, `target_id uuid?`, `request_id varchar(128)?`, `correlation_id varchar(128)?`, `before_summary jsonb?`, `after_summary jsonb?`, `occurred_at timestamptz D=now()`, `retention_until timestamptz` | append-only, 비밀값·정답 원문 금지 |

### 4.3 상점·캐릭터·재고·개인거래

| 테이블 | 공통 | 추가 컬럼 | 키·상태 |
| --- | --- | --- | --- |
| `store_products` | S | `name varchar(120)`, `description text?`, `product_kind varchar(40)`, `created_by_user_id uuid FK(users)` | `(classroom_id,lower(name),product_kind) UQ` |
| `store_product_versions` | I | `store_product_id uuid FK(store_products)`, `version integer`, `product_kind varchar(40)`, `unit_price bigint`, `vat_rate_bps integer`, `purchasable boolean D=true`, `metadata jsonb D={}`, `published_at timestamptz`, `retired_at timestamptz?` | `(store_product_id,version) UQ`, 게시 후 불변 |
| `store_stocks` | 없음 | `store_product_id uuid PK FK(store_products)`, `classroom_id uuid`, `available_quantity integer D=0`, `reserved_quantity integer D=0`, `state_version bigint D=0`, `updated_at timestamptz D=now()` | 수량 0 이상 |
| `store_orders` | S | `classroom_student_id uuid FK(classroom_students)`, `subtotal_amount bigint`, `tax_amount bigint`, `total_amount bigint`, `policy_snapshot_id uuid FK(policy_snapshots)`, `transaction_group_id uuid? FK(transaction_groups)`, `received_at timestamptz?`, `operation_canceled_at timestamptz?` | SM-SHP-001, 금액 산식 |
| `store_order_items` | I | `store_order_id uuid FK(store_orders)`, `store_product_version_id uuid FK(store_product_versions)`, `quantity integer`, `unit_price bigint`, `vat_rate_bps integer`, `subtotal_amount bigint`, `tax_amount bigint`, `total_amount bigint` | `(store_order_id,store_product_version_id) UQ`, 수량>0 |
| `character_catalog` | G | `character_code varchar(80)`, `name varchar(120)`, `source_title varchar(200)`, `source_url text`, `rights_status varchar(40)`, `rights_evidence_key varchar(512)`, `reviewed_at timestamptz?`, `status varchar(40)` | `character_code UQ`, 승인 전 판매 금지 |
| `classroom_characters` | S | `character_catalog_id uuid FK(character_catalog)`, `store_product_id uuid? FK(store_products)`, `display_name varchar(120)`, `sort_order integer D=0` | `(classroom_id,character_catalog_id) UQ` |
| `inventory_items` | S | `classroom_student_id uuid FK(classroom_students)`, `character_catalog_id uuid FK(character_catalog)`, `source_order_item_id uuid? FK(store_order_items)`, `locked_trade_id uuid? FK(trades)`, `acquired_at timestamptz D=now()` | 디지털 소유 원장, 상태 `AVAILABLE·LOCKED_TRADE`, 거래 잠금 일관성 |
| `student_character_profiles` | G | `classroom_id uuid`, `classroom_student_id uuid FK(classroom_students)`, `state_version bigint D=0`, `equipment_revision bigint D=0`, `equipment_changed_at timestamptz D=now()` | 학생당 최대 1개, 단일 고정 체형, 슬롯별 장착 상태의 낙관적 버전 |
| `character_equipment` | G | `classroom_id uuid`, `classroom_student_id uuid FK(classroom_students)`, `character_profile_id uuid FK(student_character_profiles)`, `inventory_item_id uuid FK(inventory_items)`, `slot varchar(40)`, `state_version bigint D=0`, `equipped_at timestamptz D=now()` | 장착 표현 상태, 프로필별 슬롯 UQ, 인벤토리 아이템 UQ, 낙관적 버전 |
| `trades` | S | `proposer_student_id uuid FK(classroom_students)`, `counterpart_student_id uuid FK(classroom_students)`, `description text?`, `policy_snapshot_id uuid? FK(policy_snapshots)`, `transaction_group_id uuid? FK(transaction_groups)`, `counterpart_decided_at timestamptz?`, `teacher_decided_at timestamptz?`, `expires_at timestamptz?` | SM-TRD-001, 자기거래 금지 |
| `trade_money_legs` | I | `trade_id uuid FK(trades)`, `from_student_id uuid FK(classroom_students)`, `to_student_id uuid FK(classroom_students)`, `amount bigint` | `(trade_id,from_student_id,to_student_id) UQ`, 금액>0 |
| `trade_item_legs` | I | `trade_id uuid FK(trades)`, `inventory_item_id uuid FK(inventory_items)`, `from_student_id uuid FK(classroom_students)`, `to_student_id uuid FK(classroom_students)` | `(trade_id,inventory_item_id) UQ`, 동일 학생 금지 |
| `trade_decisions` | I | `trade_id uuid FK(trades)`, `decision_type varchar(40)`, `actor_type varchar(40)`, `actor_id uuid`, `decision varchar(40)`, `reason text?`, `decided_at timestamptz D=now()` | `(trade_id,decision_type) UQ` |

### 4.4 예금·적금·투자·교육용 대출·신용

| 테이블 | 공통 | 추가 컬럼 | 키·상태 |
| --- | --- | --- | --- |
| `financial_products` | S | `product_type varchar(40)`, `name varchar(120)`, `description text?` | `(classroom_id,product_type,lower(name)) UQ` |
| `financial_product_versions` | I | `financial_product_id uuid FK(financial_products)`, `version integer`, `product_type varchar(40)`, `term_units integer`, `interest_rate_bps integer`, `early_termination_rate_bps integer`, `installment_count integer?`, `installment_amount bigint?`, `minimum_amount bigint`, `maximum_amount bigint`, `status varchar(40)`, `published_at timestamptz`, `retired_at timestamptz?` | `(financial_product_id,version) UQ`, 게시 후 불변 |
| `deposit_accounts` | S | `classroom_student_id uuid FK(classroom_students)`, `product_version_id uuid FK(financial_product_versions)`, `principal_amount bigint`, `expected_interest_amount bigint`, `opened_at timestamptz D=now()`, `matures_at timestamptz`, `closed_at timestamptz?`, `opening_transaction_group_id uuid FK(transaction_groups)`, `closing_transaction_group_id uuid? FK(transaction_groups)` | SM-DEP-001 |
| `savings_accounts` | S | `classroom_student_id uuid FK(classroom_students)`, `product_version_id uuid FK(financial_product_versions)`, `installment_amount bigint`, `installment_count integer`, `paid_installment_count integer D=0`, `principal_balance bigint D=0`, `expected_interest_amount bigint`, `opened_at timestamptz D=now()`, `matures_at timestamptz`, `closed_at timestamptz?`, `opening_transaction_group_id uuid? FK(transaction_groups)`, `closing_transaction_group_id uuid? FK(transaction_groups)` | SM-SAV-001 |
| `savings_installments` | S | `savings_account_id uuid FK(savings_accounts)`, `installment_number integer`, `scheduled_amount bigint`, `due_at timestamptz`, `retryable_until timestamptz`, `paid_at timestamptz?`, `transaction_group_id uuid? FK(transaction_groups)` | `(savings_account_id,installment_number) UQ`, SM-SAV-002 |
| `investment_markets` | S | `active_scenario_version_id uuid? FK(investment_scenario_versions)`, `current_step integer D=0`, `opened_at timestamptz?`, `closed_at timestamptz?` | 학급당 1개 UQ, SM-IVT-001 |
| `investment_scenario_versions` | I | `investment_market_id uuid FK(investment_markets)`, `version integer`, `name varchar(120)`, `scenario_seed varchar(128)?`, `investment_mode varchar(20) D=LONG_TERM`, `scenario_source varchar(30) D=AI_GENERATED`, `reference_series_code varchar(40)?`, `reference_event_name varchar(120)?`, `reference_source_name varchar(120)?`, `reference_source_url varchar(500)?`, `reference_period_start date?`, `reference_period_end date?`, `status varchar(40)`, `published_at timestamptz?` | `(investment_market_id,version) UQ`, 역사 지수 참조는 LONG_TERM+HISTORICAL_INDEX에서만 필수, 게시 후 불변 |
| `investment_assets` | S | `scenario_version_id uuid FK(investment_scenario_versions)`, `symbol varchar(20)`, `name varchar(120)`, `fictional_company_profile varchar(1000)`, `display_order integer D=0` | `(scenario_version_id,symbol) UQ` |
| `investment_price_steps` | I | `investment_asset_id uuid FK(investment_assets)`, `step_number integer`, `price bigint`, `change_rate_bps integer`, `revealed_at timestamptz?` | `(investment_asset_id,step_number) UQ`, 가격>0 |
| `investment_orders` | S | `classroom_student_id uuid FK(classroom_students)`, `investment_asset_id uuid FK(investment_assets)`, `side varchar(10)`, `quantity integer`, `executed_price bigint?`, `executed_price_step integer?`, `transaction_group_id uuid? FK(transaction_groups)`, `submitted_at timestamptz D=now()`, `executed_at timestamptz?`, `reject_reason varchar(80)?` | SM-IVT-002, 정수 수량>0 |
| `investment_positions` | 없음 | `id uuid PK`, `classroom_id uuid`, `classroom_student_id uuid FK(classroom_students)`, `investment_asset_id uuid FK(investment_assets)`, `quantity integer D=0`, `average_price bigint D=0`, `state_version bigint D=0`, `created_at timestamptz D=now()`, `updated_at timestamptz D=now()` | `(classroom_student_id,investment_asset_id) UQ`, 수량 0 이상 |
| `loan_policy_versions` | I | `version integer`, `maximum_principal bigint`, `interest_rate_bps integer`, `term_units integer`, `installment_count integer`, `grace_units integer D=0`, `credit_adjustments jsonb D={}`, `status varchar(40)`, `effective_from timestamptz`, `superseded_at timestamptz?` | `(classroom_id,version) UQ`, 적용 후 불변 |
| `loan_applications` | S | `classroom_student_id uuid FK(classroom_students)`, `loan_policy_version_id uuid FK(loan_policy_versions)`, `requested_amount bigint`, `approved_amount bigint?`, `credit_level_snapshot varchar(40)`, `credit_score_snapshot integer`, `decision_reason text?`, `decided_by_user_id uuid? FK(users)`, `decided_at timestamptz?` | 상태 `APPLIED·APPROVED·REJECTED·CANCELED` |
| `loans` | S | `loan_application_id uuid FK(loan_applications)`, `principal_amount bigint`, `applied_interest_rate_bps integer`, `credit_level_at_approval varchar(40)`, `outstanding_principal bigint`, `outstanding_interest bigint D=0`, `disbursement_transaction_group_id uuid FK(transaction_groups)`, `started_at timestamptz`, `matures_at timestamptz`, `closed_at timestamptz?` | `loan_application_id UQ`, SM-LON-001 실행 이후 상태 |
| `loan_installments` | S | `loan_id uuid FK(loans)`, `installment_number integer`, `scheduled_principal bigint`, `scheduled_interest bigint`, `due_at timestamptz`, `grace_until timestamptz`, `paid_principal bigint D=0`, `paid_interest bigint D=0`, `paid_at timestamptz?`, `transaction_group_id uuid? FK(transaction_groups)` | `(loan_id,installment_number) UQ`, 금액 0 이상 |
| `credit_profiles` | 없음 | `classroom_student_id uuid PK FK(classroom_students)`, `classroom_id uuid`, `level varchar(40)`, `score integer`, `state_version bigint D=0`, `evaluated_at timestamptz`, `created_at timestamptz D=now()`, `updated_at timestamptz D=now()` | 점수 0~1000, SM-CRD-001 |
| `credit_events` | I | `classroom_student_id uuid FK(credit_profiles)`, `event_type varchar(40)`, `score_delta integer`, `score_before integer`, `score_after integer`, `level_before varchar(40)`, `level_after varchar(40)`, `reason text`, `related_type varchar(40)?`, `related_id uuid?`, `occurred_at timestamptz D=now()` | 전후 점수 산식, append-only |

### 4.5 지도 활동·퀴즈·투표

| 테이블 | 공통 | 추가 컬럼 | 키·상태 |
| --- | --- | --- | --- |
| `activity_sessions` | S | `activity_type varchar(40)`, `policy_snapshot_id uuid? FK(policy_snapshots)`, `created_by_user_id uuid FK(users)`, `public_display_enabled boolean D=false`, `starts_at timestamptz?`, `ends_at timestamptz?` | SM-ACT-001 |
| `activity_participants` | I | `activity_session_id uuid FK(activity_sessions)`, `classroom_student_id uuid FK(classroom_students)`, `participant_status varchar(40)`, `joined_at timestamptz?`, `completed_at timestamptz?` | `(activity_session_id,classroom_student_id) UQ` |
| `activity_presentation_slides` | I | `activity_session_id uuid FK(activity_sessions)`, `sequence_number integer`, `title varchar(120)`, `image_url text` | `(activity_session_id,sequence_number) UQ`, 순서 1부터. 담은 뒤 수정하지 않고 `activity_sessions.presentation_cursor`만 옮김 |
| `quiz_questions` | S | `created_by_user_id uuid FK(users)`, `content_type varchar(40)`, `title varchar(160)?` | 문제 컨테이너, 교사 화면 전용 |
| `quiz_question_versions` | I | `quiz_question_id uuid FK(quiz_questions)`, `version integer`, `content_type varchar(40)`, `question_type varchar(40)`, `status varchar(40)`, `prompt text`, `explanation text?`, `source varchar(40)`, `ai_run_id uuid? FK(ai_runs)`, `published_at timestamptz?`, `retired_at timestamptz?` | `(quiz_question_id,version) UQ`, SM-QST-001 |
| `quiz_options` | I | `quiz_question_version_id uuid FK(quiz_question_versions)`, `option_key varchar(20)`, `option_text text`, `is_correct boolean`, `sort_order integer` | `(quiz_question_version_id,option_key) UQ`, 정답 1개 이상 |
| `quiz_sessions` | 없음 | `activity_session_id uuid PK FK(activity_sessions)`, `classroom_id uuid`, `mode varchar(20)`, `content_type varchar(40)`, `reward_policy_public jsonb D={}`, `leaderboard_public boolean D=false`, `status varchar(40)`, `state_version bigint D=0`, `status_changed_at timestamptz D=now()`, `created_at timestamptz D=now()`, `updated_at timestamptz D=now()` | SM-QZS-001 |
| `quiz_session_questions` | I | `quiz_session_id uuid FK(quiz_sessions)`, `quiz_question_version_id uuid FK(quiz_question_versions)`, `sequence_number integer`, `prompt_snapshot text`, `options_snapshot jsonb`, `correct_answer_snapshot jsonb`, `score_value integer`, `time_limit_seconds integer?` | `(quiz_session_id,sequence_number) UQ`, 실행 후 불변 |
| `quiz_submissions` | S | `quiz_session_id uuid FK(quiz_sessions)`, `classroom_student_id uuid FK(classroom_students)`, `mode varchar(20)`, `answer_sheet_version integer`, `submitted_at timestamptz?` | `(quiz_session_id,classroom_student_id,answer_sheet_version) UQ` |
| `quiz_answers` | I | `quiz_submission_id uuid FK(quiz_submissions)`, `quiz_session_question_id uuid FK(quiz_session_questions)`, `selected_option_key varchar(20)?`, `free_text text?`, `is_correct boolean?`, `score_awarded integer?`, `response_time_ms integer?`, `answered_at timestamptz D=now()` | `(quiz_submission_id,quiz_session_question_id) UQ` |
| `quiz_result_versions` | I | `quiz_session_id uuid FK(quiz_sessions)`, `version integer`, `status varchar(40)`, `reward_transaction_group_id uuid? FK(transaction_groups)`, `published_at timestamptz?`, `supersedes_result_version_id uuid? FK(quiz_result_versions)` | `(quiz_session_id,version) UQ`, 게시 후 불변 |
| `quiz_student_results` | I | `quiz_result_version_id uuid FK(quiz_result_versions)`, `classroom_student_id uuid FK(classroom_students)`, `score integer`, `correct_count integer`, `rank integer?`, `reward_amount bigint D=0`, `total_response_time_ms bigint?` | `(quiz_result_version_id,classroom_student_id) UQ` |
| `quiz_question_issues` | S | `quiz_session_question_id uuid FK(quiz_session_questions)`, `reported_by_student_id uuid? FK(classroom_students)`, `reason text`, `resolution text?`, `resolved_by_user_id uuid? FK(users)`, `resolved_at timestamptz?` | 학생별·세션 문항별 신고 1회 부분 UQ, SM-QAD-001 |
| `regrading_runs` | S | `quiz_session_id uuid FK(quiz_sessions)`, `source_result_version_id uuid FK(quiz_result_versions)`, `new_result_version_id uuid? FK(quiz_result_versions)`, `reason text`, `started_by_user_id uuid FK(users)`, `started_at timestamptz D=now()`, `completed_at timestamptz?` | 세션별 활성 재채점 1개 부분 UQ, 상태 `REQUESTED·RUNNING·COMPLETED·FAILED` |
| `votes` | 없음 | `activity_session_id uuid PK FK(activity_sessions)`, `classroom_id uuid`, `title varchar(160)`, `description text?`, `anonymous boolean D=true`, `status varchar(40)`, `state_version bigint D=0`, `opens_at timestamptz?`, `closes_at timestamptz?`, `created_at timestamptz D=now()`, `updated_at timestamptz D=now()` | 상태 `READY·OPEN·CLOSED·RESULT_PUBLISHED` |
| `vote_options` | I | `vote_id uuid FK(votes)`, `label varchar(160)`, `description text?`, `sort_order integer`, `budget_amount bigint?` | `(vote_id,sort_order) UQ` |
| `vote_eligibilities` | I | `vote_id uuid FK(votes)`, `classroom_student_id uuid FK(classroom_students)`, `eligibility_token_hash varchar(128)`, `used_at timestamptz?` | `(vote_id,classroom_student_id) UQ`, `(vote_id,eligibility_token_hash) UQ |
| `vote_ballots` | I | `vote_id uuid FK(votes)`, `vote_option_id uuid FK(vote_options)`, `eligibility_token_hash varchar(128)`, `receipt_hash varchar(128)`, `cast_at timestamptz D=now()` | `(vote_id,eligibility_token_hash) UQ`, `receipt_hash UQ` |

### 4.6 AI·비동기 작업·내보내기·리포트

| 테이블 | 공통 | 추가 컬럼 | 키·상태 |
| --- | --- | --- | --- |
| `ai_runs` | S | `async_operation_id uuid? FK(async_operations)`, `requested_by_user_id uuid? FK(users)`, `run_type varchar(40)`, `trigger_type varchar(40)`, `analysis_period_from timestamptz?`, `analysis_period_to timestamptz?`, `model_provider varchar(40)?`, `model_id varchar(120)?`, `prompt_version varchar(40)?`, `input_scope jsonb`, `validation_result jsonb?`, `requested_at timestamptz D=now()`, `completed_at timestamptz?`, `error_code varchar(80)?`, `retention_until timestamptz` | 자동·규칙 전용 실행은 요청 사용자·모델 메타 NULL, `async_operation_id UQ` 또는 NULL, SM-AIR-001 |
| `ai_artifacts` | I | `ai_run_id uuid FK(ai_runs)`, `artifact_type varchar(40)`, `content jsonb`, `schema_version varchar(20)`, `safety_status varchar(40)` | 생성 후 불변 |
| `ai_proposals` | S | `ai_run_id uuid FK(ai_runs)`, `proposal_type varchar(40)`, `source_type varchar(40)`, `severity varchar(20)?`, `metric_code varchar(80)?`, `direction varchar(20)?`, `dedupe_key varchar(200)?`, `proposed_values jsonb`, `allowed_ranges jsonb`, `expires_at timestamptz`, `applied_policy_version_id uuid? FK(classroom_policy_versions)` | 상태 `AWAITING_TEACHER·ACKNOWLEDGED·APPROVED·REJECTED·EXPIRED·APPLIED·APPLY_FAILED`, 같은 자동 분석 구간의 dedupe key는 서비스에서 멱등 저장 |
| `ai_decisions` | I | `ai_proposal_id uuid FK(ai_proposals)`, `decided_by_user_id uuid FK(users)`, `decision varchar(40)`, `reason text?`, `decided_at timestamptz D=now()` | 결정 `ACKNOWLEDGE·APPROVE·REJECT`, 정책 적용 최종 결정은 1개 UQ |
| `economic_event_drafts` | S | `ai_run_id uuid? FK(ai_runs)`, `activity_session_id uuid? FK(activity_sessions)`, `source_type varchar(40)`, `source_proposal_id uuid? FK(ai_proposals)`, `investment_price_step_id uuid? FK(investment_price_steps)`, `title varchar(160)`, `content text`, `economic_effect jsonb`, `valid_from timestamptz?`, `valid_until timestamptz?`, `revealed_at timestamptz?` | 상태 `DRAFT·APPROVED·SCHEDULED·REVEALED·APPLIED·CANCELED`, INVESTMENT_NEWS는 가격 단계 연결 필수·단계당 활성 뉴스 1개 |
| `async_operations` | S | `operation_type varchar(40)`, `requested_by_user_id uuid? FK(users)`, `progress_percent integer D=0`, `result_location varchar(512)?`, `error_code varchar(80)?`, `error_detail text?`, `started_at timestamptz?`, `completed_at timestamptz?`, `expires_at timestamptz?` | 진행률 0~100 |
| `export_operations` | 없음 | `async_operation_id uuid PK FK(async_operations)`, `classroom_id uuid`, `requested_by_user_id uuid FK(users)`, `export_type varchar(40)`, `filter_snapshot jsonb`, `result_s3_key varchar(512)?`, `retention_until timestamptz`, `created_at timestamptz D=now()` | 비밀 URL 저장 금지 |
| `report_snapshots` | I | `classroom_student_id uuid? FK(classroom_students)`, `report_type varchar(40)`, `schema_version varchar(20)`, `snapshot jsonb`, `as_of timestamptz`, `retention_until timestamptz` | 개인·학급 리포트 구분, 생성 후 불변 |

<!-- TABLES:END -->

## 5. 유일성·부분 유일 인덱스

| ID | 테이블 | 정의 | 이유 |
| --- | --- | --- | --- |
| UK-001 | `classroom_teachers` | `UNIQUE (classroom_id) WHERE role='OWNER' AND ended_at IS NULL` | 활성 소유 교사 최대 1명; 생성·위임 서비스가 정확히 1명을 보장 |
| UK-002 | `classroom_teachers` | `UNIQUE (classroom_id,user_id,role) WHERE ended_at IS NULL` | 활성 역할 중복 방지 |
| UK-003 | `classroom_students` | `UNIQUE (classroom_id,roster_number) WHERE retired_at IS NULL` | 활성 출석번호 중복 방지 |
| UK-004 | `classroom_students` | `UNIQUE (classroom_id,lower(nickname)) WHERE retired_at IS NULL` | 활성 닉네임 대소문자 중복 방지 |
| UK-005 | `student_sessions` | `UNIQUE (classroom_student_id) WHERE status='ACTIVE'` | 학생 활성 세션 1개 |
| UK-006 | `ledger_accounts` | `UNIQUE (classroom_student_id) WHERE account_type='STUDENT_WALLET' AND status='ACTIVE'` | 학생 지갑 1개 |
| UK-007 | `ledger_accounts` | `UNIQUE (classroom_id,system_owner_key) WHERE account_type='CLASSROOM_FUND' AND status='ACTIVE'` | 학급 공동기금 1개 |
| UK-008 | `investment_markets` | `UNIQUE (classroom_id) WHERE status <> 'CLOSED'` | 활성 투자시장 1개 |
| UK-009 | `classroom_policy_versions` | `UNIQUE (classroom_id,policy_area) WHERE superseded_at IS NULL` | 영역별 현재 적용 정책 1개 |
| UK-010 | `ai_decisions` | `UNIQUE (ai_proposal_id)` | 제안 최종 결정 1개 |
| UK-011 | `school_memberships` | `UNIQUE (school_id,user_id,role) WHERE status='ACTIVE' AND ended_at IS NULL` | 활성 학교 역할 중복 방지 |
| UK-012 | `salary_roles` | `UNIQUE (classroom_id,lower(name))` | 학급 내 역할·급여명 중복 방지 |
| UK-013 | `store_products` | `UNIQUE (classroom_id,lower(name),product_kind)` | 학급 상점의 유형별 상품명 중복 방지 |
| UK-014 | `financial_products` | `UNIQUE (classroom_id,product_type,lower(name))` | 학급 금융상품의 유형별 이름 중복 방지 |
| UK-015 | `realtime_streams` | `UNIQUE (classroom_id,stream_type,coalesce(resource_id,zero_uuid))` | 같은 리소스의 활성 실시간 stream 중복 방지 |
| UK-016 | `salary_roles` | `UNIQUE (classroom_id,role_code)` | 학생 배정과 API가 참조하는 학급 내 안정 역할 코드 |
| UK-017 | `autonomous_schedule_modules` | `UNIQUE (autonomous_schedule_id,module_code)` | 같은 일정에 같은 모듈 중복 연결 방지 |
| UK-018 | `school_ai_setting_versions` | `UNIQUE (school_id) WHERE superseded_at IS NULL` | 학교별 현재 AI 설정을 한 버전으로 제한 |
| UK-019 | `student_character_profiles` | `UNIQUE (classroom_id,classroom_student_id)` | 학생별 캐릭터 프로필 최대 1개 |
| UK-020 | `character_equipment` | `UNIQUE (character_profile_id,slot)` | 프로필별 슬롯당 장착 행 최대 1개 |
| UK-021 | `character_equipment` | `UNIQUE (inventory_item_id)` | 같은 보유 아이템의 중복 장착 방지 |

UK-001~009·UK-011~016·UK-018의 부분·함수 유일성은 PostgreSQL `CREATE UNIQUE INDEX`로, UK-010·017·019~021은 테이블 `UNIQUE` 제약으로 구현합니다. `classrooms.classroom_code`의 전역 유일 제약은 별도 테이블 UQ로 관리합니다. 학급 소유 교사는 최초 학급 생성 트랜잭션에서 반드시 함께 삽입하고, 서비스 인수 테스트로 “0명” 상태가 커밋되지 않도록 보완합니다.

## 6. 검사 제약조건

| ID | 대상 | 조건 |
| --- | --- | --- |
| CK-001 | `classrooms` | `grade BETWEEN 4 AND 6` |
| CK-002 | `classrooms` | `actual_ends_on IS NULL OR actual_starts_on IS NULL OR actual_ends_on >= actual_starts_on` |
| CK-003 | `autonomous_schedules` | `day_of_week BETWEEN 1 AND 7 AND local_end_time > local_start_time` |
| CK-004 | 모든 금액 | 환불이 없으므로 도메인 금액 `>=0`; 원장 `ledger_entries.amount`만 음수 허용 |
| CK-005 | 모든 rate | 세율·기본 금리 `BETWEEN 0 AND 10000`; 투자 등락률은 `BETWEEN -10000 AND 100000` |
| CK-006 | `payslips` | `gross_amount=deduction_amount+tax_amount+net_amount` |
| CK-007 | `ledger_accounts` | 학생 계정과 시스템 계정 컬럼 중 정확히 하나만 존재 |
| CK-008 | `ledger_accounts` | `account_type='STUDENT_WALLET'`이면 `current_balance>=0` |
| CK-009 | `ledger_accounts` | `next_entry_sequence>=1` |
| CK-010 | `ledger_entries` | `amount<>0 AND balance_before+amount=balance_after AND account_sequence>=1` |
| CK-011 | `transaction_groups` | 복구 거래는 자기 자신을 참조하지 않고 `root_transaction_group_id<>id` |
| CK-012 | `store_stocks` | `available_quantity>=0 AND reserved_quantity>=0` |
| CK-013 | `store_orders` | `subtotal_amount+tax_amount=total_amount` |
| CK-014 | `store_order_items` | `quantity>0`, 금액 산식과 `vat_rate_bps BETWEEN 0 AND 10000` |
| CK-015 | `inventory_items` | 상태는 `AVAILABLE·LOCKED_TRADE`이며 `LOCKED_TRADE`일 때만 `locked_trade_id` 존재 |
| CK-016 | `trades` | `proposer_student_id<>counterpart_student_id` |
| CK-017 | `trade_money_legs` | `amount>0 AND from_student_id<>to_student_id` |
| CK-018 | `trade_item_legs` | `from_student_id<>to_student_id` |
| CK-019 | 금융 원금·이자 | 금액 0 이상, 가입·대출 원금은 0보다 큼 |
| CK-020 | `savings_accounts` | `0<=paid_installment_count<=installment_count` |
| CK-021 | `savings_installments` | `installment_number>0 AND retryable_until>=due_at` |
| CK-022 | `investment_price_steps` | `step_number>=0 AND price>0` |
| CK-023 | `investment_orders` | `side IN ('BUY','SELL') AND quantity>0` |
| CK-024 | `investment_positions` | `quantity>=0 AND average_price>=0` |
| CK-025 | `loans` | `0<=outstanding_principal<=principal_amount AND outstanding_interest>=0` |
| CK-026 | `loan_installments` | 예정·납부 금액 0 이상, 납부액은 예정액 이하 |
| CK-027 | `credit_profiles` | `score BETWEEN 0 AND 1000` |
| CK-028 | `credit_events` | `score_before+score_delta=score_after`, 전후 점수 0~1000 |
| CK-029 | `quiz_session_questions` | `sequence_number>0 AND score_value>=0 AND time_limit_seconds>0` 또는 NULL |
| CK-030 | `quiz_answers` | 객관식 선택과 자유응답 중 최대 하나만 존재, 점수·응답시간 0 이상 |
| CK-031 | `quiz_student_results` | 점수·정답 수·보상·응답시간 0 이상, 순위는 1 이상 또는 NULL |
| CK-032 | `vote_options` | `sort_order>=0 AND (budget_amount IS NULL OR budget_amount>=0)` |
| CK-033 | `ai_runs` | `retention_until>=requested_at` |
| CK-034 | `async_operations` | `progress_percent BETWEEN 0 AND 100` |
| CK-035 | 보관 테이블 | `retention_until>=created_at` 또는 기준 시각 |
| CK-036 | `classrooms` | `classroom_code`는 혼동 문자를 제외한 영문 대문자·숫자 8자, `currency_name`은 한글·영문·숫자 1~10자 |
| CK-037 | `classrooms` | `planned_student_count>0` |
| CK-038 | `salary_roles` | `role_code`는 영문 대문자로 시작하는 영문 대문자·숫자·밑줄 1~40자 |
| CK-039 | `school_ai_setting_versions` | `version>0`, 사유는 공백이 아니며 `superseded_at`은 `effective_from`보다 뒤 |
| CK-040 | `student_character_profiles` | `state_version>=0 AND equipment_revision>=0` |
| CK-041 | `character_equipment` | 슬롯은 10개 허용값 중 하나이고 `state_version>=0` |
| CK-042 | `classrooms` | `class_number IS NULL OR class_number BETWEEN 1 AND 99` |
| CK-043 | `loan_installments` | `status IN ('SCHEDULED','MISSED','PAID')` |

상태 `CHECK` 값은 [핵심 상태 머신](STATE_MACHINES.md)의 전체 상태를 사용합니다. 상태 머신이 있는 테이블은 해당 `SM-*`의 상태 집합을 DDL에 그대로 넣고, 상태 머신이 없는 보조 테이블은 4장의 상태 목록을 사용합니다.

## 7. 원장·불변성·트랜잭션 제약

### 7.1 커밋 거래 균형

`transaction_groups.status='COMMITTED'`인 거래는 커밋 시점에 다음을 모두 만족해야 합니다.

1. `ledger_entries`가 2개 이상입니다.
2. `sum(ledger_entries.amount)=0`입니다.
3. 모든 항목이 같은 `classroom_id`입니다.
4. 계정별 `account_sequence`가 기존 `next_entry_sequence`와 일치합니다.
5. `balance_before+amount=balance_after`입니다.
6. 마지막 항목의 `balance_after`와 `ledger_accounts.current_balance`가 일치합니다.

행 단위 `CHECK`만으로 그룹 합계를 검증할 수 없으므로 `DEFERRABLE INITIALLY DEFERRED` constraint trigger를 사용합니다. 트리거는 `ledger_entries` 변경과 `transaction_groups`의 `COMMITTED` 전이 양쪽에 설치합니다.

### 7.2 동시성 잠금 순서

교착을 줄이기 위해 금액·아이템 명령은 항상 다음 순서로 잠급니다.

1. 관련 `ledger_accounts`를 UUID 오름차순으로 `FOR UPDATE`
2. `store_stocks` 또는 `inventory_items`를 UUID 오름차순으로 `FOR UPDATE`
3. 도메인 루트 주문·거래·금융계좌를 `FOR UPDATE`
4. 거래 그룹·원장 항목·상태 이력·outbox 삽입
5. 계정 잔액·sequence와 도메인 상태 갱신

### 7.3 완료 데이터 불변

다음 행은 운영자 SQL을 포함해 일반 애플리케이션 역할에서 `UPDATE`·`DELETE`할 수 없습니다.

- `COMMITTED` transaction group의 가격·유형·정책·복구 연결
- 모든 `ledger_entries`
- `policy_snapshots`, `policy_snapshot_sources`
- 게시된 상품·금융상품·퀴즈·투자 시나리오 버전
- `audit_events`, `state_transitions`, `credit_events`
- 게시된 `quiz_result_versions`와 그 학생 결과
- `vote_ballots`

`outbox_events`는 payload와 식별 필드는 불변이지만 `published_at`, `publish_attempts`, `last_error`만 발행기 역할이 갱신할 수 있습니다.

### 7.4 복구 거래

- `recovery_of_group_id`는 `COMMITTED` 원거래만 참조합니다.
- 최초 원거래는 `root_transaction_group_id=id`를 저장합니다.
- 재복구도 동일 root를 유지합니다.
- 복구 거래는 반대 부호 원장 항목과 필요한 새 업무 상태를 별도로 커밋합니다.
- 원거래·원장 항목은 수정하거나 삭제하지 않습니다.

### 7.5 정책 스냅샷

가격·세율·금리·반올림·상품 조건을 계산한 뒤 다음 순서로 저장합니다.

1. 정규화된 계산 입력으로 `snapshot_hash` 생성
2. `policy_snapshots.calculated_values` 저장
3. 사용한 정책·상품·가격 버전을 `policy_snapshot_sources`에 저장
4. 거래 그룹이 스냅샷을 참조
5. 원장과 도메인 상태를 커밋

같은 학급의 같은 해시가 이미 있으면 기존 스냅샷을 재사용할 수 있습니다.

### 7.6 디지털 아이템 소유와 장착

- `inventory_items`는 소유자와 거래 잠금만 기록하는 단일 소유 원장입니다. 장착 중인 아이템도
  소유 원장 상태는 `AVAILABLE`이며, 실제 장착 여부는 `character_equipment` 존재로 판단합니다.
- `character_equipment`의 학급·학생·프로필·인벤토리 복합 FK는 다른 학급 또는 다른 학생의
  아이템을 장착하는 것을 차단합니다.
- `trg_character_equipment_validate_item`은 인벤토리 행을 잠근 뒤 `AVAILABLE`인 소유 아이템과
  `character_code` 접두사의 슬롯 일치를 검사합니다. `bg_*`는 `BACKGROUND`로 정규화합니다.
- `trg_inventory_items_equipment_lock`은 장착 행이 남아 있는 아이템을 `LOCKED_TRADE`로 바꾸지
  못하게 합니다. 서비스도 거래 등록 전에 장착 여부를 확인해야 합니다.
- `student_character_profiles`, `character_equipment`, `inventory_items`의 `state_version`은 각각
  낙관적 동시성에 사용합니다. 외부 장착 명령은 프로필 버전을 기준으로 충돌을 판정합니다.

## 8. 조회 인덱스

PK·UQ·FK 인덱스 외에 다음 인덱스를 만듭니다. PostgreSQL은 FK 자식 인덱스를 자동 생성하지 않으므로 모든 FK 선두 컬럼에 별도 인덱스가 필요합니다.

| ID | 인덱스 | 컬럼·조건 | 지원 조회 |
| --- | --- | --- | --- |
| IX-001 | `ix_classrooms_school_status` | `(school_id,status)` | 학교별 학급 목록 |
| IX-002 | `ix_classroom_students_classroom_status` | `(classroom_id,status,roster_number)` | 학생 명단 |
| IX-003 | `ix_student_sessions_expires` | `(expires_at) WHERE status='ACTIVE'` | 세션 만료 |
| IX-004 | `ix_classroom_modules_status` | `(classroom_id,status,module_code)` | 활성 모듈 |
| IX-005 | `ix_autonomous_schedules_lookup` | `(classroom_id,day_of_week,local_start_time) WHERE enabled=true` | 자율시간 계산 |
| IX-006 | `ix_payslips_student_issued` | `(classroom_student_id,issued_at DESC)` | 월급명세서 |
| IX-007 | `ix_transaction_groups_classroom_committed` | `(classroom_id,committed_at DESC,id) WHERE status='COMMITTED'` | 거래 관리 기본 목록 |
| IX-008 | `ix_transaction_groups_type_status` | `(classroom_id,transaction_type,status,created_at DESC)` | 거래 유형 필터 |
| IX-009 | `ix_transaction_groups_recovery` | `(recovery_of_group_id) WHERE recovery_of_group_id IS NOT NULL` | 복구 연결 |
| IX-010 | `ix_ledger_entries_account_sequence` | `(ledger_account_id,account_sequence DESC)` | 잔액 원장 |
| IX-011 | `ix_ledger_entries_group` | `(transaction_group_id)` | 거래 상세 |
| IX-012 | `ix_idempotency_expires` | `(expires_at)` | 만료 정리 |
| IX-013 | `ix_state_transitions_aggregate` | `(classroom_id,aggregate_type,aggregate_id,state_version DESC)` | 상태 이력 |
| IX-014 | `ix_outbox_unpublished` | `(occurred_at,event_id) WHERE published_at IS NULL` | 발행 재시도 |
| IX-015 | `ix_audit_events_classroom_time` | `(classroom_id,occurred_at DESC,id)` | 감사 조회 |
| IX-016 | `ix_store_products_classroom_status` | `(classroom_id,status,product_kind)` | 상점 상품 |
| IX-017 | `ix_store_orders_student_time` | `(classroom_student_id,created_at DESC)` | 학생 주문 |
| IX-018 | `ix_store_orders_fulfillment` | `(classroom_id,status,created_at)` | 수령 처리 |
| IX-019 | `ix_inventory_items_owner_status` | `(classroom_student_id,status,acquired_at DESC)` | 보유·거래 잠금 목록 |
| IX-020 | `ix_trades_proposer_status` | `(proposer_student_id,status,created_at DESC)` | 보낸 거래 |
| IX-021 | `ix_trades_counterpart_status` | `(counterpart_student_id,status,created_at DESC)` | 받은 거래 |
| IX-022 | `ix_trades_teacher_queue` | `(classroom_id,status,created_at) WHERE status='AWAITING_TEACHER'` | 교사 승인함 |
| IX-023 | `ix_deposit_accounts_maturity` | `(classroom_id,status,matures_at)` | 예금 만기 |
| IX-024 | `ix_savings_installments_due` | `(classroom_id,status,due_at)` | 적금 자동납입 |
| IX-025 | `ix_investment_price_steps_reveal` | `(investment_asset_id,step_number DESC)` | 가격 그래프 |
| IX-026 | `ix_investment_orders_student_time` | `(classroom_student_id,submitted_at DESC)` | 주문 이력 |
| IX-027 | `ix_loan_applications_queue` | `(classroom_id,status,created_at)` | 대출 승인함 |
| IX-028 | `ix_loan_installments_due` | `(classroom_id,status,due_at)` | 상환·연체 |
| IX-029 | `ix_credit_events_student_time` | `(classroom_student_id,occurred_at DESC)` | 신용 변화 |
| IX-030 | `ix_activity_sessions_classroom_status` | `(classroom_id,status,starts_at DESC)` | 지도 활동 |
| IX-031 | `ix_quiz_submissions_session_student` | `(quiz_session_id,classroom_student_id)` | 제출 현황 |
| IX-032 | `ix_quiz_results_session_version` | `(quiz_session_id,version DESC)` | 최신 결과 |
| IX-033 | `ix_vote_ballots_vote_option` | `(vote_id,vote_option_id)` | 익명 집계 |
| IX-034 | `ix_ai_runs_classroom_status` | `(classroom_id,status,requested_at DESC)` | AI 작업 |
| IX-035 | `ix_async_operations_status` | `(classroom_id,status,created_at)` | 비동기 작업 감시 |
| IX-036 | `ix_report_snapshots_retention` | `(retention_until)` | 보관 만료 |
| IX-037 | `ix_autonomous_schedule_modules_module` | `(classroom_id,module_code,autonomous_schedule_id)` | 모듈별 자율시간 조회 |
| IX-038 | `ix_student_character_profiles_student` | `(classroom_id,classroom_student_id)` | 본인 캐릭터 프로필 조회 |
| IX-039 | `ix_character_equipment_profile` | `(classroom_id,character_profile_id)` | 프로필 장착 슬롯 조회 |
| IX-040 | `ix_character_equipment_owner` | `(classroom_id,classroom_student_id)` | 학생별 장착 상태 조회 |

JSONB 인덱스는 기본 생성하지 않습니다. 실제 쿼리와 `EXPLAIN ANALYZE` 근거가 생긴 필드만 표현식 또는 GIN 인덱스로 추가합니다.

## 9. 공개 projection과 민감정보 경계

공개 화면은 다음 개인 테이블을 직접 조회하지 않습니다.

- `ledger_accounts`, `ledger_entries`
- `deposit_accounts`, `savings_accounts`
- `investment_orders`, `investment_positions`
- `loan_applications`, `loans`, `loan_installments`
- `credit_profiles`, `credit_events`
- `trades`, `trade_*`
- TEST 모드 `quiz_submissions`, `quiz_answers`, `quiz_student_results`
- `vote_eligibilities`, `vote_ballots` 원문

공개 이벤트는 애플리케이션 query 계층에서 다음 집계 DTO를 생성합니다.

- 교사가 `leaderboard_public=true`로 확정한 퀴즈 GAME의 1~3위 순위·닉네임·장착 캐릭터만
- 투표 선택지별 합계
- 공동기금 총액·사용처
- 투자 시장 공개 가격 단계
- 지도 활동 진행 상태

공개 projection을 별도 테이블로 물리화할지는 부하 테스트 후 결정합니다. MVP 초기에는 트랜잭션 기준 테이블을 읽는 전용 query와 캐시를 사용하되 개인 필드가 포함된 내부 DTO를 재사용하지 않습니다.

## 10. 보관·삭제·파티셔닝

| 데이터 | 기본 보관 | 처리 |
| --- | --- | --- |
| 학생 세션·토큰 | 만료 후 30일 이내 | 토큰 해시 삭제 |
| 학생 앱 조회 | READ_ONLY 전환 후 30일 | 학생 접근 종료 |
| 학급 운영·거래·금융·학습 기록 | READ_ONLY 후 1년 | 식별 연결 제거 후 비식별 집계 가능 |
| 감사 로그 | 1년 | 보관기한 기준 삭제 |
| AI 입력·출력 메타데이터 | 1년 | 원문·민감정보 최소화 |
| 내보내기 파일 | 생성 시 설정한 단기 기한 | S3 object와 DB 위치 제거 |
| 백업 | 삭제 후 최대 30일 | 운영 복구 정책에 따름 |

초기에는 파티셔닝하지 않습니다. `audit_events`, `outbox_events`, `ledger_entries`가 학급당 예상량을 초과하면 월별 `occurred_at` 또는 `created_at` range 파티셔닝을 ADR로 추가합니다.

## 11. 마이그레이션 구현 순서

| 단계 | Flyway 후보 | 테이블 |
| --- | --- | --- |
| P1 | `V1_1__identity_classroom.sql` | 학교·사용자·코스·학급·학생·설정 |
| P2 | `V1_2__ledger.sql` | 원장·정책 스냅샷·멱등성·상태 이력 |
| P3 | `V1_3__event_audit.sql` | stream·outbox·감사 |
| P4 | `V1_4__store_trade.sql` | 상점·캐릭터·재고·개인거래 |
| P5 | `V1_5__finance.sql` | 예금·적금·투자·대출·신용 |
| P6 | `V1_6__activity_quiz_vote.sql` | 지도 활동·퀴즈·투표 |
| P7 | `V1_7__ai_report.sql` | AI·비동기 작업·내보내기·리포트 |
| P8 | `V1_8__constraints_triggers.sql` | 복합 FK·부분 UQ·원장 지연 트리거·불변 트리거 |
| P9 | `V1_9__indexes.sql` | 조회 인덱스 |
| P10 | `V1_10__classroom_identity_and_public_quiz.sql` | 전역 학급 코드·화폐 단위명 불변성과 공개 GAME 1~3위 View |
| P11 | `V1_11__mvp_schema_alignment.sql` | 예상 학생 수·안정 역할 코드·자율시간 모듈 연결·학교 AI 설정 버전 |
| P12 | `V1_12__seed_course_recommendations.sql` | 초4~6 × 1회·1주·1달·1학기 12개 추천 코스와 버전·모듈 제품 데이터 |
| P13 | `V1_13__activity_public_fields.sql` | 지도 활동별 교사 승인 공용 화면 필드 allowlist |
| P14 | `V1_14__ai_v1_2_execution.sql` | AI 실행 메타데이터·확인 결정·모니터 중복 방지 |
| P15 | `V1_15__quiz_result_workflow.sql` | 학생별 중복 신고·세션별 동시 재채점 방지 부분 UQ |
| P16 | `V1_16__student_character_profiles_and_equipment.sql` | 단일 체형 프로필, 슬롯별 장착, 54개 코드형 도트 상품과 학급 상점 초기 데이터 |
| P17 | `V1_17__basic_character_catalog.sql` | 기본 테마 코드형 도트 파츠 10개 추가 및 기존 학급 상점 보강 |

하나의 제품 초기 버전이므로 Flyway version은 `V1_1`~`V1_17`으로 분리합니다. 이미 공유 환경에 적용한 파일은 수정하지 않고 새 migration을 추가합니다.

## 12. Testcontainers 인수 기준

| ID | 테스트 |
| --- | --- |
| DBT-001 | 다른 `classroom_id`의 학생·거래·상품을 복합 FK로 참조하면 실패 |
| DBT-002 | 학급에 활성 OWNER 교사를 두 명 삽입하면 실패 |
| DBT-003 | 활성 학생의 출석번호·닉네임 중복이 실패 |
| DBT-004 | 같은 학생의 활성 세션 두 개가 실패 |
| DBT-005 | 학생 지갑 음수 잔액 커밋이 실패 |
| DBT-006 | 불균형 원장 거래의 `COMMITTED` 커밋이 지연 트리거에서 실패 |
| DBT-007 | 원장 전후 잔액 산식과 계정 sequence 불일치가 실패 |
| DBT-008 | 정책 스냅샷의 계산값 수정이 실패 |
| DBT-009 | 복구 거래가 원거래와 root를 보존하고 원거래를 바꾸지 않음 |
| DBT-010 | 같은 학급·실행자·경로·멱등성 키를 두 번 저장하면 중복 제약에서 실패 |
| DBT-011 | 같은 아이템을 두 활성 거래에 잠그면 한쪽이 실패 |
| DBT-012 | 상점 재고 수량을 음수로 삽입하면 실패 |
| DBT-013 | 게시된 상점 상품 버전의 단가를 수정하면 실패 |
| DBT-014 | 동일 투표 자격 토큰으로 두 번 투표하면 실패 |
| DBT-015 | outbox payload는 바뀌지 않고 발행 메타데이터만 갱신 가능 |
| DBT-016 | 공개 퀴즈 View는 교사가 공개한 GAME 1~3위의 닉네임·장착 캐릭터만 내보내고 점수·정답 수·응답 시간·TEST 결과를 제외 |
| DBT-017 | `classroom_accepts_new_operations()`가 READ_ONLY 학급에 대해 `false`를 반환 |
| DBT-018 | 학급 코드가 전역에서 유일하고 형식이 유효하며, 화폐 단위명 형식과 생성 후 불변성이 강제됨 |
| DBT-019 | 예상 학생 수, 역할 코드, 자율시간-모듈 학급 범위, 학교 AI 현재 설정 제약이 강제됨 |
| DBT-020 | 격리된 PostgreSQL에서 전체 Flyway 스키마를 정리하고 재생성할 수 있음 |
| DBT-021 | 학생별 프로필·프로필별 슬롯·아이템별 장착 유일성과 10개 장착 슬롯 허용값이 강제됨 |
| DBT-022 | 장착 행은 같은 학생의 `AVAILABLE` 아이템만 참조하고, 장착 아이템을 해제하지 않은 거래 잠금은 실패 |

## 13. 애플리케이션 책임

DB가 강제하지 않고 Spring 서비스가 판정하는 규칙:

- 사용자 역할·담당 학급·모듈 상태·앱 모드
- 상태 머신의 허용 명령과 상태 전이
- 학년별 추천 모듈과 선수 모듈 영향
- AI 제안 범위·안전검사·교사 승인
- 대출 심사와 신용 정책 계산
- 가격·세율·금리·반올림 계산
- 학급 코드 CSPRNG 생성·충돌 재시도와 화폐 단위명 NFKC 정규화·서비스 금칙어 검사
- 공개 DTO 필드 허용 목록
- 멱등성 키 재사용 시 `request_hash` 비교
- 학급 OWNER가 정확히 1명인 상태로 학급 생성 트랜잭션 완료

DB는 결과 무결성과 불변성을 보장하고, 서비스는 명령의 의미·권한·현재 상태를 판정합니다.

## 14. 구현 결과와 후속 작업

구현 결과:

- 논리 ERD 87개 엔티티가 87개 물리 테이블에 1:1 매핑
- 전체 컬럼·자료형·NULL·기본값 규칙 명시
- PK·학급 복합 FK·UQ·부분 UQ 정의
- 원장 균형·불변성·복구·정책 스냅샷 규칙 정의
- Flyway `V1_1`~`V1_17`에 조회 인덱스 40개, FK 보조 인덱스 14개, 학급 식별·공개 순위·MVP 필드·추천 코스·활동 공개 필드·AI v1.2 실행 메타데이터·퀴즈 신고/재채점 동시성 제약·캐릭터 슬롯 장착·64개 코드형 도트 상품 구현
- Testcontainers에 DBT-001~022와 87개 테이블 생성 검사 구현
- Java 21·Spring Boot 4.1·Testcontainers 2.0.5 기준 테스트 컴파일 성공
- PostgreSQL 17.10의 빈 격리 데이터베이스에 Flyway `V1_1`~`V1_14` 14개 직접 적용, 85개 물리 테이블과 Hibernate 스키마 검증 성공
- 격리 데이터베이스 삭제·재생성 후 `V1_1`~`V1_14` 전체 재적용 성공
- 안전장치가 있는 로컬 외부 DB 경로로 테이블 생성 검사와 DBT-001~020 총 21개 실행, 건너뜀 0건으로 성공
- `ai_runs` 분석 구간과 `ai_proposals.dedupe_key`를 `ON_DEMAND`·`AUTO_MONITOR` 실제
  집계 서비스에 연결하고, 확인된 알림도 다음 7일 구간에서 다시 발생하면 새 제안으로
  저장하도록 구현
- Testcontainers 21개 전체 실행은 Docker 사용 가능 Jenkins의 결과를 최종 PASS 기준으로 사용

`V1_14__ai_v1_2_execution.sql`은 기존 마이그레이션을 소급 수정하지 않고 `ai_runs`의
트리거·분석 구간, `ai_proposals`의 출처·심각도·지표·방향·중복키,
`ai_decisions`의 확인 후 승인 가능 제약, `economic_event_drafts`의 출처 제안 연결을
추가합니다. `ACKNOWLEDGED`는 최종 거절이 아니므로 같은 제안에 확인 결정 1개와 최종
승인·거절 결정 1개를 각각 허용합니다.

`V1_15__quiz_result_workflow.sql`은 학생별 같은 세션 문항 중복 신고와 같은 세션의
활성 재채점 중복 실행을 부분 유일 인덱스로 차단합니다.

`V1_16__student_character_profiles_and_equipment.sql`은 한 학생의 단일 캐릭터
프로필과 슬롯별 장착 상태를 분리합니다. 장착된 아이템은 계속 학생 소유여야 하며
배경을 포함한 서로 다른 슬롯은 동시에 유지됩니다.

`V1_17__basic_character_catalog.sql`은 빨간 캡·비니·선글라스·헤드폰·파란
티셔츠·노란 후드·초록 조끼·멜빵바지·운동화·배낭을 코드형 레이어 상품으로
추가하고, 이미 생성된 학급 상점에도 누락 없이 보강합니다.

후속 작업:

1. Docker 사용 가능 Jenkins 에이전트에서 DBT 전체 실행
2. [API·스키마 필드 매핑](../contracts/api-field-mapping.json)을 구현 코드의 DTO·영속 계층과 동기화
3. 서비스 계층에서 OWNER 0명 방지, 같은 멱등 키의 요청 hash 비교·기존 응답 반환, READ_ONLY 명령 차단 인수 테스트
4. 금융·퀴즈 버전 불변성과 투자·대출 음수 값 거부를 포함한 서비스·DB 통합 테스트 보강
5. 실제 쿼리 기반 `EXPLAIN ANALYZE` 후 인덱스 조정
