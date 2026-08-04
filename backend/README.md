# 이코빌리지 핵심 API

Java 21과 Spring Boot 4.1 기반 핵심 API 프로젝트입니다. PostgreSQL 17 Flyway
마이그레이션, DB 무결성 테스트와 교사·학생 계정 영속성 모델을 포함합니다.

## 구성

```text
src/main/java/com/b201/ecovillage/
  EcoVillageApplication.java
  identity/
    domain/       교사 사용자·학교 소속·학급 교사·학생·학생 자격증명
    repository/   Spring Data JPA 저장소
src/main/resources/
  application.yml
  db/migration/   Flyway V1_1~V1_13
src/test/java/
  identity/       계정 도메인 단위 테스트
  SchemaIntegrityTest.java   PostgreSQL 17 Testcontainers 무결성 테스트
```

`build.gradle`은 Java 21과 Spring Boot 4.1.0 의존성 관리 기준입니다.
Spring Boot 4의 Flyway 자동 구성을 위해 `spring-boot-starter-flyway`를 사용하고,
PostgreSQL 지원은 `flyway-database-postgresql`로 추가합니다.

## 실행 조건

- Java 21
- Gradle 8.14 이상 또는 9.x
- Docker Engine과 Docker Compose

저장소 루트에서 개발 환경 파일을 준비하고 PostgreSQL 17과 Redis를 실행합니다.

```powershell
Copy-Item .env.example .env
docker compose up -d
```

백엔드 실행:

```powershell
cd backend
.\gradlew.bat bootRun
```

기본 포트는 `8080`이며 상태 확인 주소는
`http://localhost:8080/actuator/health`입니다. `application.yml`은 백엔드
디렉터리와 저장소 루트의 `.env`를 선택적으로 읽습니다.

백엔드까지 컨테이너로 함께 실행할 때는 저장소 루트에서 다음 명령을 사용합니다.

```powershell
docker compose --profile app up -d --build
```

## 테스트와 빌드

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat bootJar
```

Docker가 없으면 Testcontainers 기반 21개 DB 테스트는 건너뛰지만 계정 도메인
단위 테스트와 Java 컴파일은 실행됩니다. Jenkins에서는 DB 테스트가 하나도
건너뛰지 않고 통과해야 합니다.

2026-07-30에는 로컬 PostgreSQL 17.10의 빈 격리 데이터베이스에서 애플리케이션을
기동해 `V1_1`~`V1_13` 13개를 적용하고, 85개 물리 테이블과 Hibernate 스키마
검증을 확인했습니다. 데이터베이스를 삭제·재생성한 뒤 같은 절차를 반복해 전체
재적용도 확인했습니다. 이 직접 적용 검증은 Docker 사용 Jenkins의 Testcontainers
21개 무결성 테스트를 대체하지 않습니다.

Docker를 사용할 수 없는 개발 PC에서는 로컬 전용
`ecovillage_migration_test` 데이터베이스에 한해 같은 DB 테스트를 실행할 수
있습니다. 이 테스트는 실행 중 Flyway `clean`을 호출하므로 다른 데이터베이스
이름과 원격 호스트는 코드에서 거절합니다.

```powershell
$env:TEST_POSTGRES_JDBC_URL = "jdbc:postgresql://localhost:55432/ecovillage_migration_test"
$env:TEST_POSTGRES_USERNAME = "postgres"
$env:TEST_POSTGRES_PASSWORD = "<local-development-password>"
$env:ALLOW_EXTERNAL_DB_CLEAN = "true"
.\gradlew.bat test --tests "com.b201.ecovillage.SchemaIntegrityTest" --rerun-tasks
```

루트에서 DDL·문서·테스트 식별자 매핑을 정적으로 검사할 수 있습니다.

```bash
node scripts/validate-database-implementation.mjs
node scripts/validate-api-schema-consistency.mjs
```

## 학생 로그인 API

`POST /auth/student-sessions`는 OpenAPI `API-AUTH-001` 계약을 구현합니다.

- 학급 코드·출석번호·닉네임·비밀번호·기기 UUID를 검증합니다.
- 비밀번호는 Argon2id 해시와 비교하고 로그인 실패로 계정을 잠그지 않습니다.
- 액세스 토큰은 15분 JWT, 갱신 토큰은 14일 만료이며 갱신 토큰 해시만 저장합니다.
- 새 기기 로그인은 기존 ACTIVE 학생 세션을 먼저 폐기합니다.
- 기기 UUID는 HMAC-SHA-256 지문으로만 저장합니다.
- 단일 인스턴스 순간 요청 제한을 적용하며 배포 단계에서는 Redis 기반 제한기로
  교체할 수 있도록 인터페이스를 분리했습니다.

로컬 기본 키는 개발 전용입니다. 배포 환경에서는 `.env.example`의
`AUTH_JWT_SECRET`, `AUTH_TOKEN_PEPPER`를 Secrets Manager 등에서 주입해야 합니다.

## 교사 OIDC 로그인 API

`POST /auth/teacher-sessions`는 OpenAPI `API-AUTH-008` 계약을 구현합니다.

- 교사 웹은 외부 OIDC에서 Authorization Code + PKCE를 완료하고 `id_token`을 전달합니다.
- 백엔드는 ID 토큰의 서명, issuer, client ID audience, 만료를 검증합니다.
- 검증된 `sub`가 활성 `users.external_subject` 교사 계정과 일치해야 로그인됩니다.
- 액세스 토큰은 15분 JWT, 갱신 토큰은 Redis에 HMAC 해시만 14일 보관합니다.
- 교사의 복수 브라우저 로그인을 허용하며 각 세션은 독립적으로 갱신·로그아웃됩니다.
- `POST /auth/token-refreshes`는 갱신 토큰을 회전하고 이전 토큰 재사용을 거절합니다.
- `DELETE /auth/sessions/current`, `GET /auth/me`로 현재 세션 폐기와 역할·담당 학급 조회를 지원합니다.
- 교사 웹 갱신 토큰은 JSON 본문에 노출하지 않고 `HttpOnly`, `SameSite=Strict`
  쿠키로만 발급하며 운영에서는
  `AUTH_REFRESH_COOKIE_SECURE=true`로 설정합니다.
- 보호 API는 학생 앱과 교사 웹 모두 `Authorization: Bearer {access_token}`을
  사용합니다. 교사 웹은 액세스 토큰을 메모리에만 유지합니다.
- 로컬 교사 웹 CORS 원본은 `WEB_ALLOWED_ORIGINS`에 명시하며 `*`는 허용하지 않습니다.

로컬에서는 `TEACHER_OIDC_ENABLED=false`가 기본이므로 교사 로그인 요청은
`AUTH_PROVIDER_UNAVAILABLE`을 반환합니다. 실제 제공자의 issuer, JWK Set URI,
client ID를 `.env` 또는 배포 비밀로 설정한 뒤 활성화합니다. 이코빌리지 서버는
외부 계정 비밀번호를 받거나 저장하지 않습니다.

## 로컬 개발 데이터베이스 재생성

아래 절차는 저장소의 로컬 Compose PostgreSQL 데이터만 삭제합니다. 공유·운영
데이터베이스에는 사용하지 않습니다.

```powershell
$projectName = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { "ecovillage" }
docker compose down
docker volume rm "$projectName`_postgres-data"
docker compose up -d postgres
cd backend
.\gradlew.bat test
```

마지막 테스트는 빈 PostgreSQL 17에 `V1_1`부터 최신 버전까지 적용하고,
Testcontainers 내부에서 `clean → 전체 재적용`이 가능한지도 검증합니다. Flyway
`clean`은 격리된 테스트 컨테이너에서만 허용합니다. 이미 적용한 마이그레이션을
수정하거나 운영 데이터베이스를 되돌리는 방식으로 사용하지 않습니다.

## 계정 모델 원칙

- 교사·관리자 계정은 `users`, 학교 역할은 `school_memberships`에 저장합니다.
- 학생은 실명 계정을 만들지 않고 학급별 출석번호와 닉네임을
  `classroom_students`에 저장합니다.
- 비밀번호 원문은 저장하지 않고 해시만 `student_credentials`에 분리합니다.
- 활성 학생의 출석번호와 대소문자를 무시한 닉네임은 학급 안에서 유일합니다.
- 학생 탈퇴·종료는 행을 삭제하지 않고 상태와 종료 시각을 기록합니다.
- 적용한 Flyway 파일은 수정하지 않고 새 버전을 추가합니다.

## Realtime WebSocket

Authenticated teacher and student clients issue a 60-second, single-use ticket with
`POST /auth/realtime-tickets`, then connect to `GET /ws/v1?ticket={ticket}`. The
server supports channel subscriptions, sequence replay, recovery-required responses,
event acknowledgement, heartbeat messages, and automatic connection expiry.

Local `bootRun` uses the in-process event bus by default. Docker Compose sets
`REALTIME_REDIS_ENABLED=true`, so multiple backend instances exchange committed
outbox events through Redis Pub/Sub. Configure browser origins with
`WEB_ALLOWED_ORIGINS`; the local default includes ports `3000` and `5173`.
