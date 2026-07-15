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
- [API 명세](docs/specifications/api%20명세서.txt)
- [ERD](docs/specifications/erd.txt)
- [기술 스택 및 배포](docs/specifications/기술스택%20및%20배포%20주의사항.txt)

