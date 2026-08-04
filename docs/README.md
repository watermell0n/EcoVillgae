# 문서 안내

이 디렉터리의 루트에는 개발 과정에서 계속 기준으로 사용하는 설계·계약 문서를 둡니다.
특정 시점의 정합성 검토와 충돌 검사 결과는 [`reviews/`](reviews/)에 분리합니다.

## 권장 읽기 순서

1. [프로젝트 README](../README.md): 서비스 목표, MVP와 제외 범위
2. [요구사항 기준선](REQUIREMENTS_BASELINE.md): 확정 요구사항과 용어
3. [API 명세 전 결정사항](API_PRE_DESIGN_DECISIONS.md): 설계 전에 확정한 해석과 정책
4. [RTM](RTM.md): 요구사항과 화면·API·데이터·테스트 연결
5. [API 공통 규칙](API_COMMON_RULES.md), [HTTP API](HTTP_API_SPEC.md), [WebSocket](WEBSOCKET_SPEC.md)
6. [논리 데이터 모델](DATA_MODEL.md), [물리 스키마](PHYSICAL_SCHEMA.md)

## 제품·과정 설계

| 문서 | 역할 |
| --- | --- |
| [학생 앱 디자인 문서](design/README.md) | 단일 체형 코드 기반 도트 캐릭터와 학생 화면 구현 기준 |
| [요구사항 기준선](REQUIREMENTS_BASELINE.md) | 확정 요구사항과 공통 용어 |
| [추천 코스](COURSE_RECOMMENDATIONS.md) | 학년×규모별 모듈·난이도 조합 |
| [AI 활용 방향](AI_DIRECTION.md) | AI 기능·안전·승인 경계 |
| [교육용 대출 MVP 정책·상환 흐름](LOAN_MVP_POLICY.md) | 대출 신청·승인·상환·연체와 원장·후속 구현 기준 |
| [AI 단계별 개발 계획](ai/IMPLEMENTATION_PLAN.md) | AI 계약·규칙·통합·평가의 실행 순서와 단계별 완료 기준 |
| [향후 모듈과 로드맵](FUTURE_MODULES_AND_ROADMAP.md) | MVP 이후 범위 |
| [기술 스택](TECH_STACK.md) | 앱·웹·서버·인프라 기술 기준 |

## 화면·권한·API 계약

| 문서 | 역할 |
| --- | --- |
| [역할·권한 매트릭스](PERMISSION_MATRIX.md) | 역할별 리소스·명령 권한 |
| [핵심 상태 머신](STATE_MACHINES.md) | 도메인 상태와 허용 전이 |
| [화면 ID·행동 명세](SCREEN_SPEC.md) | 역할별 화면과 사용자 행동 |
| [API 공통 규칙](API_COMMON_RULES.md) | 인증·오류·멱등성·동시성 규칙 |
| [HTTP API 명세](HTTP_API_SPEC.md) | 명령·조회 REST API |
| [WebSocket 명세](WEBSOCKET_SPEC.md) | 실시간 채널·이벤트·재연결 |
| [임시 인증정보 저장 계약](EPHEMERAL_AUTH_STORAGE.md) | 재설정·실시간·공용 화면 토큰 저장 규칙 |

## 데이터·추적 설계

| 문서 | 역할 |
| --- | --- |
| [RTM](RTM.md) | 요구사항과 설계·테스트 추적 |
| [논리 데이터 모델](DATA_MODEL.md) | 엔티티·관계·도메인 불변식 |
| [물리 스키마](PHYSICAL_SCHEMA.md) | PostgreSQL 테이블·제약·인덱스·DBT |

## 협업·인프라

| 문서 | 역할 |
| --- | --- |
| [팀 일정·협업 방식](TEAM_SCHEDULE_AND_WORKFLOW.md) | 작업 순서와 담당 경계 |
| [인프라 기준선](INFRASTRUCTURE_BASELINE.md) | 로컬·CI·AWS 구축 기준 |
| [Git 작업 안내](GIT_GUIDE.md) | 브랜치·커밋·머지 절차 |

## 검토 기록

| 문서 | 역할 |
| --- | --- |
| [문서 충돌 검사](reviews/DOCUMENT_CONSISTENCY_AUDIT.md) | 문서 간 충돌·누락과 해결 기록 |
| [API·스키마 필드 정합성 검토](reviews/API_SCHEMA_FIELD_AUDIT.md) | API DTO와 저장·projection 매핑 검토 |
| [학생 앱·교사 웹 인증 계약 점검](reviews/CLIENT_AUTH_CONTRACT_AUDIT.md) | 클라이언트별 토큰 저장·갱신·CORS·CSRF·WebSocket 연결 기준 |
| [교사 웹 인증 미연결과 소유 경계](reviews/TEACHER_WEB_AUTH_GAP_AND_OWNERSHIP.md) | 교사 로그인 미연결 현황, 토큰 저장 위치 수정, AI·프런트 파일 소유 분리 |

`reviews/` 문서는 검토 시점의 판정 근거입니다. 제품 정책이나 구현 계약을 변경할 때는
먼저 루트의 기준 문서를 수정하고 계약·검증 파일을 재생성한 뒤 검토 기록을 갱신합니다.
