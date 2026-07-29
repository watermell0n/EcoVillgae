# Jenkins

루트 `Jenkinsfile`이 공통 계약·DB·Terraform·서비스 검사를 수행합니다.

에이전트 요구사항:

- Java 21
- Node.js
- Terraform
- Docker Engine
- Python
- Jenkins JUnit 플러그인

일반 브랜치 검증에서는 `REQUIRE_DB_TESTS=false`를 사용할 수 있습니다.
Jenkins Docker 에이전트 검증과 배포 후보에서는 `REQUIRE_DB_TESTS=true`로 실행해
PostgreSQL Testcontainers 19개(DBT-001~018과 83개 테이블 생성 검사)가 하나라도
skip되면 실패하도록 합니다.

학생 앱·교사 웹·AI 서비스 단계는 각 프로젝트 파일이 생성된 뒤 자동으로
활성화됩니다. 배포 단계는 AWS 환경과 승인 방식이 확정될 때 추가합니다.
