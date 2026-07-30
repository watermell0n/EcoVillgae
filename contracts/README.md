# API 계약 사용 안내

이 디렉터리는 사람이 검토하는 HTTP·WebSocket 명세를 구현 도구가 읽는 계약으로 변환한 결과입니다.

| 파일 | 역할 | 기준 문서 |
| --- | --- | --- |
| [openapi.yaml](openapi.yaml) | 107개 REST API의 경로·권한·요청·응답·오류 계약 | [HTTP API 명세](../docs/HTTP_API_SPEC.md) |
| [asyncapi.yaml](asyncapi.yaml) | 10개 논리 채널·48개 서버 이벤트·4개 연결 제어 메시지 계약 | [WebSocket 명세](../docs/WEBSOCKET_SPEC.md) |
| [api-field-mapping.json](api-field-mapping.json) | 107개 작업의 MVP/후속 분류와 요청·응답 필드·저장 경계 | [API·스키마 정합성 검토](../docs/reviews/API_SCHEMA_FIELD_AUDIT.md) |

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
pnpm --package=@redocly/cli dlx redocly lint contracts/openapi.yaml
pnpm --package=@asyncapi/cli dlx asyncapi validate contracts/asyncapi.yaml
```

생성기는 다음 불변식을 검사합니다.

- OpenAPI 작업 107건, API ID·메서드·경로 중복 0건
- AsyncAPI 논리 채널 10건, 이벤트 ID·`event_type` 중복 0건
- 사람이 읽는 명세와 계약 파일 사이 API·채널·이벤트 ID 누락 0건
- MVP·필수 지원 API 68건의 명시적 strict DTO와 저장·조회 경계
- 후속 API 39건의 `planned` 상태

## 구현 경계

- OpenAPI가 HTTP 경로·입출력·오류·보안 계약의 기준입니다.
- AsyncAPI가 WebSocket 메시지 envelope·payload·송수신 방향의 기준입니다.
- 상태 전이·권한·제품 정책의 상세 의미는 연결된 `SM-*`, `PM-*`, RTM 요구사항을 함께 확인합니다.
- 변경 명령은 HTTP로만 처리하고 WebSocket은 커밋된 결과 전달에만 사용합니다.
- 계약에 없는 필드를 구현 과정에서 임의로 추가하지 않고 먼저 기준 문서와 생성기를 수정합니다.
