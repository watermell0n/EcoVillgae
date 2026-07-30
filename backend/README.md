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
  db/migration/   Flyway V1_1~V1_11
src/test/java/
  identity/       계정 도메인 단위 테스트
  SchemaIntegrityTest.java   PostgreSQL 17 Testcontainers 무결성 테스트
```

`build.gradle`은 Java 21과 Spring Boot 4.1.0 의존성 관리 기준입니다.

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

루트에서 DDL·문서·테스트 식별자 매핑을 정적으로 검사할 수 있습니다.

```bash
node scripts/validate-database-implementation.mjs
node scripts/validate-api-schema-consistency.mjs
```

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
