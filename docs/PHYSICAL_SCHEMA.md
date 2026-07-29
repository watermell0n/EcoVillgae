# 이코빌리지 PostgreSQL 물리 스키마

- 버전: v0.1-draft
- 기준일: 2026-07-29
- 입력: [논리 데이터 모델 v0.1](DATA_MODEL.md)
- 상태: 논리 ERD 완료 후 물리 설계 진행 예정
- 대상: PostgreSQL 17, Flyway migration

## 1. 이 문서의 현재 범위

이 문서는 논리 ERD를 실제 테이블로 변환할 때 사용할 공통 규칙과 작업 순서를 먼저 고정합니다. 테이블별 전체 컬럼·인덱스·DDL은 논리 ERD 검토가 끝난 뒤 이 문서에 추가합니다.

## 2. 공통 자료형

| 논리 값 | PostgreSQL 자료형 | 규칙 |
| --- | --- | --- |
| 식별자 | `uuid` | 애플리케이션에서 생성, 외부 노출 가능 |
| 금액 | `bigint` | 최소 화폐 단위 정수, 학생 지갑 음수 금지 |
| 세율·금리·비율 | `integer` | basis point, 허용 범위 `CHECK` 적용 |
| 수량·순번 | `integer` 또는 `bigint` | 음수 금지, 원장 sequence는 `bigint` |
| 상태 | `varchar(40)` | 애플리케이션 enum과 DB `CHECK`를 함께 관리 |
| 서버 시각 | `timestamptz` | UTC 저장 |
| 운영 날짜 | `date` | 실제 학급 시작일·종료일 |
| 유연한 정책·스냅샷 | `jsonb` | `schema_version`과 해시를 함께 저장 |
| 비밀번호 | `text` | 단방향 해시만 저장 |
| 이벤트·요청 해시 | `varchar(128)` | 원문 비밀값을 저장하지 않음 |

## 3. 공통 컬럼

상태 보유 테이블:

```text
id uuid primary key
classroom_id uuid not null
status varchar(40) not null
state_version bigint not null default 0
status_changed_at timestamptz not null
created_at timestamptz not null
updated_at timestamptz not null
```

불변 이력 테이블:

```text
id uuid primary key
classroom_id uuid not null
created_at timestamptz not null
```

불변 이력 테이블에는 일반적인 `updated_at`을 두지 않습니다.

## 4. 테이블 구현 순서

| 단계 | 테이블 묶음 | 선행 조건 |
| --- | --- | --- |
| P1 | 학교·사용자·학급·교사·학생·모듈·정책 | 없음 |
| P2 | 원장 계정·거래 그룹·원장 항목·멱등성·상태 이력 | P1 |
| P3 | outbox·실시간 stream·감사 | P1·P2 |
| P4 | 상점·재고·캐릭터·아이템·개인거래 | P1·P2 |
| P5 | 예금·적금·투자·대출·신용 | P1·P2 |
| P6 | 활동·퀴즈·투표 | P1·P2 |
| P7 | AI 실행·제안·비동기 작업·리포트 | P1·P3 |

## 5. 반드시 DB에서 강제할 제약

- 동일 학급 범위 복합 외래키
- 학급별 소유 교사 1명
- 학급별 출석번호·닉네임 유일성
- 학생별 활성 세션 1개
- 학생별 지갑 1개, 학급별 공동기금 계정 1개
- 원장 계정 sequence 유일성과 전후 잔액 산식
- 커밋 거래 원장 합계 0
- 커밋된 거래·원장·정책 스냅샷의 수정·삭제 금지
- 멱등성 키와 request hash 재사용 규칙
- 거래 중 아이템 잠금의 단일 소유
- 게시 버전과 실행 스냅샷의 불변 참조
- 투자 포지션 수량 음수 금지
- 투표별 자격 토큰 1회 사용
- outbox stream sequence와 event ID 유일성

## 6. 애플리케이션에서 검증할 규칙

- 현재 역할·담당 학급·앱 모드·모듈 상태
- 상태 머신별 허용 명령
- 학년별 모듈 추천과 선수 모듈 영향
- AI 제안 허용 범위와 교사 승인
- 공개 화면 DTO의 개인정보 제외
- 거래·금융·퀴즈의 복합 업무 조건

DB는 결과 무결성과 불변성을 보장하고, Spring 서비스는 업무 명령의 의미와 권한을 판정합니다.

## 7. 후속 산출물

1. 테이블·컬럼·NULL·기본값 데이터 사전
2. 인덱스·외래키·부분 유일 인덱스 명세
3. 원장 불변성 지연 제약 트리거
4. Flyway `V1__initial_schema.sql`
5. Testcontainers 무결성 테스트
6. OpenAPI 필드와 저장·projection 필드 매핑표
