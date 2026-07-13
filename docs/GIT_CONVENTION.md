# Git Convention & Branch Strategy

이 문서는 팀 프로젝트의 브랜치 전략과 커밋 컨벤션을 정의합니다.

## Branch Strategy

우리 팀은 Git Flow 브랜치 전략을 기반으로 작업합니다.
기본 작업 단위는 feature 브랜치입니다.

## Branch Types

| 브랜치 | 용도 |
| -- | -- |
| `main` | 실제 배포 가능한 안정 버전 |
| `develop` | 개발 통합 브랜치 |
| `feature/*` | 기능 개발 브랜치 |
| `release/*` | 배포 준비 브랜치 |
| `hotfix/*` | 운영 중 긴급 수정 브랜치 |

## Branch Naming

브랜치 이름은 작업 목적이 드러나도록 작성합니다.

```text
<type>/<short-description>
```

예시:

```text
feature/login
feature/jwt-auth
feature/user-profile
fix/image-upload
docs/readme-update
hotfix/login-error
release/v1.0.0
```

## Git Flow

### 기능 개발

```text
develop
-> feature/*
-> develop
```

1. `develop`에서 feature 브랜치를 생성합니다.
2. 기능 구현 후 feature 브랜치에 커밋합니다.
3. 원격 저장소에 push합니다.
4. `develop`을 대상으로 PR을 생성합니다.
5. 리뷰 후 `develop`에 merge합니다.

### 배포 준비

```text
develop
-> release/*
-> main
-> develop
```

1. 배포할 기능이 `develop`에 모이면 release 브랜치를 생성합니다.
2. 배포 전 테스트, 버그 수정, 문서 보완을 진행합니다.
3. 안정화가 완료되면 `main`에 merge합니다.
4. release 과정에서 발생한 수정 사항은 `develop`에도 반영합니다.

### 긴급 수정

```text
main
-> hotfix/*
-> main
-> develop
```

1. 운영 중 치명적인 버그가 발생하면 `main`에서 hotfix 브랜치를 생성합니다.
2. 수정 후 `main`에 merge합니다.
3. 동일한 수정 사항을 `develop`에도 반영합니다.

## Commit Principle

커밋의 기본 원칙은 다음과 같습니다.

```text
1 Action, 1 Commit
```

하나의 커밋에는 하나의 의미 있는 변경만 포함합니다.

## Commit Message Format

기본 형식은 다음 중 하나를 사용합니다.

```text
<type>(optional scope): <subject>
```

또는

```text
[commit type]: [commit message] ([jira ticket number?])
```

권장 형식:

```text
<type>(<scope>): <subject>
```

scope는 선택 사항입니다.

## Commit Types

| 타입 | 설명 |
| -- | -- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서만 수정, 코드 변경 없음 |
| `style` | 코드 포맷팅, 세미콜론 등 스타일 변경, 논리 변경 없음 |
| `refactor` | 리팩토링, 기능 변화 없음 |
| `test` | 테스트 관련 코드 추가 또는 수정 |
| `chore` | 빌드, 패키지 매니저 설정 등 기타 작업 |
| `design` | CSS 등 사용자 UI 디자인 변경 |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일 또는 폴더명을 수정하거나 옮기는 작업 |
| `remove` | 파일 삭제 작업 |
| `!BREAKING CHANGE` | 큰 API 변경 또는 호환성이 깨지는 변경 |
| `!HOTFIX` | 급하게 치명적인 버그를 수정하는 작업 |

## Commit Examples

```text
feat: JWT 인증 기능 추가
fix: 이미지 업로드 오류 수정
docs: README 설치 방법 수정
style: 코드 포맷팅 적용
refactor: 중복 코드 제거
test: 로그인 테스트 코드 추가
chore: 빌드 설정 변경
design: 로그인 페이지 스타일 수정
comment: 인증 로직 설명 주석 추가
rename: user 폴더명을 member로 변경
remove: 사용하지 않는 이미지 파일 삭제
feat(login): 로그인 버튼에 로딩 스피너 추가
fix(api): 응답 코드 오류 수정
```

Jira 티켓을 포함하는 예시:

```text
feat(login): 로그인 기능 추가 (JIRA-123)
fix(api): 이미지 업로드 오류 수정 (JIRA-124)
```

## Subject Rules

커밋 제목은 다음 규칙을 따릅니다.

- 50글자를 넘지 않도록 간결하게 작성합니다.
- 첫 글자는 대문자가 아닌 소문자로 시작합니다.
- 마지막에 마침표를 붙이지 않습니다.
- 커밋 메시지만 봐도 변경 사항의 요점을 알 수 있게 작성합니다.

## Body Rules

본문은 제목만으로 설명이 부족할 때 작성합니다.

- 무엇을 변경했는지 작성합니다.
- 왜 변경했는지 작성합니다.
- 제목과 본문 사이에는 빈 줄을 둡니다.
- 필요할 때만 작성하며 필수는 아닙니다.

예시:

```text
feat(auth): JWT 재발급 로직 추가

access token 만료 시 refresh token을 검증해 새로운 access token을 발급한다.
기존에는 만료 시 재로그인이 필요해 사용자 경험이 좋지 않았다.
```

## Footer Rules

footer에는 이슈 번호, 참고 커밋, breaking change 등을 작성합니다.

- body와 footer 사이에는 빈 줄을 둡니다.
- 필요할 때만 작성하며 필수는 아닙니다.

예시:

```text
fix(upload): 이미지 확장자 검증 오류 수정

대문자 확장자를 가진 이미지 파일이 업로드되지 않는 문제를 수정했다.

Closes JIRA-125
```

## Commit Convention

1. 의미 있는 단위로 커밋합니다.
2. 한 번의 커밋에 지나치게 많은 변경사항을 포함하지 않습니다.
3. 커밋 메시지는 명확하게 작성합니다.
4. 커밋 메시지만 봐도 변경 사항의 요점을 파악할 수 있어야 합니다.
5. 협업에 필요한 정보를 포함합니다.
6. 코드 변화로 인해 영향을 받는 모듈, 파일, 관련 이슈를 언급합니다.

## Pull Request Rules

PR은 기본적으로 `develop` 브랜치를 대상으로 생성합니다.

PR에는 다음 내용을 포함합니다.

- 작업 내용
- 변경 이유
- 테스트 결과
- 관련 Jira 티켓 또는 이슈 번호
- 리뷰어가 집중해서 봐야 할 부분

## Merge Rules

- PR은 최소 1명 이상의 리뷰 후 merge합니다.
- 충돌이 발생하면 작업자가 직접 해결합니다.
- merge 전 `develop`의 최신 변경 사항을 반영합니다.
- merge 후 불필요한 feature 브랜치는 삭제합니다.

