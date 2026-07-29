# 이코빌리지 Git 작업 안내

- 기준일: 2026-07-29
- 저장소: `https://lab.ssafy.com/s15-webmobile4-sub1/S15P11B201.git`
- 공통 기준 브랜치: `master`

상세 일정과 경로별 담당은 [팀 일정·협업 방식](TEAM_SCHEDULE_AND_WORKFLOW.md)을
따릅니다.

## 1. 브랜치 역할

| 브랜치 | 역할 |
| --- | --- |
| `master` | 신규 MVP의 공통 구조와 검토가 끝난 변경 |
| `codex/backend-dev` | 허병우 개인 백엔드 개발 |
| `feature/*` | 팀원별 기능·설계 작업 |
| `fix/*` | 버그 수정 |
| `docs/*` | 독립 문서 변경 |
| `release` | 이전 시연 코드 보존, 신규 MVP 작업 기준이 아님 |
| `develop` | 이전 기준선, 신규 작업 기준이 아님 |

팀원은 `release`와 `develop`에서 신규 MVP 브랜치를 만들지 않습니다. 최신
`master`에서 각자 브랜치를 만들고 Merge Request도 `master`를 대상으로 합니다.

## 2. 작업 시작

```bash
git fetch origin --prune
git switch master
git pull --ff-only origin master
git switch -c feature/S15P11B201-123-classroom-students
```

브랜치 이름은 `<type>/<Jira번호>-<kebab-case-description>`을 권장합니다.

허병우의 백엔드 작업:

```bash
git fetch origin --prune
git switch codex/backend-dev
git pull --ff-only origin codex/backend-dev
```

팀원은 `codex/backend-dev`에서 분기하거나 직접 커밋하지 않습니다.

## 3. 경로별 기본 소유

| 경로 | 담당 |
| --- | --- |
| `backend/` | 허병우 |
| `ai-service/`, `docs/ai/` | 박수경 |
| `student-app/`, `teacher-web/`, `docs/ux/` | 신현경 |
| `assets/characters/`, `docs/design/` | 최상진 |
| `contracts/`, `infra/`, 루트 공통 설정 | 허병우 통합 |

담당은 다른 사람의 변경을 금지한다는 뜻이 아니라 동시 수정을 막기 위한 기본
책임자입니다. 담당 밖 파일을 수정할 때는 Jira와 MR에 이유를 남깁니다.

## 4. 커밋

한 커밋에는 하나의 의미 있는 변경만 포함합니다.

```text
<type>(optional scope): <subject>
```

주요 타입:

- `feat`: 기능
- `fix`: 버그
- `docs`: 문서
- `test`: 테스트
- `design`: 디자인
- `chore`: 환경·빌드·도구

예시:

```text
feat(classroom): 경제교실 학생 등록 추가
feat(ai): 경제 밸런싱 응답 스키마 추가
design(character): 프로필 슬롯 가이드 추가
chore: 로컬 PostgreSQL과 Redis 구성 추가
```

## 5. 공통 파일

다음 파일은 충돌 가능성이 높으므로 한 번에 한 사람만 수정합니다.

- 루트 환경·빌드 설정과 lock 파일
- `contracts/openapi.yaml`, `contracts/asyncapi.yaml`
- `backend/src/main/resources/db/migration/`
- Jenkins·Terraform
- 캐릭터 편집 원본

API 변경은 계약을 먼저 MR로 합의하고 각 구현 브랜치가 병합된 `master`를 다시
반영합니다. 적용된 Flyway 파일은 수정하지 않고 새 버전을 추가합니다.

## 6. 커밋 전 확인

```bash
git status --short
git diff --check
git diff
```

백엔드:

```powershell
cd backend
.\gradlew.bat test
```

문서·계약·DB 정적 검사:

```bash
node scripts/validate-contract-coverage.mjs
node scripts/validate-document-consistency.mjs
node scripts/validate-data-model.mjs
node scripts/validate-physical-schema.mjs
node scripts/validate-database-implementation.mjs
```

각 앱과 AI 서비스가 초기화된 뒤에는 해당 디렉터리 README에 실행 명령을
추가합니다.

## 7. Push와 Merge Request

```bash
git push -u origin <branch-name>
```

MR에는 다음 내용을 포함합니다.

- 관련 Jira
- 무엇을 왜 바꿨는지
- 영향받는 화면·API·DB·이벤트
- 실행한 테스트와 결과
- 리뷰어가 확인할 사항

기본 템플릿은 `.gitlab/merge_request_templates/Default.md`를 사용합니다.

## 8. 최신 master 반영

작업 중 하루에 한 번 이상 최신 기준을 반영합니다.

```bash
git fetch origin
git rebase origin/master
```

공유된 개인 브랜치에서 이미 다른 사람이 작업을 이어받았다면 rebase 전에
팀과 확인하고 merge 방식을 사용합니다.

## 9. 금지·주의

- 다른 사람의 변경을 확인 없이 삭제하지 않습니다.
- 공유 브랜치에 `git push --force`를 사용하지 않습니다.
- `.env`, 비밀번호, 토큰, 인증서와 Terraform 상태를 커밋하지 않습니다.
- `git reset --hard`, `git clean -fd`는 영향 범위를 모르면 실행하지 않습니다.
- 기능 구현을 `master`에 직접 푸시하지 않습니다.

비밀정보를 커밋했다면 파일 삭제만 하지 말고 즉시 키를 폐기·재발급하고 팀에
알립니다.
