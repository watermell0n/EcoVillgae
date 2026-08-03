# API 계약 사용 안내

이 디렉터리는 사람이 검토하는 HTTP·WebSocket 명세를 구현 도구가 읽는 계약으로 변환한 결과입니다.

| 파일 | 역할 | 기준 문서 |
| --- | --- | --- |
| [openapi.yaml](openapi.yaml) | 109개 REST API의 경로·권한·요청·응답·오류 계약 | [HTTP API 명세](../docs/HTTP_API_SPEC.md) |
| [ai-internal-openapi.yaml](ai-internal-openapi.yaml) | Spring과 FastAPI 사이 schema v1.2, 4개 내부 생성 API 계약 | [내부 AI API 계약](../docs/ai/INTERNAL_API_CONTRACT.md) |
| [asyncapi.yaml](asyncapi.yaml) | 10개 논리 채널·48개 서버 이벤트·4개 연결 제어 메시지 계약 | [WebSocket 명세](../docs/WEBSOCKET_SPEC.md) |
| [api-field-mapping.json](api-field-mapping.json) | 109개 작업의 MVP/후속 분류와 요청·응답 필드·저장 경계 | [API·스키마 정합성 검토](../docs/reviews/API_SCHEMA_FIELD_AUDIT.md) |

## 적용 순서

1. 제품 정책은 `docs/`의 사람이 읽는 기준 문서에서 먼저 합의합니다.
2. `HTTP_API_SPEC.md` 또는 `WEBSOCKET_SPEC.md`의 ID·경로·이벤트를 변경합니다.
3. 계약을 다시 생성합니다.
4. 커버리지와 공식 규격 검사를 통과시킵니다.
5. 생성된 타입·클라이언트·서버 인터페이스는 계약 파일에서 만듭니다.

## 생성과 검증

저장소 루트에서 실행합니다.

```powershell
node scripts/generate-contracts.mjs
node scripts/validate-contract-coverage.mjs
node scripts/validate-api-schema-consistency.mjs
node scripts/validate-ai-internal-contract.mjs
node scripts/validate-spring-openapi-contract.mjs
pnpm --package=@redocly/cli dlx redocly lint contracts/openapi.yaml
pnpm --package=@redocly/cli dlx redocly lint contracts/ai-internal-openapi.yaml
pnpm --package=@asyncapi/cli dlx asyncapi validate contracts/asyncapi.yaml
```

`validate-spring-openapi-contract.mjs`는 실제 Spring Controller의 method/path와 record DTO 최상위
필드를 OpenAPI 계약과 비교합니다. 현재 검토가 끝난 차이는
`spring-openapi-baseline.json`에 기록하며, 새로운 차이 또는 해소된 차이가 생기면 CI가 실패해
명세와 구현 중 어느 쪽을 갱신할지 검토하도록 합니다.

학생 클라이언트의 최종 대상은 Expo 앱입니다. Expo에서는 OpenAPI 생성 타입을 그대로 사용하거나
API 경계의 단일 mapper에서만 `snake_case`를 camelCase로 바꾸며, 현재 Vite 프로토타입의 수동
응답 타입을 신규 계약의 기준으로 사용하지 않습니다.

생성기는 다음 불변식을 검사합니다.

- OpenAPI 작업 109건, API ID·메서드·경로 중복 0건
- AsyncAPI 논리 채널 10건, 이벤트 ID·`event_type` 중복 0건
- 사람이 읽는 명세와 계약 파일 사이 API·채널·이벤트 ID 누락 0건
- MVP·필수 지원 API 74건의 명시적 strict DTO와 저장·조회 경계
- 후속 API 35건의 `planned` 상태
- 내부 AI API 4건, schema v1.2 fixture 성공 13건·실패 9건, 이벤트 템플릿 7종,
  분석 모드·설정 문맥·이벤트 출처·투자 단계 연결·퀴즈 난이도와 금지 입력 교차 참조

## 구현 경계

- OpenAPI가 HTTP 경로·입출력·오류·보안 계약의 기준입니다.
- AsyncAPI가 WebSocket 메시지 envelope·payload·송수신 방향의 기준입니다.
- 상태 전이·권한·제품 정책의 상세 의미는 연결된 `SM-*`, `PM-*`, RTM 요구사항을 함께 확인합니다.
- 변경 명령은 HTTP로만 처리하고 WebSocket은 커밋된 결과 전달에만 사용합니다.
- 계약에 없는 필드를 구현 과정에서 임의로 추가하지 않고 먼저 기준 문서와 생성기를 수정합니다.
