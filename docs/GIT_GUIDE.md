# 이코빌리지 Git 작업 안내

- 기준일: 2026-07-29
- 저장소: `https://lab.ssafy.com/s15-webmobile4-sub1/S15P11B201.git`
- GitLab 기본 브랜치: `master`

## 1. 문서 목적

이 문서는 이코빌리지 팀원이 브랜치를 잘못 선택하거나 다른 사람의 변경을 덮어쓰지 않고 작업하기 위한 안내서입니다.

기존 안내문서는 `main` 브랜치를 기준으로 작성된 범용 템플릿이었습니다. 현재 저장소는 `master`가 기본 브랜치이고 브랜치별 내용도 서로 다르므로 실제 상태를 기준으로 다시 작성했습니다.

## 2. 현재 원격 브랜치 상태

2026-07-29 기준 원격 브랜치는 다음과 같습니다.

| 브랜치 | 현재 역할과 상태 |
| --- | --- |
| `master` | GitLab 기본 브랜치, 최신 MVP 기획 문서 |
| `develop` | 초기 Git 안내 문서 상태에 머물러 있어 현재 구현과 동기화되지 않음 |
| `release` | 기존 이코빌리지 시연용 프론트엔드·백엔드 구현과 최근 수정사항 |

세 브랜치는 공통 조상에서 갈라졌지만 `develop`과 `release`의 진행 상태가 크게 다릅니다.

### 현재 가장 중요한 주의사항

- 새 기능을 무조건 `develop`에서 시작하지 않습니다.
- 기존 시연 코드의 수정은 팀이 별도로 정하기 전까지 `release`를 기준으로 합니다.
- 새 지속형 MVP 구현을 `release`에서 이어갈지, `develop`을 새 기준으로 만들지는 팀 결정이 필요합니다.
- `release`를 `develop`이나 `master`에 바로 병합하지 않습니다.
- 브랜치 정리 전에는 작업 목적과 기준 브랜치를 팀에 먼저 공유합니다.

## 3. 권장 브랜치 역할

브랜치 정리가 끝난 뒤에는 다음 역할로 통일하는 것을 권장합니다.

| 브랜치 | 역할 | 직접 푸시 |
| --- | --- | --- |
| `master` | 승인된 제품 기준선과 배포 가능한 안정 버전 | 원칙적으로 금지 |
| `develop` | 다음 버전을 위한 개발 통합 | 원칙적으로 금지 |
| `feature/*` | 기능 개발 | 가능 |
| `fix/*` | 일반 버그 수정 | 가능 |
| `docs/*` | 문서 변경 | 가능 |
| `release/*` | 배포 전 통합 테스트와 안정화 | 담당자만 |
| `hotfix/*` | 운영 중 긴급 수정 | 담당자만 |

`master`나 `develop`에 직접 푸시해야 하는 특별한 사유가 있다면 변경 범위와 이유를 팀에 공유하고 명시적인 동의를 받은 뒤 진행합니다.

## 4. 저장소 처음 받기

```bash
git clone https://lab.ssafy.com/s15-webmobile4-sub1/S15P11B201.git
cd S15P11B201
git fetch origin --prune
git branch -a
```

현재 기본 브랜치를 확인합니다.

```bash
git remote show origin
```

출력의 `HEAD branch`가 `master`인지 확인합니다.

## 5. 작업 시작 전 확인

항상 다음 세 가지를 먼저 확인합니다.

```bash
git status
git branch --show-current
git log --oneline --decorate -5
```

- 현재 브랜치가 작업 목적에 맞는가
- 커밋하지 않은 내 변경사항이 있는가
- 원격 최신 커밋을 기준으로 시작하는가

다른 사람이 만든 파일이나 수정 중인 파일은 임의로 삭제하거나 되돌리지 않습니다.

## 6. 작업 브랜치 만들기

### 6.1 기준 브랜치 최신화

팀이 정한 기준 브랜치로 이동합니다.

```bash
git switch <base-branch>
git pull --ff-only origin <base-branch>
```

예를 들어 브랜치 정리 후 새 기능을 `develop`에서 시작한다면 다음과 같습니다.

```bash
git switch develop
git pull --ff-only origin develop
```

현재 시연 코드의 수정이라면 기준 브랜치가 `release`인지 팀에 먼저 확인합니다.

### 6.2 기능 브랜치 생성

```bash
git switch -c feature/<short-description>
```

예시:

```text
feature/classroom-course
feature/teacher-ai-chat
feature/economy-balancing
feature/quiz-authoring
fix/private-trade-tax
docs/git-guide
```

### 6.3 브랜치 이름 규칙

```text
<type>/<kebab-case-description>
```

- 영문 소문자와 하이픈을 사용합니다.
- 작업 내용을 알 수 있는 짧은 이름을 사용합니다.
- Jira 번호를 사용한다면 앞에 붙일 수 있습니다.

```text
feature/S15P11B201-123-course-template
```

## 7. 파일 변경과 스테이징

변경사항을 먼저 확인합니다.

```bash
git status --short
git diff
```

가능하면 작업한 파일만 명시적으로 스테이징합니다.

```bash
git add frontend/src/pages/teacher/TeacherPage.vue
git add backend/src/main/java/com/b201/example/ExampleService.java
```

문서 작업 예시:

```bash
git add README.md docs/GIT_GUIDE.md
```

`git add .`은 관련 없는 변경과 비밀정보까지 포함할 수 있으므로 변경 범위를 모두 확인한 경우에만 사용합니다.

스테이징 결과를 다시 확인합니다.

```bash
git diff --cached --check
git diff --cached --name-status
git diff --cached
```

## 8. 커밋 규칙

한 커밋에는 하나의 의미 있는 변경만 포함합니다.

```text
<type>(optional scope): <subject>
```

### 커밋 타입

| 타입 | 용도 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 변경 |
| `refactor` | 기능 변화 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `style` | 포맷이나 스타일 변경 |
| `design` | UI 디자인 변경 |
| `chore` | 빌드·환경·도구 설정 |
| `remove` | 파일 또는 기능 제거 |

예시:

```text
feat(course): 학년·기간별 추천 코스 추가
feat(ai): 교사용 경제 상태 상담 API 추가
fix(trade): 개인거래 부가세 계산 오류 수정
docs: Git 작업 안내 추가
test(deposit): 만기 중복 지급 테스트 추가
```

제목은 짧고 구체적으로 작성하고 마지막에 마침표를 붙이지 않습니다.

변경 이유나 주의사항이 제목만으로 부족하면 본문을 추가합니다.

```text
feat(ai): 경제 밸런싱 추천 범위 추가

가격, 재고, 활동 보상의 안전 범위를 계산하고 교사가 승인하기 전에는
실제 설정에 반영하지 않도록 한다.
```

## 9. 커밋 전 검증

변경 범위에 맞는 테스트를 실행합니다.

### 프론트엔드

```bash
cd frontend
pnpm typecheck
pnpm test:run
pnpm build
```

### 백엔드

Windows:

```powershell
cd backend
.\gradlew.bat test
```

macOS·Linux:

```bash
cd backend
./gradlew test
```

문서만 수정했다면 다음을 확인합니다.

- 링크가 실제 파일을 가리키는가
- 오래된 브랜치명이나 기능명이 남지 않았는가
- MVP와 발전 방향이 섞이지 않았는가
- `git diff --cached --check`가 통과하는가

## 10. 원격에 푸시

처음 푸시하는 작업 브랜치:

```bash
git push -u origin <branch-name>
```

예시:

```bash
git push -u origin feature/teacher-ai-chat
```

이후에는 다음 명령을 사용할 수 있습니다.

```bash
git push
```

다른 사람이 사용하는 브랜치에 `--force`를 사용하지 않습니다.

## 11. GitLab Merge Request

일반 기능과 버그 수정은 GitLab Merge Request로 검토합니다.

권장 흐름:

```text
합의한 기준 브랜치 최신화
→ feature·fix·docs 브랜치 생성
→ 구현과 테스트
→ 작업 브랜치 push
→ GitLab Merge Request 생성
→ 리뷰와 수정
→ 승인 후 병합
```

Merge Request에는 다음 내용을 포함합니다.

- 무엇을 변경했는가
- 왜 변경했는가
- 어떤 화면·API·데이터에 영향이 있는가
- 실행한 테스트와 결과
- 리뷰어가 집중해서 확인할 부분
- 관련 Jira 티켓 또는 이슈

기능 개발의 기본 대상은 브랜치 정리 후 `develop`으로 통일하는 것을 권장합니다. 배포 승인 또는 공식 기준선 반영만 `master`를 대상으로 합니다.

## 12. 충돌 처리

Merge Request 전에 대상 브랜치의 최신 변경을 반영합니다.

```bash
git fetch origin
git rebase origin/<target-branch>
```

팀이 merge 방식을 사용하기로 했다면 다음처럼 처리합니다.

```bash
git merge origin/<target-branch>
```

충돌이 발생하면:

1. 충돌 파일의 양쪽 변경 의도를 확인합니다.
2. 다른 사람의 변경을 임의로 버리지 않습니다.
3. 해결 결과를 테스트합니다.
4. 해결한 파일만 스테이징합니다.
5. rebase 또는 merge를 완료합니다.

의도를 알 수 없으면 해당 파일 작업자와 확인한 뒤 해결합니다.

## 13. 금지하거나 주의할 명령

다음 명령은 다른 변경을 잃을 수 있으므로 정확한 영향 범위를 모르면 사용하지 않습니다.

```text
git reset --hard
git clean -fd
git push --force
git checkout -- <file>
git restore <file>
```

- 공유 브랜치 기록을 임의로 다시 쓰지 않습니다.
- 다른 사람이 만든 커밋을 이유 없이 삭제하지 않습니다.
- 광범위한 파일 삭제 전에는 대상 목록을 확인합니다.
- `.env`, 비밀번호, API 키와 인증서를 커밋하지 않습니다.

비밀정보를 커밋했다면 파일만 삭제해서 끝내지 말고 즉시 팀에 알리고 해당 키를 폐기·재발급합니다.

## 14. 작업 종료

Merge Request가 병합된 뒤 기준 브랜치를 최신화합니다.

```bash
git switch <base-branch>
git pull --ff-only origin <base-branch>
```

병합된 로컬 브랜치를 삭제합니다.

```bash
git branch -d <branch-name>
```

원격 브랜치 삭제는 GitLab 설정이나 팀 규칙에 따라 진행합니다.

## 15. 빠른 체크리스트

### 작업 전

- [ ] 작업 목적과 기준 브랜치를 확인했다.
- [ ] 원격 최신 상태를 가져왔다.
- [ ] 내 작업 브랜치를 만들었다.

### 커밋 전

- [ ] 관련 없는 파일이 포함되지 않았다.
- [ ] 비밀정보가 포함되지 않았다.
- [ ] 필요한 테스트와 빌드를 실행했다.
- [ ] 커밋 메시지가 변경 내용을 설명한다.

### Merge Request 전

- [ ] 대상 브랜치가 맞다.
- [ ] 대상 브랜치의 최신 변경을 반영했다.
- [ ] 변경 이유와 테스트 결과를 작성했다.
- [ ] 리뷰가 끝나기 전 직접 병합하지 않았다.

## 16. 브랜치 정리를 위해 별도로 결정할 사항

현재 저장소는 `release`에 구현이 있고 `develop`이 뒤처져 있습니다. 다음 개발을 시작하기 전에 팀이 아래 사항을 한 번 확정해야 합니다.

1. 기존 `release` 구현을 계속 발전시킬지
2. `release` 구현을 검증한 뒤 `develop`의 새 기준선으로 반영할지
3. 새 지속형 MVP를 기존 구현 위에 확장할지 별도 구조로 시작할지
4. `master`에 문서만 둘지 배포 가능한 코드도 함께 둘지
5. GitLab에서 `master`와 `develop`을 보호 브랜치로 설정할지

이 결정 전에는 큰 병합을 진행하지 않고, 필요한 문서 수정과 긴급한 시연 코드 수정만 명확한 기준 브랜치에서 수행합니다.
