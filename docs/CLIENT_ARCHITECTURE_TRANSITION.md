# 학생 앱·교사 웹 아키텍처 정정과 전환 계획

- 결정일: 2026-08-01
- 상태: 팀 실행 기준
- 대체 결정: `S15P11B201-219`의 학생 Vite·PWA 우선안과 기존 교사용 Expo 앱 범위

## 1. 확정 구조

| 사용자·화면 | 구현 기술 | 배포 단위 |
| --- | --- | --- |
| 학생 | React Native + Expo + TypeScript | Galaxy Tab용 EAS 내부 배포 앱 |
| 교사 | Next.js App Router | 교사 웹 |
| 학교·플랫폼 관리자 | Next.js App Router | 관리자 웹 |
| 교실 공용 화면 | Next.js 발표 전용 경로 | 웹 |
| 공통 서버 | Java 21 + Spring Boot 4.1 | REST API·WebSocket |

학생 앱과 교사 웹은 UI, 라우팅, 인증 저장 방식과 배포를 분리합니다. 금액·거래·권한·정책의 최종 판정은 공통 Spring Boot API가 수행하며 OpenAPI·AsyncAPI 계약을 공유합니다.

## 2. 현재 상태와 전환 원칙

- `student-app/`에는 React·Vite 학생 UI가 구현되어 있으나 최종 기술은 Expo입니다.
- 기존 Vite 구현은 Expo 화면 이전의 참고 자료로 보존하고 신규 기능을 추가하지 않습니다.
- Expo에서 로그인, Expo Router, API 연결, 지갑·상점 핵심 화면과 Galaxy Tab 설치 빌드가 통과하면 Vite 코드를 별도 MR로 제거합니다.
- `teacher-web/`은 Next.js를 유지하며 수업 진행 화면과 상세 관리 화면을 같은 웹 애플리케이션에서 제공합니다.
- `SCR-TCH-*`는 별도 교사용 앱 ID가 아니라 교사 웹의 수업 진행 경로 ID로 유지합니다.

## 3. 역할별 실행 지시

### 허병우 · 백엔드·통합

- 요구사항·기술·API·권한·화면·RTM 문서를 확정 구조로 동기화합니다.
- 학생 Expo Bearer 토큰·SecureStore 흐름과 교사 웹 HttpOnly 쿠키 흐름을 분리해 계약과 테스트를 점검합니다.
- OpenAPI·AsyncAPI를 프런트의 유일한 서버 계약으로 유지하고 공용 파일 변경을 통합합니다.

### 신현경 · 프런트엔드

- 최신 `develop`에서 Expo 전환 브랜치를 만들고 기존 Vite UI를 화면 단위로 이전합니다.
- Expo Router, TanStack Query, React Hook Form·Zod, SecureStore를 우선 연결합니다.
- 교사 기능은 `teacher-web/`의 Next.js 수업 진행·관리 경로에만 구현합니다.
- Galaxy Tab 내부 배포 빌드를 검증하기 전 Vite 구현을 삭제하지 않습니다.

### 박수경 · AI

- 학생 앱과 교사 웹이 AI 서비스를 직접 호출하지 않고 Spring Boot를 통해서만 요청하는지 점검합니다.
- 기존 FastAPI·Pydantic·Bedrock/OpenAI 어댑터 계약이 클라이언트 분리의 영향을 받지 않는지 회귀 검증합니다.
- AI 결과가 교사 웹의 검토·승인 흐름을 거치고 학생 계좌·정책을 직접 변경하지 않도록 유지합니다.

### 최상진 · 캐릭터 디자인

- 캐릭터는 Galaxy Tab Expo 앱에서도 웹과 동일한 64×64 도트 에셋으로 렌더링하고, 슬롯별 장착 상태만 전달합니다. 제작·검수 기준은 [도트 캐릭터 이미지 작업 규격](design/CHARACTER_ASSET_SPEC.md)을 따릅니다.
- 프로필, 상점 미리보기, 장착 슬롯별 크기·파일명·여백 규칙을 문서화합니다.
- 웹 전용 CSS 표현에 의존하지 않는 에셋 전달 기준을 프런트 담당자와 확인합니다.

## 4. Git·검증 규칙

1. 모든 신규 작업은 최신 `develop`을 `pull --ff-only`로 받은 뒤 분기합니다.
2. 역할별 Jira 이슈 하나를 브랜치와 MR 하나에 연결합니다.
3. 공용 계약·루트 설정은 통합 담당자와 먼저 합의합니다.
4. `develop`에서 앱·웹·백엔드·AI 통합 검증을 마친 결과만 `master`로 이동합니다.
5. 기존 Vite 제거는 Expo 핵심 기능과 Galaxy Tab 빌드 검증 후 별도 MR로 수행합니다.

## 5. 팀 공지문

> 학생 클라이언트는 학교 지급 Galaxy Tab용 React Native·Expo 앱으로, 교사는 별도 모바일 앱 없이 Next.js 웹으로 확정했습니다. 현재 `student-app/`의 Vite UI는 Expo 전환 참고용 프로토타입이므로 신규 기능을 추가하지 말고, 핵심 화면과 실기기 빌드가 검증될 때까지 삭제하지 않습니다. 학생 앱과 교사 웹은 UI·인증 저장·배포를 분리하고 Spring Boot REST API와 WebSocket 계약을 공유합니다. 각 담당자는 Jira의 본인 전환 이슈에서 작업하고 최신 `develop`에서 분기해 `develop` 대상 MR을 만듭니다. `master`에는 통합 검증을 마친 결과만 반영합니다.
