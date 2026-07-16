# 이코빌리지

교사가 수업 단계를 제어하고 학생이 교실 화폐 `비빔`으로 경제활동을 체험하는 웹 서비스입니다.

## 기술 스택

- Frontend: Vue 3, TypeScript, Vite
- Backend: Java 21, Spring Boot 4.1
- Database: PostgreSQL 17
- Local infrastructure: Docker Compose

## 실행 방법

### 실행 전 확인

개별로 백엔드를 실행할 때는 JDK 21이 필요합니다. `JAVA_HOME`은 `bin` 폴더가 아닌 JDK 설치 폴더를 가리켜야 합니다.

```powershell
java -version
$env:JAVA_HOME
Test-Path "$env:JAVA_HOME\bin\java.exe"
```

### 1. 전체 로컬 실행

```powershell
Copy-Item .env.example .env
docker compose -f infra/docker-compose.local.yml up -d --build
```

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/api/v1/health
- Actuator health: http://localhost:8080/actuator/health

### 로컬 데모 인증

- 교사 로그인: `teacher` / `teacher1234`
- 학생 참여 코드: `ECO-01`, `ECO-02`, `ECO-03`
- 교사 페이지: http://localhost:5173/teacher/login
- 학생 페이지: http://localhost:5173/student/join

교사 자격 증명은 `.env`의 `DEMO_TEACHER_*` 값으로 변경할 수 있습니다. 로컬 프로필에서만 데모 교사·방·참여 코드가 생성됩니다.

종료:

```powershell
docker compose -f infra/docker-compose.local.yml down
```

Docker Compose 프로젝트명은 `ecovillage-local`입니다. DB 데이터까지 초기화하려면 명시적으로 다음 명령을 사용합니다.

```powershell
docker compose -f infra/docker-compose.local.yml down -v
```

### 2. 개별 실행

PostgreSQL만 실행:

```powershell
docker compose -f infra/docker-compose.local.yml up -d postgres
```

백엔드:

```powershell
cd backend

현재 .env.example의 비밀번호가 change-me이므로 환경변수를 맞춰줘야 함

$env:DB_URL="jdbc:postgresql://localhost:5432/ecovillage"
$env:DB_USERNAME="ecovillage"
$env:DB_PASSWORD="change-me"

.\gradlew.bat bootRun
```

프론트엔드:

```powershell
cd frontend
corepack enable (: pnpm 설치)
pnpm install
pnpm dev
```

## 문서

- [오늘의 개발 계획](docs/plans/2026-07-16-demo-flow-development-plan.md)
- [개발 환경 시작 안내](docs/GETTING_STARTED.md)
- [Git 작업 규칙](docs/GIT_CONVENTION.md)
