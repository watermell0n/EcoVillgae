# Getting Started

프로젝트를 처음 시작하는 팀원을 위한 기본 가이드입니다.

## 1. 저장소 클론

```bash
git clone <repository-url>
cd <repository-name>
```

## 2. 기본 브랜치 확인

```bash
git branch
git branch -a
```

일반적으로 다음 브랜치를 기준으로 작업합니다.

- `main`: 배포 가능한 안정 버전
- `develop`: 다음 배포를 위한 통합 개발 브랜치
- `feature/*`: 기능 개발 브랜치
- `release/*`: 배포 준비 브랜치
- `hotfix/*`: 운영 중 긴급 수정 브랜치

## 3. 최신 develop 브랜치 가져오기

```bash
git checkout develop
git pull origin develop
```

## 4. 기능 브랜치 생성

기능 단위로 브랜치를 생성합니다.

```bash
git checkout -b feature/login
```

브랜치 이름 예시:

```text
feature/login
feature/jwt-auth
feature/user-profile
fix/image-upload
docs/readme-update
```

## 5. 작업 후 커밋

커밋은 `1 Action, 1 Commit` 원칙을 따릅니다.

```bash
git add .
git commit -m "feat(login): 로그인 기능 추가"
```

## 6. 원격 브랜치에 push

```bash
git push origin feature/login
```

## 7. Pull Request 생성

PR은 기본적으로 `develop` 브랜치를 대상으로 생성합니다.

PR 작성 시 포함하면 좋은 내용:

- 작업 내용
- 변경 이유
- 테스트 여부
- 관련 Jira 티켓 또는 이슈 번호
- 리뷰어가 확인해야 할 부분

## 8. PR merge 후 정리

PR이 merge되면 로컬 브랜치를 정리합니다.

```bash
git checkout develop
git pull origin develop
git branch -d feature/login
```

원격 브랜치 삭제는 팀 규칙에 따라 진행합니다.

```bash
git push origin --delete feature/login
```

## 권장 작업 흐름

```text
develop 최신화
-> feature 브랜치 생성
-> 기능 구현
-> 커밋
-> push
-> PR 생성
-> 코드 리뷰
-> develop merge
```

