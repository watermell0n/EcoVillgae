# 학생 웹

React, Vite, TypeScript를 사용하는 학생 웹 작업 영역입니다.

## 소유 범위

- 학생 로그인·홈·지갑·상점·금융·퀴즈 화면
- 교사가 시작한 지도 활동 화면 전환
- 서버 상태와 로컬 UI 상태는 이후 API 연동 단계에서 도입합니다.

## 경계

- 잔액·거래·재고·금융상품 상태는 화면에서 확정하지 않습니다.
- HTTP·WebSocket 계약 변경은 `contracts/`를 먼저 수정하고 합의합니다.
- 학교 태블릿 실기기 테스트에서는 개발 PC의 LAN 주소를 사용합니다.

초기 React 프로젝트는 이 디렉터리 안에서만 생성하고 루트 공통 설정을 임의로 변경하지 않습니다.

## 현재 UI 구현

- `src/App.tsx`: 사이드바 기반 학생 웹, 마을 광장, 지갑, 월급명세서, 상점, 캐릭터 슬롯 화면
- `src/data/`: 백엔드 DTO 형태를 따라 만든 임시 화면 데이터
- `src/styles.css`: `docs/design/FE_SCREEN_GENERATION_SPEC.md`의 색상·레이아웃 규칙

## 실행

```bash
npm install
npm run dev
```

개발 서버는 `/api` 요청을 `http://localhost:8080` 백엔드로 프록시합니다.
백엔드 주소를 바꿔야 하면 `VITE_API_BASE_URL` 환경변수를 사용합니다.
