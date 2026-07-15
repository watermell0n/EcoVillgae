# 비빔 교실 경제

교사가 수업 단계를 제어하고 학생이 비빔으로 경제활동을 체험하는 웹 서비스입니다.

## 기술 스택

- Frontend: Vue 3, TypeScript, Vite
- Backend: Java 21, Spring Boot 4.1
- Database: PostgreSQL 17
- Local infrastructure: Docker Compose

## 전체 로컬 실행

```powershell
Copy-Item .env.example .env
docker compose -f infra/docker-compose.local.yml up --build
```

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/api/v1/health
- Actuator health: http://localhost:8080/actuator/health

### 로컬 데모 인증

- 교사 로그인: `teacher` / `teacher1234`
- 학생 참여 코드: `BIBIM-01`, `BIBIM-02`, `BIBIM-03`
- 교사 화면: http://localhost:5173/teacher/login
- 학생 화면: http://localhost:5173/student/join

교사 자격 증명은 `.env`의 `DEMO_TEACHER_*` 값으로 변경할 수 있습니다. 로컬 프로필에서만 데모 교사·방·참여 코드가 생성됩니다.

종료:

```powershell
docker compose -f infra/docker-compose.local.yml down
```

DB 데이터까지 초기화하려면 명시적으로 다음 명령을 사용합니다.

```powershell
docker compose -f infra/docker-compose.local.yml down -v
```

## 개별 실행

PostgreSQL만 실행:

```powershell
docker compose -f infra/docker-compose.local.yml up postgres
```

백엔드:

```powershell
cd backend
.\gradlew.bat bootRun
```

프론트엔드:

```powershell
cd frontend
pnpm install
pnpm dev
```

## 문서

- [오늘의 개발 계획](docs/plans/2026-07-16-demo-flow-development-plan.md)
- [개발 환경 시작 안내](docs/GETTING_STARTED.md)
- [Git 작업 규칙](docs/GIT_CONVENTION.md)
