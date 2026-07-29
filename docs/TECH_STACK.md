# 이코빌리지 기술 스택

- 확정일: 2026-07-29
- 상태: MVP 목표 기술 스택 확정
- 적용 범위: 학생·교사용 앱, 관리자·교사 관리 웹, API, AI, AWS 인프라와 배포

## 1. 확정 기술

| 영역 | 확정 기술 |
| --- | --- |
| 학생·교사용 앱 | React Native + Expo + TypeScript |
| 앱 라우팅 | Expo Router |
| 서버 상태 | TanStack Query |
| 앱 로컬 상태 | Zustand |
| 폼·검증 | React Hook Form + Zod |
| 애니메이션·제스처 | Reanimated + Gesture Handler |
| 민감 토큰 저장 | Expo SecureStore |
| 푸시·카메라·QR | Expo Notifications + Expo Camera |
| 관리자·교사 관리 웹 | Next.js App Router |
| 핵심 API 서버 | Java 21 + Spring Boot 4.1 |
| 실시간 수업 | WebSocket + Redis Pub/Sub |
| 메인 데이터베이스 | PostgreSQL 17 |
| 파일·학습 자료 | Amazon S3 + CloudFront |
| 비동기 처리 | Amazon SQS |
| AI 모듈 | Python + FastAPI + Pydantic |
| AI 모델 | AWS Bedrock 기본 + OpenAI 어댑터 |
| 클라우드 | AWS 서울 리전, ECS Fargate, RDS, ElastiCache |
| 앱 배포 | EAS Build + Submit + Update |
| CI/CD | Jenkins |
| 인프라 관리 | Terraform |
| 모니터링 | Sentry + OpenTelemetry + CloudWatch |

## 2. 클라이언트 구성

### 2.1 학생·교사용 앱

- 학교 지급 태블릿에서 React Native와 Expo 기반 앱을 사용합니다.
- Expo Router로 학생·교사 역할별 화면과 접근 권한을 분리합니다.
- 서버 데이터는 TanStack Query, 기기 내 UI 상태는 Zustand로 관리합니다.
- 입력 폼은 React Hook Form과 Zod로 검증합니다.
- 인증 토큰과 민감한 기기 정보는 Expo SecureStore에 저장합니다.
- 알림은 Expo Notifications, QR 인식은 Expo Camera를 사용합니다.
- 잔액·아이템·금융상품·거래 변경은 온라인 상태에서만 서버가 확정합니다.

### 2.2 관리자·교사 관리 웹

- Next.js App Router 기반으로 경제교실 개설, 정책·모듈 설정, 거래 승인, AI 운영 지원과 리포트를 제공합니다.
- 교실 공용 화면은 관리 웹의 발표용 경로로 제공하고 학생 개인 금융정보를 노출하지 않습니다.
- 교사용 앱과 관리 웹은 같은 API와 권한 정책을 사용합니다.

## 3. 서버와 데이터

- Java 21과 Spring Boot 4.1을 핵심 도메인·거래 API의 기준으로 사용합니다.
- 모든 금액·아이템 변경과 금융상품 처리는 핵심 API 서버와 거래 원장을 통과합니다.
- 실시간 퀴즈, 타이머, 지도 활동과 화면 전환은 WebSocket으로 전달합니다.
- 다중 서버의 실시간 이벤트 전달은 Redis Pub/Sub를 사용합니다.
- PostgreSQL 17을 거래, 정책, 금융상품, 퀴즈와 감사 기록의 기준 데이터베이스로 사용합니다.
- 파일과 학습 자료는 S3에 저장하고 CloudFront로 제공합니다.
- 시간이 오래 걸리거나 재시도가 필요한 작업은 SQS로 분리합니다.

## 4. AI 구성

- AI 모듈은 Python, FastAPI와 Pydantic으로 핵심 API 서버와 분리합니다.
- 기본 모델 공급자는 AWS Bedrock이며 공급자 교체와 보조 사용을 위해 OpenAI 어댑터를 둡니다.
- 핵심 API 서버가 권한, 원본 데이터 최소화, 구조화 입력과 결과 적용을 통제합니다.
- AI는 설명·초안·선택지를 생성하고 금액과 경제 효과는 규칙 엔진이 계산합니다.
- AI가 데이터베이스나 학생 계좌를 직접 변경하지 않습니다.
- 교사 승인 없이 AI 결과를 학생에게 공개하거나 정책에 적용하지 않습니다.

## 5. AWS 배포 구조

- 기본 리전은 AWS 서울 리전입니다.
- 앱 서버와 AI 모듈은 ECS Fargate에 배포합니다.
- PostgreSQL은 Amazon RDS, Redis는 Amazon ElastiCache를 사용합니다.
- 정적 파일과 학습 자료는 S3와 CloudFront로 제공합니다.
- 비동기 작업 큐는 SQS를 사용합니다.
- 인프라는 Terraform으로 선언하고 환경별 변경 이력을 관리합니다.

## 6. 빌드·배포

- Jenkins가 테스트, 정적 검사, 서버 이미지 빌드와 배포 파이프라인을 실행합니다.
- 서버 이미지는 검증 후 ECS Fargate 환경에 배포합니다.
- React Native 앱은 EAS Build, Submit과 Update를 사용합니다.
- 운영 배포와 앱 업데이트는 승인 단계를 두고 자동 실행 권한을 제한합니다.
- 데이터베이스 스키마 변경은 애플리케이션 배포와 분리해 검증합니다.

## 7. 관측과 장애 대응

- 앱과 웹 오류 추적은 Sentry를 사용합니다.
- 서비스 간 트레이스와 공통 계측은 OpenTelemetry를 사용합니다.
- 인프라 로그·지표·경보는 CloudWatch를 사용합니다.
- 거래번호, 요청 추적 ID와 AI 실행 ID를 연결해 장애 원인을 추적합니다.
- AI 또는 비동기 처리 장애가 발생해도 핵심 거래 API와 교사 수동 운영은 계속 사용할 수 있어야 합니다.

## 8. MVP에서 사용하지 않는 이전 기준

- Vue, Pinia와 Vue Router
- React 기반 PWA를 학생 앱의 주 배포 방식으로 사용하는 구성
- Django를 핵심 API 서버로 사용하는 구성
- 특정 AI 공급자에 직접 결합된 구현
- 수동으로만 관리하는 AWS 인프라

기존 시연 브랜치에 위 기술이 남아 있더라도 신규 MVP 구현의 기준은 이 문서입니다.
