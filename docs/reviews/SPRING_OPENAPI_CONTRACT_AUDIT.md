# Spring-OpenAPI 계약 차이 기준선

- 추적 이슈: `S15P11B201-279`
- 기준 입력: `contracts/api-field-mapping.json`, Spring Controller/record DTO
- 학생 클라이언트 기준: React Native + Expo + TypeScript

## 검사 범위

- Spring에 실제로 노출된 HTTP method/path가 OpenAPI에 존재하는지 검사합니다.
- 실제 구현됐지만 OpenAPI에서 `planned`로 남은 경로를 검사합니다.
- 구현된 strict API의 request/response 최상위 DTO 필드를 Jackson `SNAKE_CASE` 기준으로 비교합니다.
- 현재 차이는 `contracts/spring-openapi-baseline.json`에 고정하고, 추가·해소된 차이는 CI에서 검토를 요구합니다.
- OpenAPI에 있지만 아직 Spring에 없는 API는 백로그 상태일 수 있으므로 이 검사에서 오류로 보지 않습니다.

## 현재 요약

| 항목 | 수 |
| --- | ---: |
| OpenAPI 작업 | 132 |
| Spring 엔드포인트 | 98 |
| OpenAPI와 연결된 Spring 엔드포인트 | 96 |
| 기준선에 기록한 차이 | 2 |

## 현재 차이

| 종류 | API/경로 | 상세 |
| --- | --- | --- |
| SPRING_ROUTE_NOT_IN_OPENAPI | POST /auth/teacher-password-sessions | backend/src/main/java/com/b201/ecovillage/identity/api/TeacherPasswordSessionController.java#create |
| SPRING_ROUTE_NOT_IN_OPENAPI | POST /auth/teacher-registrations | backend/src/main/java/com/b201/ecovillage/identity/api/TeacherRegistrationController.java#create |

## 담당 작업 연결

| 범위 | Jira |
| --- | --- |
| 인증 bootstrap·명단·모듈/정책 조회 | `S15P11B201-269` |
| AI 실행·제안 및 operation 조회 | `S15P11B201-270` |
| 상품 command | `S15P11B201-271` |
| 상품·퀴즈 AI APPLY 라우팅 | `S15P11B201-272` |
| 코스·모듈 중지·정책 변경 | `S15P11B201-273` |
| 퀴즈 결과·보상·재채점 | `S15P11B201-274` |
| 교사 웹 상태 복원 | `S15P11B201-275` |
| 교사 상품·GAME AI UI | `S15P11B201-276` |
| Expo 인증·API 생성 타입 | `S15P11B201-277` |
| Expo 활성 모듈·동적 리소스 | `S15P11B201-278` |

## 클라이언트 규칙

Spring JSON은 `snake_case`입니다. TypeScript에서 camelCase를 쓰려면 생성기가 이름을 바꾸지 않는 한
응답 경계의 단일 mapper에서만 변환합니다. 수동 interface를 선언하고 Spring JSON을 그대로
camelCase 객체로 간주해서는 안 됩니다. 현재 Vite 학생 앱은 이전 참고용이며 이 기준선의 최종 소비자는 Expo 앱입니다.

## 실행

`node scripts/validate-spring-openapi-contract.mjs`

계약을 의도적으로 변경한 경우 검토 후 다음 명령으로 기준선과 이 문서를 갱신합니다.

`node scripts/validate-spring-openapi-contract.mjs --write-baseline`
