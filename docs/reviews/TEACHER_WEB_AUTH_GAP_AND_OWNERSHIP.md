# 교사 웹 인증 미연결과 AI·프런트 소유 경계 정리

- 작성일: 2026-08-02
- 작성자: 박수경 (AI 담당)
- 관련 문서: [학생 앱·교사 웹 인증 계약 점검](CLIENT_AUTH_CONTRACT_AUDIT.md) (`S15P11B201-261`)
- 대상 브랜치: `feat/ai-features` (최신 `develop` 병합 완료)

`develop`의 클라이언트 구조·인증 계약 정정을 `feat/ai-features`에 병합하면서 교사 웹을
점검한 결과입니다. 확인된 사항 두 가지(A·B)와 협업 규칙 한 가지(C)를 공유합니다.

## 요약

| 항목 | 내용 | 상태 |
| --- | --- | --- |
| A | 교사 웹이 보호 API에 Bearer 토큰을 보내지 않음 | **미해결 · 팀 결정 필요** |
| B | 액세스 토큰을 `sessionStorage`에 두어 계약 위반 | 수정 완료 |
| C | AI 담당이 교사 UI 파일을 직접 수정 | 파일 분리로 조치, 규칙 합의 필요 |

## 스택 확인 결과 (이상 없음)

Expo·Vite 혼용 문제가 AI 작업 범위에도 있는지 확인했으나 해당 사항이 없었습니다.

| 확인 | 결과 |
| --- | --- |
| AI 작업이 `student-app`(Vite)을 수정했는가 | 수정 이력 없음 |
| `teacher-web` 스택 | Next.js 15 App Router + React 19 + TS (계약과 일치) |
| AI 코드에 Vite 흔적(`vite`, `import.meta.env`) | 없음 |
| `docs/ai/` 15개 문서의 클라이언트 서술 | 정정 대상 없음 |

참고로 Expo 학생 앱 프로젝트는 아직 저장소에 없고(`app.json` 부재) `student-app`은
`vite ^6.0.0` 상태입니다. 학생 프런트 담당 범위라 이 문서에서는 다루지 않습니다.

---

## A. 교사 웹이 인증 없이 API를 호출합니다

### 현상

교사가 로그인 버튼을 눌러도 화면 단계만 바뀌고 서버에 인증 요청을 보내지 않습니다.
그 결과 액세스 토큰이 없는 상태로 보호 API를 호출해 모두 인증 오류로 실패합니다.

```
로그인 클릭 → setStage("classrooms")  ← 화면만 전환
   → 보호 API 호출 시 Authorization 헤더 없음 → 401
```

로그인 폼도 이메일·비밀번호 입력인데, 확정 계약상 교사 로그인은 외부 OIDC
Authorization Code + PKCE로 받은 `id_token`을 서버에 전달하는 방식이라 형태가 다릅니다.

### 백엔드는 이미 준비되어 있습니다

A는 백엔드 구현이 아니라 프런트 연결과 설정값 문제입니다.

| 엔드포인트 | 동작 | 상태 |
| --- | --- | --- |
| `POST /auth/teacher-sessions` | `id_token` 검증 → 액세스 토큰(본문) + 갱신 쿠키(Set-Cookie) | 구현 완료 |
| `POST /auth/token-refreshes` | 갱신 쿠키로 재발급 → 새 액세스 토큰 + 쿠키 회전 | 구현 완료 |
| `DELETE /auth/sessions/current` | 로그아웃 + 갱신 쿠키 삭제 | 구현 완료 |
| `GET /auth/me` | 현재 사용자 조회 | 구현 완료 |
| CORS `allowCredentials(true)` | 교차 출처 쿠키 송수신 허용 | `develop` 병합으로 반영됨 |

요청·응답 형식(JSON은 snake_case):

- 로그인 요청 — 헤더 `Idempotency-Key`(UUID), 본문 `{ "id_token": "..." }`
- 로그인 응답 201 — `{ access_token, expires_at, session_id, teacher_id, display_name, role }` + `Set-Cookie`
- 갱신 응답 201 — 교사는 `refresh_token`이 본문에 포함되지 않고 쿠키만 회전됩니다

### 남은 작업

| # | 작업 | 담당 | 비고 |
| --- | --- | --- | --- |
| 1 | OIDC Authorization Code + PKCE로 `id_token` 획득 | 교사 프런트 | 분량 가장 큼 |
| 2 | `POST /auth/teacher-sessions` 호출 후 `setAccessToken()` 연결 | 교사 프런트 | 연결 지점 준비됨 |
| 3 | 401 응답 시 갱신 후 원요청 재시도 | 교사 프런트 | |
| 4 | 로그아웃 시 `DELETE /auth/sessions/current` + `clearAccessToken()` | 교사 프런트 | |
| 5 | OIDC 제공자 확정 및 설정값 입력 | 백엔드·인프라 | **선행 블로커** |

### 블로커

`application.yml`의 `ecovillage.teacher-oidc.enabled` 기본값이 `false`이고, 이 상태에서
로그인을 호출하면 `OidcTeacherIdentityVerifier`가 다음과 같이 응답합니다.

```
503 AUTH_PROVIDER_UNAVAILABLE
"교사 로그인 제공자가 아직 설정되지 않았습니다."
```

**어떤 OIDC 제공자를 사용할지 정하고 `issuer`, `jwk-set-uri`, `client-id`를 채워야**
프런트 작업 1~4를 실제로 검증할 수 있습니다. 그전까지는 프런트를 완성해도 로그인이
503으로 실패합니다. 제공자 선정은 팀 결정 사항입니다.

---

## B. 액세스 토큰 저장 위치 수정 (완료)

확정 계약은 "교사 웹: 액세스 토큰은 메모리, 갱신 토큰은 HttpOnly 쿠키"입니다.
기존 구현은 액세스 토큰을 `sessionStorage`에서 읽고 있었습니다. `sessionStorage`는
페이지의 모든 스크립트가 읽을 수 있어 XSS 발생 시 토큰이 그대로 노출됩니다.

| 항목 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 액세스 토큰 | `sessionStorage` | 모듈 메모리 변수 |
| 설정·폐기 수단 | 없음 | `setAccessToken()` / `clearAccessToken()` |
| 갱신 토큰 | HttpOnly 쿠키 (`credentials: "include"`) | 변경 없음 |

갱신 토큰은 원래부터 클라이언트에 저장하지 않고 쿠키로만 주고받고 있어 계약을
지키고 있었습니다. 토큰을 모듈 메모리에 두게 되면서, 서버 컴포넌트가 이 모듈을
가져가면 상태가 모든 요청에 공유될 수 있어 `app/api.ts`를 `"use client"`로 고정했습니다.

동작 변화는 없습니다. 변경 전에도 `sessionStorage`에 값을 넣는 코드가 없어 토큰이 항상
비어 있었고, 지금도 `setAccessToken()` 호출부가 없어 마찬가지입니다. 이번 수정의 의미는
저장 위치 계약을 맞추고 A의 연결 지점을 만든 것입니다.

---

## C. AI 담당과 교사 프런트의 파일 소유 경계

### 상황

`teacher-web`은 신현경 님이 만들고 관리해 온 디렉터리인데, AI 담당 커밋 `12ef552`가
`page.tsx`를 430줄 추가·263줄 삭제로 크게 수정했습니다. 팀 규칙은 "AI는 학생·교사 UI
프로젝트를 직접 수정하지 않는다", "다른 담당자의 디렉터리를 수정해야 하면 먼저 공유한다"
입니다.

이번에는 같은 기간에 교사 프런트 작업이 없어 충돌이 발생하지 않았습니다. 다만 이번
`develop` 병합에서 `application.yml`·`WEBSOCKET_SPEC.md` 충돌과, `openapi.yaml`에서
git이 충돌로 표시하지 못한 채 잘못 합쳐진 사례가 실제로 있었습니다.

다만 추가된 내용 자체는 순수 UI가 아니라 AI 계약을 화면에 붙이는 작업이었습니다.
즉 **내용은 AI 담당 몫인데 파일 소유자는 교사 프런트**인 경우로, 기존 규칙이 다루지
않던 상황입니다.

### 조치 — 파일 분리 (적용 완료)

소유자별로 파일을 나누어 앞으로 같은 파일을 동시에 고치지 않도록 했습니다.

| 파일 | 소유 | 내용 |
| --- | --- | --- |
| `app/ai/AiWorkspace.tsx` | **AI 담당** | AI 화면의 단일 진입점. 하위 패널을 배치 |
| `app/ai/ModuleActivationPanel.tsx` | **AI 담당** | 시작 전 점검·추천 기반 모듈 활성화 |
| `app/ai/DraftRecommendationPanel.tsx` | **AI 담당** | 상품 가격·GAME 보상 추천 |
| `app/page.tsx` | **교사 프런트** | 화면 배치. `AiWorkspace` 태그 하나로 가져다 씀 |
| `app/api.ts` | 공용 (변경 시 공유) | API 클라이언트, 응답 타입, 모듈 계약 |
| `app/types.ts` | **AI 담당** | AI 패널이 `page.tsx`로부터 받는 값의 최소 계약 |
| `app/ui.tsx` | 교사 프런트 | 공통 레이아웃 조각 (`PlainSection`) |

각 파일 상단에 소유자를 주석으로 적어 두었습니다. AI 기능을 추가·수정할 때
`page.tsx`를 건드릴 필요가 없고, 겹치는 지점은 `AiWorkspace`를 배치한 한 줄뿐입니다.

### 병합에서 드러난 보완점

`develop` 병합(2026-08-03)에서 교사 프런트가 `page.tsx`를 다시 크게 고치며
`ModuleActivationPanel` 배치가 빠졌습니다. 파일은 남아 있었고 타입 검사·빌드·테스트가
모두 통과했으므로 **어떤 자동 검사도 잡지 못했습니다.** 그 결과 모듈 활성화와 밸런싱
제안 적용이 화면에서 도달 불가 상태가 되었습니다.

같은 병합에서 `page.tsx`가 `types.ts`의 타입들을 인라인으로 다시 선언했습니다. 그래서
`types.ts`는 "화면 간 공유 타입"이 아니라 **AI 패널의 프롭 계약**으로 역할을 좁혔고,
`page.tsx`의 선언과 구조적으로 호환되는 부분집합만 두기로 했습니다.

교훈은 **배치가 빠지는 회귀는 타입으로 막히지 않는다**는 점입니다. AI 패널을 추가하거나
되살릴 때는 `AiWorkspace` 안에 두어 진입점을 하나로 유지합니다.

### 합의가 필요한 부분 (제안)

위 분리는 제안이며 교사 프런트 담당자의 확인이 필요합니다. 특히 다음 두 가지입니다.

1. `app/ai/` 디렉터리를 AI 담당 소유로 두는 것에 동의하는지
2. 앞으로 AI 기능이 늘어날 때 `app/ai/` 안에서만 작업하고 `page.tsx` 배치 한 줄만
   교사 프런트에 요청하는 흐름이 맞는지

---

## 팀 결정이 필요한 사항

| # | 안건 | 결정 주체 |
| --- | --- | --- |
| 1 | 교사 OIDC 제공자 선정 및 설정값 (`issuer`, `jwk-set-uri`, `client-id`) | 백엔드·인프라 |
| 2 | A의 프런트 작업(1~4) 담당자와 일정 | 교사 프런트 |
| 3 | C의 파일 소유 경계 확정 | 교사 프런트 · AI |

## 검증 결과

| 검사 | 결과 |
| --- | --- |
| 계약 재생성 후 diff | 0 |
| 계약·문서 검증기 6종 | 전체 통과 |
| 백엔드 테스트 | 195건 실행, 실패 0 (DB 필요 21건은 Docker 미실행으로 건너뜀) |
| AI 서비스 테스트 | 541건 실행, 실패 0 |
| `teacher-web` 타입 검사 | 통과 |
| `teacher-web` 프로덕션 빌드 | 통과 |

DB 무결성 테스트 21건은 PostgreSQL 컨테이너가 필요해 건너뛴 상태입니다. Docker를
실행하고 `./gradlew test`를 다시 돌리면 함께 검증됩니다.

## 병합에서 드러난 회귀 한 건

`develop` 병합 후 AI 서비스의 이름 규칙 테스트가 실패했습니다. 두 브랜치 각각에서는
드러나지 않고 병합한 뒤에만 나타나는 종류였습니다.

- `develop`이 `docs/legacy/pixel-backend-reference/`를 추가했고 그 안에 이전 프로젝트
  이름을 쓰는 경로와 코드가 들어 있습니다.
- 이름 규칙 테스트는 `feat/ai-features`에만 있어 `develop`에서는 실행되지 않았습니다.

해당 폴더는 참고용 스냅샷이라 이름 규칙 적용 대상에서 제외했습니다(커밋 `d4a9b20`).
`openapi.yaml`의 `cookieAuth` 사례와 같은 유형이므로, 병합 후에는 양쪽 브랜치의 검사를
모두 돌려보는 편이 안전합니다.
