# 데이터베이스 마이그레이션·무결성 테스트

이 디렉터리는 `docs/PHYSICAL_SCHEMA.md`를 실행 가능한 PostgreSQL 17 스키마로 옮긴 결과다.

## 구성

- `src/main/resources/db/migration/V1_1~V1_11`: Flyway 마이그레이션
- `src/test/java`: PostgreSQL 17 Testcontainers 무결성 테스트
- `build.gradle`: Java 21, Spring Boot 4.1.0 의존성 관리 기준

## 실행 조건

- Java 21
- Gradle 8.14 이상 또는 9.x
- Docker Engine

```bash
./gradlew test
```

Windows PowerShell에서는 `.\gradlew.bat test`를 사용한다. Docker가 없는 환경에서는
Testcontainers 테스트가 건너뛰어진다. CI에서는 Docker 사용 가능 에이전트에서 반드시
전체 테스트를 실행한다.

루트에서 DDL·문서·테스트 식별자 매핑을 정적으로 검사할 수 있다.

```bash
node scripts/validate-database-implementation.mjs
node scripts/validate-api-schema-consistency.mjs
```

운영 DB에 적용한 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다.
