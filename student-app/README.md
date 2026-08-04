# 학생 앱 전환 작업 영역

최종 목표는 학교 지급 Galaxy Tab에서 사용하는 React Native·Expo·TypeScript 학생 앱입니다.
현재 저장된 React·Vite 구현은 화면 흐름과 디자인을 Expo로 이전하기 위한 프로토타입이며 최종 배포 대상이 아닙니다.
캐릭터 상점은 **64×64 단일 논리 캔버스**를 사용합니다. 한 가지 기본 체형 위에
모자·얼굴·상의·하의·목·신발·등·손·효과를 슬롯별로 합성하며, 배경은 캐릭터 뒤에서만
렌더링합니다. 제작·검수 기준은
[도트 캐릭터 이미지 작업 규격](../docs/design/CHARACTER_ASSET_SPEC.md)을 따릅니다.
`48×56` 렌더 경로와 에셋은 폐기 대상입니다.

## 전환 규칙

- 신규 학생 기능은 Expo 전환 브랜치에서 구현합니다.
- 현재 Vite 코드는 로그인·라우팅·핵심 화면·Galaxy Tab 설치 빌드가 검증될 때까지 삭제하지 않습니다.
- TypeScript 타입, 데이터 구조, 디자인 토큰과 에셋은 재사용할 수 있지만 HTML·CSS·DOM 코드는 React Native UI로 다시 작성합니다.
- 교사 화면은 이 디렉터리에 추가하지 않고 `teacher-web/`의 Next.js 경로에서 구현합니다.

## 소유 범위

- 학생 로그인·홈·지갑·상점·금융·퀴즈 화면
- 교사가 시작한 지도 활동 화면 전환
- 서버 상태와 로컬 UI 상태는 이후 API 연동 단계에서 도입합니다.

## 경계

- 잔액·거래·재고·금융상품 상태는 화면에서 확정하지 않습니다.
- HTTP·WebSocket 계약 변경은 `contracts/`를 먼저 수정하고 합의합니다.
- 학교 태블릿 실기기 테스트에서는 개발 PC의 LAN 주소를 사용합니다.

초기 React 프로젝트는 이 디렉터리 안에서만 생성하고 루트 공통 설정을 임의로 변경하지 않습니다.

## 현재 Vite 프로토타입

- `src/App.tsx`: 사이드바 기반 학생 UI 프로토타입, 마을 광장, 지갑, 월급명세서, 상점, 캐릭터 슬롯 화면
- `src/data/`: 백엔드 DTO 형태를 따라 만든 임시 화면 데이터
- `src/styles.css`: `docs/design/FE_SCREEN_GENERATION_SPEC.md`의 색상·레이아웃 규칙

## Expo 전환 앱

- 위치: `expo/`
- 실행: `cd expo && npm install && npm run android`
- Expo Router, TanStack Query, React Hook Form·Zod, Expo SecureStore 기반입니다.
- 학생 액세스 토큰은 메모리에만, 갱신 토큰은 SecureStore에 저장합니다.
- Expo 로그인·홈·지갑·월급명세서·상점·은행·투자·거래·퀴즈·수업·캐릭터·프로필 화면 흐름을 이관했습니다.
- 서버 계약이 아직 확정되지 않은 투자 주문과 캐릭터 장착은 조회형 UI로 두고 확정 API 연결 후 활성화합니다.

## 현재 프로토타입 실행

```bash
npm install
npm run dev
```

개발 서버는 `/api` 요청을 `http://localhost:8080` 백엔드로 프록시합니다.
백엔드 주소를 바꿔야 하면 `VITE_API_BASE_URL` 환경변수를 사용합니다.

실제 학생 세션에서는 `VITE_WEBSOCKET_URL`(기본 개발 프록시 경로 `/ws/v1`)로
실시간 채널에 연결합니다. 연결이 끊기면 새 일회용 티켓으로 재연결하고 마지막으로
수신한 채널 시퀀스부터 복구합니다. 목업 로그인 세션에서는 연결하지 않습니다.
