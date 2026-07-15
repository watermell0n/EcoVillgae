# 2026-07-16 서비스 흐름 데모 개발 계획

## 1. 오늘의 목표

오늘 목표는 P0 전체 기능 완성이 아니라, 교사와 학생 화면을 동시에 띄워 다음 흐름이 실제 API와 PostgreSQL 데이터로 이어지는 상태를 만드는 것이다.

```text
교사 로그인
→ 학생 참여 코드 입장
→ 학생 대기
→ 교사가 퀴즈 단계로 이동
→ 학생이 1일차 퀴즈 제출
→ 교사가 소득·세금 정산
→ 학생이 월급명세서 확인
→ 교사가 자유 활동으로 이동
→ 학생이 상점에서 상품 1회 구매
→ 1일차 요약 확인
```

전체 1·2일차 단계는 교사 대시보드에서 순서대로 넘겨볼 수 있게 만들되, 오늘 실제 비즈니스 로직을 연결하는 범위는 위의 Day 1 수직 흐름으로 제한한다.

이 계획은 프론트엔드와 백엔드 작업자가 최소 1명씩 있고, 약 8~9시간을 함께 개발한다고 가정한다.

## 2. 기술 기준

- Frontend: Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios
- Backend: Java 21, Spring Boot 4.1.x, Spring MVC, Security, JPA, Flyway
- Database: PostgreSQL 17
- Local infrastructure: Docker Compose
- Synchronization: 2초 주기 `syncVersion` 기반 폴링
- Repository: 프론트·백엔드·인프라를 함께 두는 모노레포

첨부된 기존 폴더 구조 문서의 MySQL, InnoDB, 3306, Testcontainers MySQL 항목은 사용하지 않는다. 각각 PostgreSQL, 5432, Testcontainers PostgreSQL 기준으로 적용한다.

## 3. 오늘 구현 범위

### 실제 연동

- Spring Boot, Vue, PostgreSQL 로컬 실행
- Flyway 초기 스키마와 데모 데이터
- 교사 데모 로그인
- 학생 참여 코드 확인과 최초 설정
- 교사 수업 단계 변경
- 학생·교사 상태 폴링
- 1일차 퀴즈 조회·답안 저장·최종 제출
- 교사 제출 인원 확인
- 역할 소득·퀴즈 보상·소득세 정산
- 학생 지갑과 금융 원장 기록
- 학생 월급명세서
- 상품 목록과 상품 1회 구매
- 구매 시 잔액·재고 동시 차감
- 새로고침 후 현재 단계 복구

### 화면만 연결

- 금융지식 교육
- 예금·투자
- 1일차 마감 결과
- 2일차 정산과 결과
- 공동기금 투표
- 최종 결과

이 화면들은 `현재 단계`, 짧은 안내문, `다음 구현 예정` 상태를 표시한다. 백엔드 상태 머신에는 최종 단계명을 사용해 나중에 화면을 교체할 수 있게 한다.

### 오늘 제외

- 방 생성 설정 마법사
- 관리자 대시보드
- 실제 예금·투자·금융세
- 실제 2일차 정산
- 공동기금 사용과 투표 집계
- 엑셀 내보내기
- EC2 배포 자동화
- 50명 부하 테스트
- 완성형 디자인과 애니메이션

## 4. 오늘 사용할 데모 데이터

Flyway 로컬 시드 또는 `local` 프로필 초기화 코드로 다음만 준비한다.

- 교사 계정 1개: 자격 증명은 환경변수에서 주입
- 데모 방 1개
- 참여 코드 3개
- 1일차 객관식 퀴즈 6문제
- 상품 3개
- 역할 소득 100비빔
- 참여수당 10비빔
- 정답수당 정답당 10비빔
- 소득세율 10%

운영 환경에서는 데모 시드를 실행하지 않는다.

## 5. 먼저 고정할 API 계약

개발 시작 후 30분 안에 요청·응답 예시와 오류 코드를 고정한다. 프론트는 같은 형식의 Mock API를 사용하고, 백엔드가 준비되면 Axios 구현만 실제 API로 전환한다.

| 영역 | 방식 | 경로 |
| --- | --- | --- |
| 교사 인증 | POST | `/api/v1/auth/teacher/login` |
| 학생 코드 확인 | POST | `/api/v1/student/auth/enter` |
| 학생 최초 설정 | POST | `/api/v1/student/auth/setup` |
| 학생 상태 | GET | `/api/v1/student/room-state?syncVersion={value}` |
| 교사 상태 | GET | `/api/v1/teacher/rooms/{roomId}/state?syncVersion={value}` |
| 단계 변경 | POST | `/api/v1/teacher/rooms/{roomId}/commands` |
| 현재 퀴즈 | GET | `/api/v1/student/quizzes/current` |
| 답안 저장 | PUT | `/api/v1/student/quizzes/{quizId}/answers/{questionId}` |
| 퀴즈 제출 | POST | `/api/v1/student/quizzes/{quizId}/submit` |
| 제출 현황 | GET | `/api/v1/teacher/rooms/{roomId}/quiz-summary` |
| 정산 미리보기 | GET | `/api/v1/teacher/rooms/{roomId}/settlements/day-1-income/preview` |
| 소득 정산 | POST | `/api/v1/teacher/rooms/{roomId}/settlements/day-1-income` |
| 월급명세서 | GET | `/api/v1/student/payslips/day-1` |
| 상품 목록 | GET | `/api/v1/student/shop/products` |
| 상품 구매 | POST | `/api/v1/student/shop/purchases` |

상태 변경·정산·구매 요청에는 `Idempotency-Key`를 사용한다. 교사 단계 변경에는 `expectedVersion`을 사용하고, 폴링에는 별도의 `syncVersion`을 사용한다.

## 6. 오늘 생성할 최소 DB 범위

최종 ERD의 테이블명과 컬럼명을 유지하면서 필요한 테이블만 먼저 생성한다.

- `users`
- `rooms`
- `room_rules`
- `students`
- `sessions`
- `module_states`
- `accounts`
- `financial_transactions`
- `financial_entries`
- `quizzes`
- `quiz_questions`
- `quiz_submissions`
- `quiz_answers`
- `settlement_runs`
- `settlement_attempts`
- `settlement_items`
- `products`
- `purchases`

금액은 `BIGINT`, ID는 `UUID`, 시각은 `TIMESTAMPTZ`, 메타데이터는 `JSONB`를 사용한다.

## 7. 프론트엔드 작업 트랙

### 기반

- Vue 프로젝트와 역할별 라우트 생성
- `PublicLayout`, `StudentLayout`, `TeacherLayout` 생성
- Axios 인스턴스와 공통 오류 처리
- Pinia `auth`, `session` 스토어
- `VITE_USE_MOCK` 플래그로 Mock/실제 API 전환

### 학생 화면

- 참여 코드 입력
- 닉네임·비밀번호 최초 설정
- 학생 수업 메인 셸
- 대기 화면
- 퀴즈 화면
- 월급명세서
- 자유 활동 홈
- 상점과 구매 확인창
- 단계별 Placeholder 화면

### 교사 화면

- 데모 로그인
- 수업 진행 대시보드
- 현재 단계·접속 인원·제출 인원
- 이전·다음 단계 버튼
- 정산 미리보기와 실행 확인창

### 프론트 핵심 구조

`stage → component` 매핑을 한 곳에서 관리한다.

```ts
const stageComponents = {
  DAY1_LOBBY: WaitingView,
  DAY1_QUIZ: QuizView,
  DAY1_PAYSLIP_GUIDED: PayslipView,
  DAY1_FREE_ACTIVITY: FreeActivityView,
  DAY1_SUMMARY: Day1SummaryView,
  DAY2_SETTLEMENT_WAIT: PlaceholderView,
  DAY2_FINAL_RESULT: PlaceholderView,
}
```

이 구조를 사용하면 오늘은 Placeholder를 표시하고 이후 실제 기능 컴포넌트만 교체할 수 있다.

## 8. 백엔드 작업 트랙

### 기반

- Spring Boot 프로젝트 생성
- PostgreSQL Docker Compose
- `application-local.yml`과 환경변수 연결
- Flyway 스키마·데모 시드
- 공통 응답과 예외 형식
- Actuator health check

### 인증·세션

- 교사와 학생 보안 쿠키
- 역할과 방 소유권 검사
- 학생 `login_version` 적용
- 방 상태 전이 검증
- `version`과 `sync_version` 분리
- 학생·교사 상태 조회

### 퀴즈·정산

- 현재 퀴즈 조회
- 학생별 답안 저장과 중복 제출 차단
- 교사 제출 현황
- 소득·보상·소득세 계산
- 학생별 정산 트랜잭션
- 지갑·공동기금 원장 생성
- 반복 정산 차단

### 상점

- 상품 목록
- 지갑과 상품 행 `PESSIMISTIC_WRITE` 잠금
- 잔액·재고 검증
- 구매·금융 거래·원장 항목을 한 트랜잭션에서 저장

## 9. 시간대별 병렬 일정

| 시간 | 프론트엔드 | 백엔드 | 공동 체크포인트 |
| --- | --- | --- | --- |
| T+0:00~0:30 | 라우트·DTO·Mock 응답 정의 | API 요청·응답·오류 코드 정의 | API 계약 동결 |
| T+0:30~2:00 | Vue 셸·레이아웃·단계 컴포넌트 | Spring·PostgreSQL·Flyway·시드 | 양쪽 로컬 실행 |
| T+2:00~4:00 | 학생 입장·대기·교사 제어 UI | 인증·상태 조회·단계 명령 | 두 브라우저 단계 동기화 |
| T+4:00~6:00 | 퀴즈·제출 현황·명세서 UI | 퀴즈·원장·소득 정산 | 퀴즈에서 명세서까지 통합 |
| T+6:00~7:30 | 자유 활동·상점·구매 확인 | 상품 조회·구매 트랜잭션 | 실제 잔액·재고 변경 |
| T+7:30~8:30 | 오류·로딩·재연결 처리 | 멱등성·중복 실행·복구 점검 | 전체 흐름 2회 리허설 |
| T+8:30 이후 | 치명적 UI 오류만 수정 | 치명적 API 오류만 수정 | 기능 추가 중단 |

EC2 접근 정보와 도메인이 이미 준비돼 있고 로컬 통합이 T+7:30 이전에 끝난 경우에만 빈 화면 배포를 추가한다. 그렇지 않으면 오늘은 로컬 통합 흐름을 우선한다.

## 10. Git 작업 분리

- API 계약: `docs/api/demo-flow.openapi.yaml`, 통합 담당자 소유
- 프론트: `feature/frontend-demo-flow`, `frontend/**`만 수정
- 백엔드: `feature/backend-demo-flow`, `backend/**`만 수정
- 로컬 인프라: `chore/local-postgres`, `infra/**`만 수정
- 루트 `README.md`와 `.gitignore`는 통합 담당자만 수정

인프라 담당자가 따로 없으면 백엔드 작업자가 `infra/**`를 함께 담당한다. 체크포인트마다 작은 단위로 통합하고, 하루 끝에 한꺼번에 합치지 않는다.

## 11. 통합 순서

1. 공통 API 계약과 오류 코드
2. PostgreSQL·Spring health check
3. Vue 기본 화면
4. 학생 코드 입장
5. 교사 단계 변경과 학생 폴링
6. 퀴즈 조회·제출
7. 소득 정산·명세서
8. 상품 구매
9. 새로고침 복구
10. 전체 흐름 리허설

프론트는 각 단계가 준비될 때마다 Mock을 실제 API로 교체한다. 실제 API가 실패했을 때 Mock으로 조용히 대체하지 말고 오류를 화면에 표시한다.

## 12. 오늘 반드시 작성할 테스트

### 백엔드 자동 테스트

- 잘못된 단계 이동은 `409 STATE_CONFLICT`
- 퀴즈 중복 제출 차단
- 동일 정산 반복 실행 시 중복 지급 없음
- 구매 후 지갑과 재고가 함께 변경
- 잔액 부족 또는 품절 시 전체 롤백
- 동일 구매 `Idempotency-Key` 재요청 시 기존 결과 반환

### 수동 통합 테스트

- 교사와 학생을 서로 다른 브라우저 창에서 실행
- 교사 단계 변경이 2초 안에 학생 화면에 반영
- 학생 새로고침 후 같은 단계로 복귀
- 정산 버튼 두 번 클릭해도 잔액이 한 번만 증가
- 구매 버튼 두 번 클릭해도 구매가 한 번만 생성
- 서버 재시작 후 PostgreSQL 데이터 유지

## 13. 오늘의 완료 조건

- `docker compose`로 PostgreSQL 실행 가능
- Spring Boot와 Vue를 README 명령으로 실행 가능
- 교사·학생 두 화면이 동시에 열림
- 교사 단계 변경이 학생 화면에 반영
- 학생 퀴즈 제출과 교사 제출 현황 확인
- 소득 정산 후 학생 월급명세서 표시
- 상품 구매 후 잔액과 재고 변경
- 반복 정산·중복 구매가 발생하지 않음
- 새로고침 후 현재 단계 복구
- Day 2 단계는 Placeholder로라도 순서대로 확인 가능
- 치명적인 콘솔·서버 오류 없이 전체 흐름 2회 완료

## 14. 일정이 밀릴 때 자르는 순서

1. EC2 배포
2. 디자인 세부 조정
3. 교사 이전 단계 이동
4. 퀴즈 답안 자동 저장
5. 학생 최초 비밀번호 설정을 로컬 데모 계정으로 대체
6. 상품을 3개에서 1개로 축소

다음 항목은 자르지 않는다.

- PostgreSQL 실제 연결
- 교사 단계 변경과 학생 폴링
- 퀴즈 최종 제출
- 소득 정산과 원장 기록
- 상점 구매 1회
- 멱등성 검증

오늘의 성공 기준은 화면 수가 아니라 **실제 데이터가 한 흐름으로 이어지고, 같은 요청을 반복해도 돈과 재고가 중복 변경되지 않는 것**이다.
