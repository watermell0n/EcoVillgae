# 이코빌리지 학생 Expo 앱

Galaxy Tab에서 사용하는 React Native + Expo + TypeScript 학생 앱입니다. 학생 앱 코드는 이 프로젝트 하나이며, 모든 신규 학생 기능은 여기에 구현합니다.

## 실행

Node 24 사용을 권장합니다. Node 26에서는 Expo 개발 서버가 Metro 시작 전에 멈출 수 있습니다.

```bash
nvm use
npm install
cp .env.example .env
npm run android
```

실제 태블릿에서는 `EXPO_PUBLIC_API_BASE_URL`을 개발 PC의 LAN 주소로 설정합니다.

## 이관된 화면

- 로그인, 경제교실 홈, 지갑, 월급명세서, 상점
- 예금, 투자 조회, 개인거래, 퀴즈, 공동기금 투표
- 캐릭터 보유 아이템, 프로필과 로그아웃

투자 주문과 캐릭터 장착은 서버 계약·Expo 에셋 규격이 확정될 때까지 조회형 UI로 유지합니다.

## 인증 저장 원칙

- 액세스 토큰은 앱 프로세스 메모리에만 보관합니다.
- 갱신 토큰과 기기 ID는 Expo SecureStore에 보관합니다.
- 보호 API에는 Bearer 액세스 토큰을 사용합니다.
- 401 응답 시 SecureStore의 갱신 토큰을 `X-Refresh-Token` 헤더로 보내 한 번만 갱신하고 요청을 재시도합니다.
- 토큰은 AsyncStorage나 웹 localStorage에 저장하지 않습니다.
