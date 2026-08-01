# 학생 앱·교사 웹 인증 계약 점검

- Jira: `S15P11B201-261`
- 기준 아키텍처: 학생 Galaxy Tab용 Expo 앱, 교사용 Next.js 웹, 공통 Spring Boot API
- 점검일: 2026-08-01

## 확정 계약

| 구분 | 학생 Expo 앱 | 교사 Next.js 웹 |
| --- | --- | --- |
| 로그인 | 반 코드·번호·닉네임·비밀번호·기기 ID | 외부 OIDC Authorization Code + PKCE 후 `id_token` 전달 |
| 액세스 토큰 | 응답 본문, 앱 메모리 | 응답 본문, 웹 메모리 |
| 갱신 토큰 저장 | Expo SecureStore | HttpOnly·Secure·SameSite=Strict 쿠키 |
| 토큰 갱신 전송 | `X-Refresh-Token` | `ecovillage_teacher_refresh` 쿠키 |
| 갱신 응답 | 새 액세스·갱신 토큰 | 새 액세스 토큰 본문 + 회전된 Set-Cookie |
| 세션 수 | 학생당 활성 Galaxy Tab 1대 | 교사별 복수 브라우저 세션 |
| 보호 REST API | `Authorization: Bearer` | `Authorization: Bearer` |

교사 갱신 토큰은 JavaScript가 읽을 수 있는 JSON에 넣지 않습니다. 학생 갱신 헤더와
교사 갱신 쿠키를 동시에 보내거나 서로 바꿔 보내면 요청을 거절합니다. 갱신 시 이전
토큰은 즉시 폐기되어 재사용할 수 없습니다.

## 프런트 연결 예시

### 학생 Expo

로그인 응답의 액세스 토큰은 Zustand 등 메모리 상태에, 갱신 토큰은
`expo-secure-store`에 저장합니다. 저장 키에는 학급·학생 식별자를 직접 넣지 않습니다.

```ts
import * as Crypto from "expo-crypto";

const result = await api.post("/auth/student-sessions", credentials, {
  headers: { "Idempotency-Key": Crypto.randomUUID() },
});

setAccessToken(result.data.access_token);
await SecureStore.setItemAsync("ecovillage.refresh", result.data.refresh_token);
```

갱신 후에는 새 갱신 토큰으로 SecureStore 값을 덮어쓴 뒤 액세스 토큰을 교체합니다.
현재 `student-app`의 Vite 프로토타입이 사용하는 `localStorage` 세션 저장은 최종 Expo
구현에서 재사용하지 않습니다.

### 교사 Next.js

로그인·갱신 요청에는 쿠키 수신·전송을 위해 `credentials: "include"`를 지정합니다.
응답의 액세스 토큰만 클라이언트 메모리에 보관하고 `refresh_token` 필드는 기대하지
않습니다.

```ts
const response = await fetch(`${apiBaseUrl}/auth/teacher-sessions`, {
  method: "POST",
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    "Idempotency-Key": crypto.randomUUID(),
  },
  body: JSON.stringify({ id_token: idToken }),
});

const { data } = await response.json();
setAccessToken(data.access_token);
```

보호 API에는 메모리의 액세스 토큰을 Bearer 헤더로 보냅니다. 새로고침으로 메모리가
비면 갱신 API를 한 번 호출해 액세스 토큰을 복원합니다.

## 환경변수

| 변수 | 로컬 기본값 | 운영 규칙 |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | `http://localhost:8080` | 실제 Galaxy Tab에서는 개발 서버 또는 운영 HTTPS 주소 |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | 교사 웹 API 주소. 가능하면 역방향 프록시로 같은 출처 사용 |
| `WEB_ALLOWED_ORIGINS` | `http://localhost:3000` | 쉼표 구분 명시 원본만 허용, `*` 금지 |
| `AUTH_REFRESH_COOKIE_SECURE` | `false` | HTTPS 운영에서는 반드시 `true` |
| `AUTH_ACCESS_TOKEN_TTL` | `15m` | 짧게 유지 |
| `AUTH_REFRESH_TOKEN_TTL` | `14d` | 회전형 토큰 만료 |

## CORS·CSRF 경계

- 운영은 교사 웹과 API를 같은 사이트의 HTTPS 역방향 프록시로 제공하는 방식을 우선합니다.
- 분리 출처 개발 환경은 `WEB_ALLOWED_ORIGINS`에 등록된 원본만 credentialed CORS를 허용합니다.
- 와일드카드 원본은 서버 시작 단계에서 거절합니다.
- 교사 쿠키는 SameSite=Strict이며, 갱신 POST는 브라우저 단순 폼이 만들 수 없는
  `Idempotency-Key` 헤더를 요구합니다. 허용되지 않은 출처는 CORS 사전 요청을 통과할 수 없습니다.
- 일반 보호 API는 자동 첨부되는 인증 쿠키가 아니라 Bearer 토큰을 사용하므로 쿠키 기반
  CSRF 대상이 아닙니다.

## WebSocket 인증 경계

학생과 교사 모두 장기 토큰을 WebSocket URL에 넣지 않습니다. Bearer 액세스 토큰으로
`API-AUTH-007`에 60초·1회용·역할/학급/채널 범위 티켓을 요청한 뒤
`wss://{host}/ws/v1?ticket=...`에 연결합니다. 티켓 발급·원자 소비 및 Gateway 구현은
실시간 채널 작업 `S15P11B201-176`의 범위이며, 그 전까지 REST 인증 구현이
WebSocket 연결을 직접 허용하는 것으로 간주하지 않습니다.

## 점검 결과와 검증 항목

- 교사 로그인·갱신 JSON에서 갱신 토큰 노출 제거
- 학생 헤더와 교사 쿠키의 갱신 경로 분리 및 교차 사용 거절
- 학생 단일 세션, 교사 복수 세션, 회전 토큰 재사용 거절, 로그아웃 검증
- OpenAPI의 미구현 `ECOVILLAGE_SESSION` 인증 제거 및 Bearer 보호 API로 통일
- 학생·교사별 갱신 응답을 OpenAPI `oneOf`로 구분
- 허용 원본 기반 credentialed CORS 설정과 사전 요청 테스트 추가
