# 학생·교사용 태블릿 앱

React Native, Expo, TypeScript와 Expo Router를 사용하는 앱 작업 영역입니다.

## 소유 범위

- 학생 로그인·홈·지갑·상점·금융·퀴즈 화면
- 교사가 시작한 지도 활동 화면 전환
- TanStack Query 기반 서버 상태와 Zustand 기반 로컬 UI 상태
- SecureStore, 알림, 카메라·QR과 EAS 배포 설정

## 경계

- 잔액·거래·재고·금융상품 상태는 앱에서 확정하지 않습니다.
- HTTP·WebSocket 계약 변경은 `contracts/`를 먼저 수정하고 합의합니다.
- 학교 태블릿 실기기 테스트에서는 개발 PC의 LAN 주소를 사용합니다.

초기 Expo 프로젝트는 이 디렉터리 안에서만 생성하고 루트 공통 설정을 임의로 변경하지 않습니다.
