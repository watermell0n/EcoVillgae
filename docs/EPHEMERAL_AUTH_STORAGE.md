# 임시 인증정보 저장 계약

- 버전: v1.0
- 기준일: 2026-07-30
- 범위: 비밀번호 재설정, WebSocket 연결 티켓, 공용 화면 세션
- 기준: Redis는 일회용 권한의 만료·소비 상태만 보관하며 업무 데이터의 최종 근거가 아닙니다.

## 1. 공통 보안 규칙

1. 클라이언트에 주는 토큰은 CSPRNG로 생성하고 Redis 키에는 원문 대신 `SHA-256(token)`의 소문자 hex를 사용합니다.
2. 원문 토큰·비밀번호·검증값은 PostgreSQL, 감사 이벤트, 애플리케이션 로그, 오류 응답에 저장하지 않습니다.
3. 일회용 값은 Lua script 또는 동등한 원자적 `GET`+`DEL` 연산으로 한 번만 소비합니다.
4. TTL 만료, 범위 불일치, 이미 소비됨, Redis 장애는 모두 권한 거절로 처리합니다. Redis 장애 때 우회 인증을 허용하지 않습니다.
5. 감사에는 토큰이 아닌 요청 ID, 실행자, 대상, 목적, 성공·실패 사유, 만료 시각만 기록합니다.

## 2. 키·TTL·payload

| 용도·API | Redis 키 | TTL | 최소 payload | 소비·폐기 |
| --- | --- | --- | --- | --- |
| 비밀번호 재설정 `API-AUTH-002~003` | `auth:password-reset:v1:{sha256(reset_request_id)}` | 10분 | `classroom_id`, `student_id`, `verification_method`, `verified_at`, `requested_by_user_id`, `attempt_count` | `API-AUTH-003` 성공 시 원자 소비. 실패 검증은 횟수를 증가시키고 5회면 즉시 폐기 |
| WebSocket 티켓 `API-AUTH-007` | `auth:realtime-ticket:v1:{sha256(ticket)}` | 60초 | `subject_type`, `subject_id`, `classroom_id`, 허용 `channel_ids`, 허용 `resource_ids`, `issued_at` | WebSocket 연결 성립 시 원자 소비. 재연결에는 새 티켓 필요 |
| 공용 화면 세션 `API-DSP-001` | `auth:display-session:v1:{sha256(display_token)}` | 활동 종료+5분과 발급 후 8시간 중 빠른 시각 | `display_session_id`, `classroom_id`, `activity_id`, 공개 채널 allowlist, `issued_by_user_id`, `issued_at` | `DISCONNECT`, 활동 종료·취소, 교실 종료 시 즉시 삭제 |

## 3. 비밀번호와 세션 반영

- `API-AUTH-003.new_password`는 서비스 메모리에서 Argon2id로 해시한 뒤 `student_credentials.password_hash`만 교체하고 `credential_version`을 증가시킵니다.
- 재설정 성공 시 해당 학생의 활성 `student_sessions`를 모두 폐기합니다. 새 비밀번호 원문은 응답하지 않습니다.
- 교사·관리자 웹의 회전형 갱신 세션은 외부 IdP의 세션 저장소를 기준으로 하고, 학생 세션은 `student_sessions.token_hash`를 기준으로 합니다.

## 4. 공용 projection 경계

공용 화면 세션은 `view.public_quiz_leaderboard`, `view.public_vote_aggregates`,
`view.public_fund_summary`, `view.public_investment_market`와 승인된 공개 이벤트만 읽습니다.
학생 ID·잔액·신용·대출·개인거래·TEST 결과·정답·점수는 payload에 포함하지 않습니다.

## 5. 운영 검증

- Redis keyspace notification에 의존하지 않고 명령 처리 시 TTL과 활동 상태를 다시 검증합니다.
- 로그 필터 테스트는 `password`, `new_password`, `verification_value`, `ticket`, `display_token`, 갱신 토큰 필드가 출력되지 않는지 확인합니다.
- 인수 테스트는 정상 1회 소비, 재사용 거절, TTL 만료, 다른 학급·활동·채널 사용 거절, Redis 장애 시 fail-closed를 포함합니다.
