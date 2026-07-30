# 이코빌리지 Git 작업 안내

- 기준일: 2026-07-30
- 저장소: `https://lab.ssafy.com/s15-webmobile4-sub1/S15P11B201.git`
- 개발 기준 브랜치: `develop`

상세 일정과 경로별 담당은 [팀 일정·협업 방식](TEAM_SCHEDULE_AND_WORKFLOW.md)을
따릅니다.

## 1. 브랜치 역할

| 브랜치 | 역할 |
| --- | --- |
| `master` | 배포 가능한 릴리스 이력. 기능 개발의 시작점이나 일반 MR 대상이 아님 |
| `develop` | 다음 릴리스를 위한 개발 통합 기준선이자 일반 MR 대상 |
| `feat/*` | `develop`에서 분기하는 기능·설계·문서 작업 |
| `fix/*` | 버그 수정 |
| `docs/*` | 독립 문서 변경 |
| `hotfix/*` | 운영 중 긴급 수정을 위해 `master`에서 분기하는 브랜치 |
| `release` | 이전 시연 코드 보존용 레거시 브랜치. 신규 작업 기준이 아님 |

일반 작업은 최신 `develop`에서 분기하고 Merge Request도 `develop`을 대상으로
합니다. `master`에는 릴리스와 긴급 수정만 반영합니다.

## 2. 작업 시작

```bash
git fetch origin --prune
git switch develop
git pull --ff-only origin develop
git switch -c feat/S15P11B201-123-classroom-students
```

브랜치 이름은 `<type>/<Jira번호>-<kebab-case-description>`을 권장합니다.
기존 개인 통합 브랜치는 진행 중인 변경을 정리해 `develop` 대상 MR로 병합한 뒤
신규 작업 기준으로 사용하지 않습니다.

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

API 변경은 계약을 먼저 MR로 합의하고 계약 변경이 병합된 `develop`을 각 구현
브랜치에 다시 반영합니다. 적용된 Flyway 파일은 수정하지 않고 새 버전을 추가합니다.

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

일반 기능·수정·문서 MR의 대상 브랜치는 `develop`입니다. 기능 하나가 끝날 때마다
`master`로 MR을 만들지 않습니다.

MR에는 다음 내용을 포함합니다.

- 관련 Jira
- 무엇을 왜 바꿨는지
- 영향받는 화면·API·DB·이벤트
- 실행한 테스트와 결과
- 리뷰어가 확인할 사항

기본 템플릿은 `.gitlab/merge_request_templates/Default.md`를 사용합니다.

## 8. 최신 develop 반영

작업 중 하루에 한 번 이상 최신 기준을 반영합니다.

```bash
git fetch origin
git rebase origin/develop
```

공유된 개인 브랜치에서 이미 다른 사람이 작업을 이어받았다면 rebase 전에
팀과 확인하고 merge 방식을 사용합니다.

## 9. develop 통합과 master 승격

팀 통합일에는 다음 순서로 진행합니다.

1. `master`에만 있는 최신 변경이 있으면 먼저 `master`에서 `develop`으로 MR을
   만들어 반영합니다.
2. 각 작업 브랜치는 최신 `origin/develop`을 반영하고, 발생한 충돌을 작업
   브랜치에서 해결한 뒤 `develop` 대상 MR로 병합합니다.
3. 한 브랜치를 병합할 때마다 정적 검사와 해당 영역 테스트를 실행합니다.
4. 모든 작업을 합친 `develop`에서 전체 CI, 단위·통합 테스트와 주요 사용자 흐름
   스모크 테스트를 실행합니다.
5. 충돌이 모두 해소되고 검증이 통과한 경우에만 `develop`에서 `master`로 MR을
   만들어 승격합니다.

현재 MVP 통합에서는 별도 `release/*` 브랜치를 강제하지 않습니다. 통합 중 발견한
문제는 `develop`에서 수정·재검증하며, 검증에 실패한 상태를 `master`로 옮기지
않습니다.

운영 중 긴급 수정은 `master`에서 `hotfix/*`를 분기해 검증 후 `master`로 MR을
만들고, 동일 변경을 `develop`에도 병합합니다.

## 10. 금지·주의

- 다른 사람의 변경을 확인 없이 삭제하지 않습니다.
- 공유 브랜치에 `git push --force`를 사용하지 않습니다.
- `.env`, 비밀번호, 토큰, 인증서와 Terraform 상태를 커밋하지 않습니다.
- `git reset --hard`, `git clean -fd`는 영향 범위를 모르면 실행하지 않습니다.
- `develop`과 `master`에 직접 푸시하지 않고 MR과 리뷰를 거칩니다.
- 일반 기능 브랜치에서 `master`로 바로 MR을 만들지 않습니다.

비밀정보를 커밋했다면 파일 삭제만 하지 말고 즉시 키를 폐기·재발급하고 팀에
알립니다.
