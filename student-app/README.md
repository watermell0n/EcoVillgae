# 학생 앱

학교 지급 Galaxy Tab에서 사용하는 React Native·Expo·TypeScript 학생 앱입니다.
앱 코드는 모두 [`expo/`](expo/)에 있습니다.

캐릭터 상점은 **64×64 단일 논리 캔버스**를 사용합니다. 한 가지 기본 체형 위에
모자·얼굴·상의·하의·목·신발·등·손·효과를 슬롯별로 합성하며, 배경은 캐릭터 뒤에서만
렌더링합니다. 제작·검수 기준은
[도트 캐릭터 이미지 작업 규격](../docs/design/CHARACTER_ASSET_SPEC.md)을 따릅니다.
`48×56` 렌더 경로와 에셋은 폐기 대상입니다.

## 디렉터리 구조

- `expo/`: 학생 앱 본체(Expo Router 기반). 모든 신규 기능은 여기서 구현합니다.
- `public/character/`: 캐릭터 도트 에셋 **정본**. `scripts/character/sync-assets.js`가
  이 경로를 원본으로 삼아 Expo 번들 에셋으로 동기화합니다. 앱 빌드 산출물이 아니므로
  Expo 전환 이후에도 이 위치를 유지합니다.

## 실행

```bash
cd expo && npm install && npm run web
```

- 웹 미리보기: `npm run web` (기본 포트 `8081`)
- 실기기·에뮬레이터: `npm run android`

백엔드 주소는 `EXPO_PUBLIC_API_BASE_URL` 환경변수로 지정합니다(기본
`http://localhost:8080`). 학교 태블릿 실기기 테스트에서는 `localhost` 대신 개발 PC의
같은 네트워크 LAN 주소를 사용합니다.

## 기술 스택

- Expo Router, TanStack Query, React Hook Form·Zod, Expo SecureStore
- 학생 액세스 토큰은 메모리에만, 갱신 토큰은 SecureStore에 저장합니다.
- 로그인·홈·지갑·월급명세서·상점·은행·투자·거래·퀴즈·수업·캐릭터·프로필 화면을 제공합니다.
- 실시간 채널은 연결이 끊기면 새 일회용 티켓으로 재연결하고 마지막으로 수신한 채널
  시퀀스부터 복구합니다. 목업 로그인 세션에서는 연결하지 않습니다.
- 서버 계약이 아직 확정되지 않은 투자 주문과 캐릭터 장착은 조회형 UI로 두고 확정 API
  연결 후 활성화합니다.

## 소유 범위

- 학생 로그인·홈·지갑·상점·금융·퀴즈 화면
- 교사가 시작한 지도 활동 화면 전환

## 경계

- 잔액·거래·재고·금융상품 상태는 화면에서 확정하지 않습니다.
- HTTP·WebSocket 계약 변경은 `contracts/`를 먼저 수정하고 합의합니다.
- 교사 화면은 이 디렉터리에 추가하지 않고 `teacher-web/`의 Next.js 경로에서 구현합니다.
