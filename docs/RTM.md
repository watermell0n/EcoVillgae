# 이코빌리지 요구사항 추적 매트릭스

- 버전: v1.4
- 기준일: 2026-07-30
- 상태: 요구사항·AI v1.2 사람 검토 계약·논리·물리 설계 연결, 관련 구현·기계 계약 동기화 예정
- 범위: MVP 제품·운영·기술 요구사항

## 1. 사용 방법

RTM v1.3은 확정 요구사항을 출처·역할·모듈·인수 기준과 연결하고 상태 머신 `SM-*`, 권한 규칙 `PM-*`, 화면 `SCR-*`, HTTP API `API-*`, WebSocket 채널·이벤트, 논리 데이터 모델 `DM-*`, 물리 제약·인덱스 `UK-*·CK-*·IX-*`, Flyway `V1_1`~`V1_17`, DB 무결성 테스트 `DBT-*`를 연결합니다. 각 API·채널·이벤트 ID는 OpenAPI·AsyncAPI 기계 판독 계약에 동일하게 수록합니다.

출처 약어:

- `MVP`: [README](../README.md)
- `DEC`: [API 명세 전 결정사항](API_PRE_DESIGN_DECISIONS.md)
- `CRS`: [추천 코스](COURSE_RECOMMENDATIONS.md)
- `AI`: [AI 활용 방향](AI_DIRECTION.md)
- `TECH`: [기술 스택](TECH_STACK.md)
- `DATA`: [요구사항 기준선](REQUIREMENTS_BASELINE.md)
- `SM`: [핵심 상태 머신](STATE_MACHINES.md)
- `PM`: [역할·권한 매트릭스](PERMISSION_MATRIX.md)
- `SCR`: [화면 ID·행동 명세](SCREEN_SPEC.md)
- `API`: [API 공통 규칙](API_COMMON_RULES.md)·[HTTP API 명세](HTTP_API_SPEC.md)
- `WS`: [WebSocket 명세](WEBSOCKET_SPEC.md)
- `OAS`: [OpenAPI 3.1 HTTP 계약](../contracts/openapi.yaml)
- `AAS`: [AsyncAPI 3.1 WebSocket 계약](../contracts/asyncapi.yaml)
- `DM`: [논리 데이터 모델·ERD](DATA_MODEL.md)
- `PDM`: [PostgreSQL 물리 스키마](PHYSICAL_SCHEMA.md)

## 2. 플랫폼·인증

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| SYS-001 | TECH | 학생·교사·관리자 | 학생 앱·교사/관리자 웹 | 학생은 Expo 앱, 교사·관리자는 Next.js 웹에서 역할별 진입 가능 | 빌드·수동 시나리오 | PM-RBAC-001·PM-DSP-001·PM-OPS-001 · SCR-STU-003·SCR-TCH-002·SCR-WEB-002·SCR-ADM-001·SCR-PUB-001 / TEST TBD |
| SYS-002 | TECH | 시스템 | 핵심 API·AI | Spring API와 FastAPI AI가 분리 호출됨 | 통합 테스트 | PM-SVC-001·PM-AI-006 · SCR-OPS-002 / TEST TBD |
| SYS-003 | TECH | 시스템 | 실시간 수업 | 다중 서버에서 동일 수업 상태를 수신하고 거래 최종값은 서버가 결정 | WebSocket 통합 | SM-ACT-001·SM-TXN-001 · PM-SVC-001·PM-DSP-001 · SCR-TCH-005·SCR-STU-021·SCR-PUB-002 / DBT-015 · TEST TBD |
| SYS-004 | TECH | 운영자 | AWS 인프라 | 서울 리전 자원이 Terraform으로 재현됨 | IaC plan·배포 검증 | PM-OPS-002 · SCR-OPS-001 / TEST TBD |
| SYS-005 | TECH | 운영자 | CI/CD·관측 | Jenkins·EAS 배포와 오류·추적·지표 조회 가능 | 파이프라인·장애 주입 | PM-OPS-002 · SCR-OPS-001 / TEST TBD |
| AUTH-001 | MVP·DEC | 학생 | 로그인 | 실명 없이 학급 코드·번호·닉네임·비밀번호로 로그인 | 인증 E2E | PM-AUTH-001 · SCR-STU-001 / DBT-003 · TEST TBD |
| AUTH-002 | DEC | 전 역할·기술 주체 | 권한 | STU·OWN·COT·SAD·POP·SYS·AIS·DSP가 허용 작업만 수행 | 권한 매트릭스 테스트 | PM-RBAC-001·PM-RBAC-002·PM-CLS-002·PM-CLS-003·PM-OPS-001·PM-OPS-002·PM-SVC-001·PM-DSP-001 · SCR-STU-003·SCR-TCH-002·SCR-WEB-002·SCR-WEB-007·SCR-ADM-001·SCR-ADM-002·SCR-ADM-004·SCR-OPS-001·SCR-OPS-002·SCR-PUB-001 / DBT-001·DBT-002 · TEST TBD |
| AUTH-003 | TECH·DEC | 학생 | 세션·토큰 | SecureStore 저장, 다른 태블릿 로그인 시 기존 학생 세션 종료 | 학생 기기 전환 시나리오 | PM-AUTH-001·PM-SVC-001 · SCR-STU-001·SCR-ADM-003 / API-CMN-001·API-CMN-002 · DBT-004 · TEST TBD |
| AUTH-004 | MVP·DEC | 학생·교사 | 개인정보 | 개인 금융·평가 결과가 타 학생·공용 화면에 노출되지 않음 | 접근 제어·화면 검사 | PM-DAT-001·PM-DSP-001 · SCR-STU-004·SCR-STU-020·SCR-PUB-004 / DBT-016 · TEST TBD |
| AUTH-005 | MVP·DEC | 학생 | 로그인 실패 | 연속 실패 후에도 계정 잠금 상태가 생성되지 않음 | 인증 경계값 테스트 | PM-AUTH-001 · SCR-STU-001 / TEST TBD |
| AUTH-006 | MVP·DEC | 학생·교사 | 비밀번호 재설정 | 기존 본인 확인 흐름으로 비밀번호만 재설정되고 닉네임 재설정 경로는 없음 | 인증 E2E | PM-AUTH-002·PM-CLS-004 · SCR-STU-002·SCR-WEB-006 / TEST TBD |
| AUTH-007 | DEC | 교사·학생·시스템 | 학급 코드 | 서버가 혼동 문자를 제외한 8자 코드를 전역 유일하게 생성하고 로그인에서 정규화하며 생성 후 변경하지 않음 | 생성·충돌·로그인 테스트 | SM-CLS-001 · PM-CLS-001 · SCR-WEB-003·SCR-WEB-004·SCR-STU-001 / API-CLS-001·API-AUTH-001 · DBT-018 · TEST TBD |
| AUTH-008 | MVP·TECH·DEC | 교사 | 로그인·세션 | OIDC ID 토큰의 서명·issuer·audience·만료와 등록 교사 상태를 검증하고, 교사 웹의 허용된 복수 브라우저 세션과 회전형 갱신 토큰을 지원 | 인증 API 통합·토큰 재사용 거절 테스트 | PM-AUTH-003·PM-RBAC-001 · SCR-TCH-001·SCR-WEB-001 / API-AUTH-004·API-AUTH-005·API-AUTH-006·API-AUTH-008 · TEST `TeacherSessionApiTest` |

## 3. 경제교실·코스

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| CLS-001 | MVP·DEC | 교사 | 경제교실 생성 | 학년·규모·기간·인원·통화명과 0개 이상의 선택 모듈·자율시간 저장, 역할·급여 미선택 허용 | 생성 E2E | SM-CLS-001·SM-MOD-001 · PM-CLS-001·PM-CLS-005·PM-CLS-007 · SCR-WEB-003 / DBT-002 · TEST TBD |
| CLS-002 | DEC | 교사 | 모듈 상태 | 추가 가능, 중지는 활성 데이터별 정산 규칙 적용 | 상태 전이 테스트 | SM-MOD-001 · PM-MOD-001 · SCR-WEB-005 / TEST TBD |
| CLS-003 | MVP·DEC | 학생 | 앱 운영 상태 | CLOSED·AUTONOMOUS·GUIDED별 허용 화면이 다름 | 상태별 UI 테스트 | SM-APP-001 · PM-CLS-008·PM-RBAC-002 · SCR-STU-003 / TEST TBD |
| CLS-004 | DEC | 교사·시스템 | 상태 우선순위 | GUIDED가 수동·반복 일정에 우선함 | 우선순위 테스트 | SM-APP-001 · PM-CLS-007·PM-CLS-008·PM-ACT-001 · SCR-STU-003·SCR-TCH-003 / TEST TBD |
| CLS-005 | DEC | 학생·교사 | 자율시간 | 종료 5분·1분 알림과 연장 동작 | 시간 제어 테스트 | SM-APP-001 · PM-CLS-007·PM-CLS-008 · SCR-STU-003·SCR-TCH-003·SCR-WEB-008 / TEST TBD |
| CLS-006 | DEC | 시스템 | 종료 경계 거래 | 접수 거래 완료, 미제출 초안 미실행 | 동시성 테스트 | SM-APP-001·SM-TXN-001 · PM-CLS-008·PM-TXN-001 · SCR-STU-003·SCR-TCH-003 / TEST TBD |
| CLS-007 | DEC | 교사 | 교실 종료 | ACTIVE→CLOSING→READ_ONLY→ARCHIVED 순서만 허용 | 상태 머신 테스트 | SM-CLS-001 · PM-CLS-006 · SCR-WEB-026 / DBT-017 · TEST TBD |
| CLS-008 | DEC | 교사·시스템 | 종료 정산 | 미완료 항목 해소 후 최종 스냅샷 생성 | 종료 E2E | SM-CLS-001 · PM-CLS-006·PM-FIN-009 · SCR-WEB-026·SCR-STU-024 / TEST TBD |
| CLS-009 | MVP·DEC | 학생·교사 | 지도 활동·공용 화면 | 시작 시 학생은 활동 전용 화면으로 이동하고 공용 화면에는 공개 정보만 표시 | 실시간 화면 E2E | SM-ACT-001·SM-APP-001 · PM-ACT-001·PM-DSP-001 · SCR-TCH-004·SCR-TCH-007·SCR-STU-021·SCR-WEB-028·SCR-PUB-002 / TEST TBD |
| CRS-001 | MVP·CRS | 교사 | 규모 라벨 | 라벨과 실제 날짜·활동 횟수가 독립적으로 저장됨 | 설정 테스트 | PM-CLS-005 · SCR-WEB-004 / TEST TBD |
| CRS-002 | MVP·CRS | 학생·교사 | 1회 코스 | 퀴즈·게임만 기본 추천하되 교사가 선택한 모듈은 실제 일정·예산 검증 후 사용할 수 있고 미선택 모듈은 생성·노출되지 않음 | 코스·모듈 조합 E2E | PM-CLS-005·PM-MOD-001·PM-RBAC-002 · SCR-WEB-004·SCR-WEB-005·SCR-STU-003 / TEST TBD |
| CRS-003 | MVP·CRS | 교사 | 추천표 | 12개 조합과 데이터 버전을 조회할 수 있음 | 데이터 검증 | PM-CLS-005 · SCR-WEB-004 / API-CRS-001 · `CourseRecommendationSeedTest` |
| CRS-004 | CRS | 학생·교사 | 난이도 | 학년별 문장·숫자·계산·리포트 프로필 적용 | 콘텐츠 검수 | PM-CLS-005·PM-SVC-001 · SCR-WEB-004·SCR-STU-021·SCR-STU-022·SCR-STU-024 / TEST TBD |
| CRS-005 | CRS | 교사 | 추천 수정 | 추가·제외 시 선수 모듈과 학습 영향 표시 | 추천 UI 테스트 | PM-CLS-005·PM-MOD-001 · SCR-WEB-004·SCR-WEB-005 / TEST TBD |
| CRS-006 | MVP·CRS | 학생 | 모듈 노출 | 미선택 모듈 경로·메뉴·명령 접근 차단 | 권한·UI 테스트 | SM-MOD-001 · PM-MOD-001·PM-RBAC-002 · SCR-STU-003·SCR-WEB-005 / TEST TBD |
| CLS-010 | DEC | 소유 교사·학생 | 학급 화폐 단위명 | OWN이 개설 시 한글·영문·숫자 1~10자 단위명을 필수 입력하고 금칙어를 거절하며 생성 후 모든 역할의 변경을 차단 | 생성·형식·권한·불변성 테스트 | SM-CLS-001 · PM-CLS-001·PM-CLS-005 · SCR-WEB-003·SCR-WEB-004·SCR-STU-003 / API-CLS-001·API-READ-002·API-READ-003 · DBT-018 · TEST TBD |

## 4. 경제·개인거래

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| ECO-001 | MVP·DEC | 시스템 | 지갑·원장 | 모든 금액·아이템 변경에 원장 항목 존재 | 원장 불변식 테스트 | SM-TXN-001 · PM-TXN-001·PM-SVC-001 · SCR-STU-004·SCR-WEB-012 / DBT-005·DBT-006·DBT-007 · TEST TBD |
| ECO-002 | MVP·DEC | 교사·시스템 | 복구 거래 | 완료 거래 수정·삭제 불가, 원거래 참조 복구만 가능 | API·DB 제약 테스트 | SM-TXN-001 · PM-TXN-003 · SCR-WEB-012 / DBT-009 · TEST TBD |
| ECO-003 | DEC | 시스템 | 정책 스냅샷 | 거래 상세에서 당시 가격·세율·금리·실행자 조회 | 감사 조회 테스트 | SM-TXN-001 · PM-TXN-001·PM-DAT-005 · SCR-STU-004·SCR-WEB-012·SCR-WEB-025 / DBT-008 · TEST TBD |
| ECO-004 | MVP | 교사·학생 | 선택형 급여·세금 | 역할·급여 활성화 시 역할급여·명세서가 일치하고 소득세 활성화 시 실수령액이 일치하며, 역할 모듈 미선택 시 직업·급여 없이 이용 | 모듈 조합·계산 단위 테스트 | SM-MOD-001·SM-TXN-001 · PM-MOD-001·PM-POL-001·PM-TXN-002 · SCR-STU-005·SCR-TCH-011·SCR-WEB-005·SCR-WEB-006 / TEST TBD |
| ECO-005 | MVP | 교사·학생 | 상점 | 디지털·실제 상품 구매와 상품별 부가세 계산 | 구매 E2E | SM-SHP-001·SM-TXN-001 · PM-SHP-001·PM-SHP-002·PM-SHP-003 · SCR-STU-006·SCR-STU-007·SCR-WEB-011·SCR-TCH-010 / DBT-012·DBT-013 · TEST TBD |
| ECO-006 | MVP·DEC | 학생·교사 | 환불·복구 | 학생 환불 경로 없음, 운영 오류는 복구 거래 | 권한·복구 테스트 | SM-SHP-001·SM-TXN-001 · PM-SHP-002·PM-TXN-003 · SCR-STU-007·SCR-WEB-012 / TEST TBD |
| ECO-007 | MVP | 학생 | 캐릭터 | 구매·보유·장착 상태와 소유권 일치 | 아이템 E2E | SM-ITM-001 · PM-CNT-001·PM-ITM-001·PM-SHP-001·PM-SHP-002 · SCR-STU-008·SCR-WEB-011 / TEST TBD |
| ECO-008 | MVP·DEC | 학생·교사 | 공동기금·투표 | 기금 잔액·예산·익명 투표·사용내역 일치 | 투표·원장 테스트 | SM-ACT-001·SM-TXN-001 · PM-POL-002·PM-ACT-001·PM-ACT-002 · SCR-STU-012·SCR-TCH-006·SCR-WEB-010·SCR-PUB-003 / DBT-014 · `VoteTest`·`VoteCommandServiceTest`·`VoteBallotServiceTest`·`VoteQueryServiceTest`·`LedgerAccountTest` |
| ECO-009 | MVP·DEC | 교사 | 세금 사용처 | 기본 사용처가 제공되고 이름·설명·예산 항목 수정 가능 | 설정 E2E | PM-POL-002 · SCR-WEB-010 / TEST TBD |
| ECO-010 | MVP·DEC | 운영자 | 캐릭터 원작 | 배포 지역 퍼블릭 도메인·출처·권리 검토일이 등록된 원작만 게시 가능 | 콘텐츠 승인 테스트 | PM-CNT-001 · SCR-OPS-003·SCR-WEB-011 / TEST TBD |
| TRD-001 | MVP·DEC | 학생·교사 | 개인거래 | 제안·동의·승인 전에는 자산 이전 없음 | 상태 머신 E2E | SM-TRD-001 · PM-TRD-001·PM-TRD-002·PM-TRD-003 · SCR-STU-009·SCR-STU-010·SCR-STU-011·SCR-TCH-008·SCR-TCH-009 / TEST TBD |
| TRD-002 | DEC | 학생·시스템 | 아이템 잠금 | 제안 중 중복 사용 차단, 취소·실패 시 해제 | 동시성 테스트 | SM-TRD-001·SM-ITM-001 · PM-ITM-001·PM-TRD-001·PM-TRD-002 · SCR-STU-008·SCR-STU-010·SCR-STU-011·SCR-WEB-013 / DBT-011 · TEST TBD |
| TRD-003 | DEC | 시스템 | 거래세·이전 | 금액·세금·아이템이 모두 성공하거나 모두 실패 | 트랜잭션 테스트 | SM-TRD-001·SM-TXN-001 · PM-TRD-003·PM-TXN-001 · SCR-STU-011·SCR-TCH-009·SCR-WEB-012 / TEST TBD |
| TRD-004 | DEC | 교사 | 거래소 중지 | 미완료 등록 취소와 아이템 잠금 해제 | 모듈 중지 E2E | SM-MOD-001·SM-TRD-001·SM-ITM-001 · PM-MOD-001·PM-TRD-003 · SCR-WEB-005·SCR-WEB-013 / TEST TBD |
| TRD-005 | MVP·DEC | 시스템 | 중복 방지 | 동일 요청번호 재시도 시 한 번만 반영 | 멱등성 테스트 | SM-TRD-001·SM-TXN-001 · PM-TRD-003·PM-TXN-001 · SCR-STU-010·SCR-WEB-012 / DBT-010 · TEST TBD |

## 5. 금융

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| FIN-001 | MVP·DEC | 학생·교사 | 정기예금 | 가입 금리·만기·해지 정책이 이후 변경에도 유지 | 금융 계산·스냅샷 테스트 | SM-DEP-001·SM-TXN-001 · PM-FIN-001·PM-FIN-002 · SCR-STU-013·SCR-STU-014·SCR-WEB-014 / DBT-008 · TEST TBD |
| FIN-002 | DEC | 교사·학생 | 정기적금 일정 | 2~12회 일정·유예기간·자동납입 설정 가능 | 상품 생성·스케줄 테스트 | SM-SAV-001·SM-SAV-002 · PM-FIN-001·PM-FIN-002 · SCR-STU-015·SCR-WEB-014 / TEST TBD |
| FIN-003 | DEC | 학생·시스템 | 적금 미납 | 잔액 부족 시 부분 출금 없이 MISSED, 유예 재납입 | 경계값·스케줄 테스트 | SM-SAV-002·SM-TXN-001 · PM-FIN-002·PM-SVC-001 · SCR-STU-015 / TEST TBD |
| FIN-004 | DEC | 학생·시스템 | 적금 이자·종료 | 회차별 이자와 만기·중도해지 지급이 원장과 일치 | 계산·정산 테스트 | SM-SAV-001·SM-SAV-002·SM-TXN-001 · PM-FIN-002·PM-TXN-001 · SCR-STU-015·SCR-WEB-012 / TEST TBD |
| FIN-005 | DEC | 교사·학생 | 가상 투자 자산 | 가상 자산 4종과 재현 가능한 가격 단계 제공 | 시드 재현 테스트 | SM-IVT-001 · PM-FIN-001·PM-FIN-003·PM-FIN-004 · SCR-STU-016·SCR-WEB-015 / TEST TBD |
| FIN-006 | DEC | 학생 | 투자 주문 | 현재가·정수·잔액·보유량 제한, 금지 주문 접근 불가 | 주문 E2E·경계 테스트 | SM-IVT-002·SM-TXN-001 · PM-FIN-003 · SCR-STU-017 / TEST TBD |
| FIN-007 | DEC | 학생·교사 | 투자 안전·정산 | 한도·손실상한·선 그래프·PAUSED·CLOSED 정산 동작 | 시나리오 E2E | SM-IVT-001·SM-IVT-002 · PM-FIN-001·PM-FIN-003·PM-FIN-004 · SCR-STU-016·SCR-STU-018·SCR-WEB-015 / TEST TBD |
| FIN-008 | DEC | 학생·교사 | 대출 신청·실행 | 교사 승인 후 원금 지급과 계좌 생성이 원자적 | 승인 E2E | SM-LON-001·SM-TXN-001 · PM-FIN-001·PM-FIN-005·PM-FIN-006 · SCR-STU-019·SCR-TCH-009·SCR-WEB-016 / TEST TBD |
| FIN-009 | DEC | 학생·시스템 | 대출 상환·연체 | 잔액 부족 시 OVERDUE, 유예 상환과 음수 잔액 방지 | 스케줄·경계 테스트 | SM-LON-001·SM-TXN-001 · PM-FIN-007·PM-SVC-001 · SCR-STU-019 / `LoanWorkflowApiTest` |
| FIN-010 | DEC | 학생·교사 | 신용 | 네 단계 전이·금리 조정·신규 대출 차단·비공개 적용 | 상태·권한 테스트 | SM-LON-001·SM-CRD-001 · PM-FIN-006·PM-FIN-008 · SCR-STU-020·SCR-TCH-009·SCR-WEB-016 / `LoanWorkflowApiTest`, `LoanCreditPolicyTest` |
| FIN-011 | DEC·AI | 교사·시스템 | 정책 변경 | 세율은 이후 거래, 금리는 신규 금융상품에만 적용 | 정책 버전 테스트 | SM-TXN-001·SM-AIR-001 · PM-POL-001·PM-FIN-001·PM-AI-004 · SCR-WEB-009·SCR-WEB-014·SCR-WEB-016·SCR-WEB-021 / TEST TBD |
| FIN-012 | DEC | 교사·시스템 | 교실 종료 금융 정산 | 예적금·투자·대출 종료 결과와 최종 스냅샷 일치 | 종료 통합 테스트 | SM-CLS-001·SM-DEP-001·SM-SAV-001·SM-IVT-001·SM-LON-001 · PM-CLS-006·PM-FIN-009 · SCR-WEB-026·SCR-STU-024 / TEST TBD |

## 6. 퀴즈

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| QZ-001 | MVP·DEC | 교사 | 분류 | 콘텐츠 유형과 실행 모드를 독립 조합 가능 | 모델·API 테스트 | SM-QST-001·SM-QZS-001 · PM-QZ-001·PM-QZ-002 · SCR-WEB-017·SCR-WEB-018·SCR-WEB-020 / TEST TBD |
| QZ-002 | MVP·DEC | 교사·학생 | 문제은행 권한 | 학생은 생성·수정·승인 API에 접근 불가 | 권한 테스트 | SM-QST-001 · PM-QZ-001 · SCR-WEB-017·SCR-WEB-018 / TEST TBD |
| QZ-003 | MVP·DEC | 학생·교사 | GAME | 실시간 진행·정답·속도 점수와 교사 선택 시 1~3위 닉네임·장착 캐릭터 공개 | 실시간 E2E | SM-QZS-001 · PM-QZ-002·PM-DSP-001 · SCR-STU-021·SCR-TCH-005·SCR-PUB-002·SCR-PUB-004 / DBT-016 · TEST TBD |
| QZ-004 | DEC | 학생·교사 | TEST | 비공개 결과, 순위·속도·화폐 보상 없음 | 평가 E2E | SM-QZS-001 · PM-QZ-002·PM-QZ-004 · SCR-STU-022·SCR-STU-023·SCR-WEB-019 / DBT-016 · TEST TBD |
| QZ-005 | DEC | 학생·교사 | 넌센스·전체 보상 | 넌센스는 조정된 경제 보상의 30% 이하, 모든 보상 합계는 보상 포함 전체 발행액의 30% 이하이며 예상 화폐량 표시 | 계산·UI 테스트 | SM-QZS-001·SM-TXN-001 · PM-QZ-002·PM-TXN-002 · SCR-WEB-018·SCR-TCH-005·SCR-STU-023 / TEST TBD |
| QZ-006 | DEC·AI | 교사 | AI 문제 초안 | 객관식·OX만 REVIEW_REQUIRED로 생성 | AI 계약 테스트 | SM-QST-001·SM-AIR-001 · PM-QZ-001·PM-AI-002 · SCR-WEB-018 / TEST TBD |
| QZ-007 | DEC | 교사 | 콘텐츠 안전 | 넌센스 보상 상한 위반 차단과 부적절 표현 검사 | 정책 테스트 | SM-QST-001·SM-AIR-001 · PM-QZ-001·PM-AI-002 · SCR-WEB-018 / TEST TBD |
| QZ-008 | DEC | 교사·시스템 | 문제 버전 | 게시 수정 시 새 버전, 세션은 기존 스냅샷 유지 | 버전 테스트 | SM-QST-001·SM-QZS-001 · PM-QZ-001 · SCR-WEB-017·SCR-WEB-019 / TEST TBD |
| QZ-009 | DEC | 교사·시스템 | 무효 문항 | INVALIDATED 재채점과 보상 복구가 전 학생에 동일 | 재채점 E2E | SM-QAD-001·SM-QZS-001·SM-TXN-001 · PM-QZ-002·PM-QZ-003·PM-TXN-003 · SCR-STU-023·SCR-WEB-019·SCR-WEB-012 / TEST TBD |

## 7. AI

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| AI-001 | MVP·AI | 교사 | AI 기능 | 밸런싱·챗봇·이벤트·퀴즈 초안 메뉴와 끄기 제공 | 기능·권한 테스트 | SM-AIR-001 · PM-AI-001·PM-AI-002·PM-AI-003·PM-AI-005 · SCR-WEB-018·SCR-WEB-021·SCR-WEB-022·SCR-WEB-023 / TEST TBD |
| AI-002 | AI·DEC | 시스템·AI | 책임 경계 | 금액 계산은 핵심 API, AI 출력은 설명·초안만 포함 | 계약·보안 테스트 | SM-AIR-001·SM-TXN-001 · PM-SVC-001·PM-AI-002 · SCR-WEB-018·SCR-WEB-021·SCR-WEB-023·SCR-OPS-002 / TEST TBD |
| AI-003 | AI·DEC | 교사 | 밸런싱 제안 | 허용 조정값·근거·영향 표시 후 승인 시에만 적용 | 승인 E2E | SM-AIR-001 · PM-AI-003·PM-AI-004 · SCR-WEB-021 / TEST TBD |
| AI-004 | AI·DEC | 교사 | 데이터 충분성 | 학생 5명 미만 또는 관련 거래 10건 미만이면 추천 대신 데이터 부족 표시 | 경계값 테스트 | SM-AIR-001 · PM-AI-001·PM-SVC-001 · SCR-WEB-021 / TEST TBD |
| AI-005 | AI·DEC | 교사 | 챗봇 조회 | 학급 집계만 답하고 학생 개인 분석·금융조언 차단 | 안전 평가 세트 | SM-AIR-001 · PM-AI-001·PM-DAT-001 · SCR-WEB-022 / TEST TBD |
| AI-006 | AI·DEC | 교사 | 챗봇 운영 | 근거 형식, 30회·20초·1회 재시도와 장애 대체 화면 | 부하·장애 테스트 | SM-AIR-001 · PM-AI-001·PM-OPS-002 · SCR-WEB-022·SCR-OPS-001 / TEST TBD |
| AI-007 | AI·DEC | 교사·학생 | AI 이벤트 | 7종 템플릿·학년 제한·교사 승인·금지 효과 검증 | 이벤트 계약·E2E | SM-AIR-001·SM-ACT-001 · PM-AI-002·PM-ACT-001·PM-DSP-001 · SCR-WEB-023·SCR-TCH-006·SCR-PUB-003 / TEST TBD |
| AI-008 | AI·DEC | 교사·운영자 | AI 감사 | 실행 ID·모델·프롬프트·검증·교사 결정 추적 가능 | 감사 조회 테스트 | SM-AIR-001 · PM-AI-001·PM-AI-002·PM-DAT-005 · SCR-WEB-025·SCR-ADM-005 / TEST TBD |
| AI-009 | AI·DEC | 교사 | 경제 설정 추천·시작 점검 | 개설 시 공통값만 점검하고 역할·급여·소득세·예금·대출 활성화 시 해당 정책, 상품 시 가격, GAME 퀴즈 시 보상을 추천하며 선택한 모듈의 첫 관련 활동 전 필수값·추천·허용 범위를 검사 | 모듈 조합·규칙 경계값·화면 흐름 테스트 | SM-AIR-001·SM-APP-001·SM-MOD-001 · PM-AI-003·PM-AI-004·PM-MOD-001·PM-POL-001 · SCR-WEB-003·SCR-WEB-005·SCR-WEB-009·SCR-WEB-011·SCR-WEB-018·SCR-WEB-021 / TEST TBD |
| AI-010 | AI·DEC | 교사·시스템 | 자동 모니터링·알림 | 동일 길이 구간 자동 집계, OBSERVE·WARN 제안, 중복 억제·교사 확인, 자동 적용 금지 | 스케줄·중복·승인 E2E | SM-AIR-001 · PM-AI-003·PM-AI-004·PM-DAT-001 · SCR-WEB-002·SCR-WEB-021·SCR-TCH-012 / TEST TBD |
| AI-011 | AI·DEC | 교사·학생 | 이벤트 경로·투자 뉴스 | 교사 요청과 모니터링 추천을 구분하고 승인된 투자 뉴스만 연결된 자산·가격 단계에서 공개 | 이벤트 출처·미래정보 비공개 E2E | SM-AIR-001·SM-ACT-001·SM-IVT-001 · PM-AI-002·PM-FIN-003·PM-DSP-001 · SCR-WEB-015·SCR-WEB-023·SCR-STU-016 / TEST TBD |

## 8. 데이터·운영

| 요구사항 ID | 출처 | 역할 | 대상 | 핵심 인수 기준 | 검증 방법 | 설계·테스트 연결 |
| --- | --- | --- | --- | --- | --- | --- |
| DAT-001 | DEC | 학생·교사·관리자 | 종료 후 조회 | READ_ONLY 전환 후 학생 30일, 교사·학교 관리자 1년 권한 적용 | 시간·권한 테스트 | SM-CLS-001 · PM-DAT-001 · SCR-STU-024·SCR-WEB-024·SCR-ADM-005 / TEST TBD |
| DAT-002 | DEC | 교사·시스템 | 새 학급 | 학생 자산·금융·거래·학습데이터 자동 이월 없음 | 복제 E2E | PM-DAT-004·PM-SVC-001 · SCR-WEB-027 / TEST TBD |
| DAT-003 | DEC | 학생·교사·관리자 | 복사·내보내기 | 학생 개인 리포트, 교사 PDF·CSV, 템플릿·퀴즈 복사 | 내보내기 테스트 | PM-DAT-002·PM-DAT-003·PM-DAT-004 · SCR-STU-024·SCR-TCH-012·SCR-WEB-024·SCR-WEB-027 / TEST TBD |
| DAT-004 | DEC | 관리자·시스템 | 보관·삭제 | READ_ONLY 전환 1년 후 식별 연결 삭제·감사 기록 생성, 삭제 후 백업 잔존 최대 30일 | 보존 배치 테스트 | SM-CLS-001 · PM-DAT-005·PM-DAT-006 · SCR-WEB-025·SCR-ADM-005 / TEST TBD |
| DAT-005 | AI·DEC | 교사·시스템 | AI·기기 데이터 | AI 원문은 생성 후 30일, 기기 연결은 READ_ONLY 전환 후 30일, 메타데이터는 AI 실행 후 1년 만료 | TTL·삭제 테스트 | PM-DAT-006·PM-AI-001·PM-SVC-001 · SCR-WEB-025·SCR-ADM-003·SCR-OPS-001 / TEST TBD |
| DAT-006 | MVP·DEC | 학생·시스템 | 온라인 명령 | 오프라인 변경 차단, 재연결 조회, 요청번호 멱등성 | 네트워크·재시도 테스트 | SM-TXN-001 · PM-TXN-001·PM-SVC-001 · SCR-STU-003·SCR-STU-007·SCR-STU-010·SCR-STU-017·SCR-STU-019·SCR-OPS-001 / DBT-010·DBT-015 · TEST TBD |

## 9. HTTP API 역추적

상세 요청·응답·오류는 [HTTP API 명세](HTTP_API_SPEC.md)를 기준으로 하며 구현 도구는 [OpenAPI 3.1 계약](../contracts/openapi.yaml)을 사용합니다. 아래 표는 요구사항에서 API를 찾기 위한 역방향 인덱스입니다.

| 요구사항 | HTTP·공통 API ID |
| --- | --- |
| AI-001 | API-ADM-004·API-AI-001·API-AI-002·API-AI-004·API-AI-005·API-AI-006·API-QZ-002 |
| AI-002 | API-AI-003·API-AI-006 |
| AI-003 | API-AI-002·API-AI-003·API-POL-001·API-READ-027 |
| AI-004 | API-AI-002 |
| AI-005 | API-AI-001·API-READ-027 |
| AI-006 | API-AI-001·API-OPS-001·API-READ-033 |
| AI-007 | API-AI-004·API-AI-006 |
| AI-008 | API-ADM-004·API-OPS-001·API-READ-027·API-READ-029 |
| AI-009 | API-AI-002·API-AI-003·API-CLS-007·API-POL-001·API-SHP-001·API-QZ-004·API-READ-027 |
| AI-010 | API-AI-002·API-AI-003·API-READ-026·API-READ-027 |
| AI-011 | API-AI-004·API-AI-006·API-FIN-007·API-READ-016 |
| AUTH-001 | API-AUTH-001 |
| AUTH-002 | API-ADM-001·API-ADM-003·API-AUTH-008·API-CLS-004·API-CLS-005·API-OPS-002·API-READ-001·API-READ-005·API-READ-031 |
| AUTH-003 | API-ADM-002·API-AUTH-001·API-READ-032 |
| AUTH-004 | API-DSP-001·API-READ-025 |
| AUTH-005 | API-AUTH-001 |
| AUTH-006 | API-AUTH-002·API-AUTH-003 |
| AUTH-007 | API-AUTH-001·API-CLS-001·API-READ-002·API-READ-003 |
| AUTH-008 | API-AUTH-004·API-AUTH-005·API-AUTH-006·API-AUTH-008 |
| CLS-001 | API-CLS-001·API-CLS-003·API-CLS-009·API-READ-002 |
| CLS-002 | API-MOD-001·API-MOD-002·API-READ-006 |
| CLS-003 | API-CLS-007 |
| CLS-004 | API-CLS-006·API-CLS-007 |
| CLS-005 | API-CLS-006·API-CLS-007 |
| CLS-006 | API-CLS-007 |
| CLS-007 | API-CLS-008 |
| CLS-008 | API-CLS-008 |
| CLS-009 | API-ACT-001·API-ACT-002·API-ACT-003·API-ACT-004·API-DSP-001·API-READ-024·API-READ-025 |
| CLS-010 | API-CLS-001·API-READ-002·API-READ-003 |
| CRS-001 | API-CLS-001·API-CLS-002·API-READ-002 |
| CRS-002 | API-CLS-001 |
| CRS-003 | API-CLS-002·API-READ-003 |
| CRS-004 | API-CLS-002·API-READ-003 |
| CRS-005 | API-CLS-002·API-MOD-001·API-READ-003 |
| CRS-006 | API-MOD-001·API-READ-006 |
| DAT-001 | API-DAT-001·API-READ-028 |
| DAT-002 | API-DAT-002 |
| DAT-003 | API-DAT-001·API-DAT-002·API-READ-026·API-READ-028·API-READ-030 |
| DAT-004 | API-READ-029 |
| DAT-005 | API-ADM-002·API-READ-032 |
| DAT-006 | API-CMN-008·API-READ-001·API-READ-030 |
| ECO-001 | API-READ-007·API-READ-009·API-SHP-002·API-TXN-001 |
| ECO-002 | API-READ-009·API-TXN-002 |
| ECO-003 | API-READ-007·API-READ-009 |
| ECO-004 | API-CLS-003·API-READ-004·API-READ-008·API-TXN-001 |
| ECO-005 | API-READ-010·API-READ-011·API-SHP-001·API-SHP-002·API-SHP-003·API-SHP-004 |
| ECO-006 | API-READ-011·API-SHP-002·API-TXN-002 |
| ECO-007 | API-CNT-002·API-ITM-001·API-ITM-002·API-READ-012·API-READ-035·API-SHP-001 |
| ECO-008 | API-POL-002·API-READ-023·API-VOT-001·API-VOT-002 |
| ECO-009 | API-POL-002 |
| ECO-010 | API-CNT-001·API-CNT-002 |
| FIN-001 | API-FIN-001·API-FIN-002·API-FIN-003·API-READ-014·API-READ-015 |
| FIN-002 | API-FIN-001·API-FIN-004·API-READ-014·API-READ-015 |
| FIN-003 | API-FIN-005·API-READ-015 |
| FIN-004 | API-FIN-004·API-FIN-005·API-FIN-006·API-READ-015 |
| FIN-005 | API-FIN-007·API-FIN-008·API-READ-016 |
| FIN-006 | API-FIN-009·API-READ-017 |
| FIN-007 | API-FIN-007·API-FIN-008·API-FIN-009·API-READ-016·API-READ-017 |
| FIN-008 | API-FIN-010·API-FIN-011·API-FIN-012·API-READ-014·API-READ-018·API-READ-019 |
| FIN-009 | API-FIN-010·API-FIN-013·API-READ-018 |
| FIN-010 | API-FIN-010·API-FIN-011·API-FIN-012·API-FIN-013·API-READ-018·API-READ-019 |
| FIN-011 | API-AI-003·API-FIN-001·API-FIN-010·API-POL-001·API-READ-006·API-SHP-005 |
| FIN-012 | API-CLS-008·API-FIN-008 |
| QZ-001 | API-QZ-001·API-QZ-004·API-QZ-010·API-READ-020 |
| QZ-002 | API-QZ-001·API-QZ-003·API-READ-020 |
| QZ-003 | API-QZ-004·API-QZ-005·API-QZ-007·API-READ-021·API-READ-022·API-READ-025 |
| QZ-004 | API-QZ-004·API-QZ-006·API-QZ-007·API-READ-021·API-READ-022 |
| QZ-005 | API-QZ-004·API-QZ-007 |
| QZ-006 | API-QZ-002·API-READ-020 |
| QZ-007 | API-QZ-001·API-QZ-002·API-QZ-003 |
| QZ-008 | API-QZ-001·API-QZ-003·API-READ-020 |
| QZ-009 | API-QZ-008·API-QZ-009·API-READ-022·API-TXN-002 |
| SYS-001 | API-READ-001 |
| SYS-002 | API-OPS-001·API-OPS-002·API-READ-033·API-READ-034 |
| SYS-003 | API-AUTH-007 |
| SYS-005 | API-READ-030 |
| TRD-001 | API-READ-013·API-TRD-001·API-TRD-002·API-TRD-003·API-TRD-004 |
| TRD-002 | API-ITM-001·API-READ-012·API-TRD-001·API-TRD-002·API-TRD-003·API-TRD-004 |
| TRD-003 | API-TRD-004 |
| TRD-004 | API-MOD-002·API-READ-013 |
| TRD-005 | API-TRD-001·API-TRD-004 |

`SYS-003`은 WebSocket 접속 티켓 HTTP API와 실시간 계약에 함께 연결합니다. `SYS-004`는 Terraform 인프라 계약이므로 HTTP API 연결 대상이 아닙니다.

## 10. WebSocket 역추적

상세 채널 권한·payload·순서·재연결은 [WebSocket 명세](WEBSOCKET_SPEC.md)를 기준으로 하며 구현 도구는 [AsyncAPI 3.1 계약](../contracts/asyncapi.yaml)을 사용합니다.

| 요구사항 | WebSocket 채널·이벤트 ID |
| --- | --- |
| AI-003 | WS-CLS-001·WS-TCH-001·EVT-POL-001 |
| AI-006 | WS-OPS-001·EVT-OPS-001·EVT-OPS-002·EVT-OPS-003 |
| AI-007 | WS-ACT-001·WS-ECO-001·WS-DSP-001·EVT-ACT-007·EVT-DSP-001 |
| AUTH-003 | WS-STU-001·EVT-AUTH-001 |
| AUTH-004 | WS-DSP-001·EVT-DSP-001·EVT-DSP-002·EVT-DSP-003·EVT-DSP-004·EVT-DSP-005·EVT-DSP-006 |
| CLS-002 | WS-CLS-001·EVT-MOD-001 |
| CLS-003 | WS-CLS-001·EVT-CLS-001 |
| CLS-004 | WS-CLS-001·WS-ACT-001·EVT-CLS-001·EVT-ACT-005·EVT-ACT-006 |
| CLS-005 | WS-CLS-001·EVT-CLS-001 |
| CLS-006 | WS-CLS-001·EVT-CLS-001 |
| CLS-007 | WS-CLS-001·WS-TCH-001·EVT-CLS-002 |
| CLS-008 | WS-CLS-001·WS-TCH-001·EVT-CLS-002 |
| CLS-009 | WS-ACT-001·WS-DSP-001·EVT-ACT-001·EVT-ACT-002·EVT-ACT-003·EVT-ACT-004·EVT-ACT-005·EVT-ACT-006·EVT-DSP-001·EVT-DSP-002·EVT-DSP-006 |
| CRS-006 | WS-CLS-001·EVT-MOD-001 |
| DAT-003 | WS-OPS-001·WS-STU-001·WS-TCH-001·EVT-NOTI-001·EVT-OPS-001·EVT-OPS-002·EVT-OPS-003 |
| DAT-006 | WS-CLS-001·WS-STU-001·EVT-QZ-004 |
| ECO-001 | WS-STU-001·EVT-TXN-001 |
| ECO-002 | WS-STU-001·EVT-TXN-001 |
| ECO-003 | WS-CLS-001·WS-TCH-001·EVT-POL-001 |
| ECO-005 | WS-ECO-001·WS-STU-001·WS-TCH-001·EVT-SHP-001·EVT-SHP-002 |
| ECO-006 | WS-STU-001·WS-TCH-001·EVT-SHP-002 |
| ECO-008 | WS-VOT-001·WS-DSP-001·EVT-VOT-001·EVT-VOT-002·EVT-VOT-003·EVT-VOT-004·EVT-DSP-004·EVT-DSP-005 |
| FIN-001 | WS-STU-001·EVT-FIN-001 |
| FIN-002 | WS-STU-001·EVT-FIN-002 |
| FIN-003 | WS-STU-001·EVT-FIN-002 |
| FIN-004 | WS-STU-001·EVT-FIN-002 |
| FIN-005 | WS-IVT-001·EVT-IVT-001·EVT-IVT-002 |
| FIN-006 | WS-STU-001·WS-IVT-001·EVT-IVT-003·EVT-IVT-004 |
| FIN-007 | WS-STU-001·WS-TCH-001·WS-IVT-001·EVT-IVT-001·EVT-IVT-002·EVT-IVT-004·EVT-IVT-005 |
| FIN-008 | WS-STU-001·WS-TCH-001·EVT-FIN-003 |
| FIN-009 | WS-STU-001·EVT-FIN-004 |
| FIN-010 | WS-STU-001·WS-TCH-001·EVT-FIN-003·EVT-FIN-004 |
| FIN-011 | WS-CLS-001·WS-TCH-001·EVT-POL-001 |
| FIN-012 | WS-IVT-001·WS-TCH-001·EVT-IVT-005 |
| QZ-001 | WS-QZ-001·EVT-QZ-001 |
| QZ-003 | WS-QZ-001·WS-STU-001·WS-DSP-001·EVT-QZ-001·EVT-QZ-002·EVT-QZ-003·EVT-QZ-004·EVT-QZ-005·EVT-QZ-006·EVT-QZ-007·EVT-QZ-009·EVT-DSP-003·EVT-DSP-004·EVT-DSP-005 |
| QZ-004 | WS-QZ-001·WS-STU-001·EVT-QZ-001·EVT-QZ-002·EVT-QZ-004·EVT-QZ-005·EVT-QZ-006·EVT-QZ-007·EVT-QZ-009 |
| QZ-005 | WS-QZ-001·WS-STU-001·EVT-QZ-007 |
| QZ-009 | WS-QZ-001·WS-STU-001·EVT-QZ-008 |
| SYS-003 | WS-ACT-001·WS-QZ-001·WS-VOT-001·WS-IVT-001·WS-DSP-001·EVT-ACT-003 |
| TRD-001 | WS-STU-001·WS-TCH-001·EVT-TRD-001 |
| TRD-002 | WS-STU-001·WS-TCH-001·EVT-TRD-001 |
| TRD-003 | WS-STU-001·WS-TCH-001·EVT-TRD-001 |
| TRD-004 | WS-CLS-001·EVT-MOD-001 |

WebSocket이 필요한 요구사항 44건은 모두 채널 또는 이벤트와 연결했습니다. 나머지 요구사항은 설정·조회·배치·인프라처럼 실시간 전달이 필수가 아닙니다.

## 11. 기계 판독 계약 커버리지

| 계약 | 사람이 읽는 기준 | 계약 수 | ID 차이 | 규격 검사 |
| --- | --- | ---: | ---: | --- |
| OpenAPI 3.1 | HTTP API 110개 | 작업 110개 | 0 | PASS |
| AsyncAPI 3.1 | 채널 10개·이벤트 48개 | 채널 10개·이벤트 48개 | 0 | PASS |

`scripts/generate-contracts.mjs`가 계약을 생성하면서 수·중복을 검사하고, `scripts/validate-contract-coverage.mjs`가 기준 문서와 계약의 ID 집합을 비교합니다.

## 12. 논리 데이터 모델 커버리지

| 요구사항 영역 | 논리 집계 루트 | 핵심 엔티티 |
| --- | --- | --- |
| 플랫폼·인증 | `classrooms` | 전역 `classroom_code`, `users`, `school_memberships`, `classroom_students`, `student_credentials`, `student_sessions`, `device_links` |
| 경제교실·코스 | `classrooms` | `course_template_versions`, `classroom_modules`, `classroom_policy_versions`, `autonomous_schedules`, `autonomous_schedule_modules`, `salary_roles`, `activity_sessions` |
| 경제·개인거래 | `transaction_groups`, `store_orders`, `trades` | `ledger_accounts`, `ledger_entries`, `policy_snapshots`, `store_*`, `inventory_items`, `trade_*` |
| 금융 | `deposit_accounts`, `savings_accounts`, `investment_markets`, `loans` | `financial_product_versions`, `savings_installments`, `investment_*`, `loan_*`, `credit_profiles`, `credit_events` |
| 퀴즈·투표 | `activity_sessions` | `quiz_*`, `votes`, `vote_options`, `vote_eligibilities`, `vote_ballots` |
| AI | `ai_runs` | `school_ai_setting_versions`, `ai_artifacts`, `ai_proposals`, `ai_decisions`, `economic_event_drafts` |
| 데이터·운영 | `audit_events`, `async_operations` | `report_snapshots`, `export_operations`, `idempotency_records`, `outbox_events`, `state_transitions` |

세부 관계와 불변 규칙은 DM-001~DM-014 및 6개 ERD로 정의했습니다. 모든 79개 요구사항 영역이 하나 이상의 논리 집계 루트와 연결됩니다.

## 13. 물리 데이터 모델 커버리지

| 항목 | 결과 | 검증 |
| --- | ---: | --- |
| 논리 엔티티 | 87 | 6개 ERD에서 추출 |
| 물리 테이블 | 87 | 논리 엔티티와 1:1 매핑 |
| 부분·핵심 유일성 | 21 | UK-001~021과 `classroom_code` 전역 UQ |
| 검사 제약 | 41 | CK-001~041 |
| 조회 인덱스 | 40 | IX-001~040 |
| DB 인수 기준 | 22 | DBT-001~022 |

물리 스키마는 학급 복합 FK, 원장 지연 제약, 완료 데이터 불변성, 정책 스냅샷, 공개 projection 경계를 포함합니다. `scripts/validate-physical-schema.mjs`가 87개 엔티티·테이블의 누락·중복과 제약 ID를 검사합니다.

### 13.1 실행 구현 연결

| 구현 | 위치 | 결과 |
| --- | --- | --- |
| Flyway P1~P17 | `backend/src/main/resources/db/migration/V1_1~V1_17` | 87개 테이블과 학급 식별·공개 순위·MVP 필드·추천 코스·활동 공개 필드·AI 실행·퀴즈 결과·캐릭터 장착 및 64개 코드형 상품 데이터 보강 |
| 기준 조회 인덱스 | `V1_9__indexes.sql`, `V1_11__mvp_schema_alignment.sql`, `V1_16__student_character_profiles_and_equipment.sql` | IX-001~040 |
| 학급 격리·도메인 검사 | `V1_1~V1_8` | 복합 FK·CHECK·부분 UQ |
| 원장·불변성 트리거 | `V1_8__constraints_triggers.sql` | 지연 균형·복구 root·append-only |
| 공개 projection | `V1_9__indexes.sql`, `V1_10__classroom_identity_and_public_quiz.sql` | 퀴즈·투표·공동기금·투자 4개 view, GAME 공개 1~3위 제한 |
| DBT 자동화 | `backend/src/test/java/com/b201/ecovillage/SchemaIntegrityTest.java` | DBT-001~022 + 테이블 수 검사 |
| API 필드 매핑 | `contracts/api-field-mapping.json` | MVP·지원 79개 strict, 후속 31개 planned |

Flyway `V1_1`~`V1_13` 기준선은 PostgreSQL 17.10 격리 데이터베이스에서 적용·재생성·Hibernate
검증을 완료했습니다. `V1_14`와 DBT-021~022는 코드와 계약 검사를 추가했으며, Java 21과 Docker를
사용할 수 있는 Jenkins에서 87개 테이블 생성과 Testcontainers 23개 전체 실행을 최종 PASS 기준으로
사용합니다.

## 14. 커버리지 요약

| 영역 | 요구사항 수 | 기준선 | 상태 머신 | 권한 규칙 | 화면 | HTTP API | OpenAPI | WS 대상 | WS 연결 | AsyncAPI | 설계 하나 이상 | DBT 연결 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 플랫폼·인증 | 13 | 13 | 2 | 13 | 13 | 12 | 12 | 3 | 3 | 3 | 13 | 6 |
| 경제교실·코스 | 16 | 16 | 11 | 16 | 16 | 16 | 16 | 9 | 9 | 9 | 16 | 3 |
| 경제·개인거래 | 15 | 15 | 13 | 15 | 15 | 15 | 15 | 10 | 10 | 10 | 15 | 7 |
| 금융 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | 12 | 1 |
| 퀴즈 | 9 | 9 | 9 | 9 | 9 | 9 | 9 | 5 | 5 | 5 | 9 | 2 |
| AI | 11 | 11 | 11 | 11 | 11 | 11 | 11 | 5 | 5 | 5 | 11 | 0 |
| 데이터·운영 | 6 | 6 | 3 | 6 | 6 | 6 | 6 | 2 | 2 | 2 | 6 | 1 |
| 합계 | 82 | 82 | 61 | 82 | 82 | 81 | 81 | 46 | 46 | 46 | 82 | 20 |

현재 요구사항 기준선·권한 규칙·화면 커버리지는 각각 82/82입니다. 상태 또는 불변 생명주기 규칙이 필요한 요구사항 61개는 상태 머신과 연결했습니다. 화면 행동 69개는 모두 HTTP 명령 API와 연결했고 HTTP·OpenAPI 대상 요구사항은 81/81입니다. WebSocket·AsyncAPI 대상 요구사항 46개도 채널·이벤트와 모두 연결했습니다. 테스트 열은 하나 이상의 자동 DB 무결성 테스트 `DBT-*`가 연결된 요구사항 수이며, 교사 인증은 서비스 통합 테스트 `TeacherSessionApiTest`로 연결했습니다. 나머지 서비스·E2E 인수 테스트는 `TEST TBD`로 구분합니다. `SYS-004`는 Terraform에서 연결합니다.

## 15. 다음 갱신

1. Jenkins PostgreSQL 17 Testcontainers 전체 실행 결과 연결
2. [API 필드 매핑](../contracts/api-field-mapping.json) 기반 인터페이스 생성
3. 서비스 인수 테스트 ID 연결
4. `planned` API 승격 시 저장 모델·strict DTO 추가
5. 개발 백로그·구현 커밋 연결

기존 85개 테이블의 Flyway·DB 무결성 테스트는 완료했습니다. 2026-07-30에 설계한
AI v1.2 선택형 모듈·추천 범위 계약과 서비스·E2E 테스트는 단계별 개발 계획에 따라 후속 구현합니다.
