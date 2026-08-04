# AI 통합 검증 기록

- 검증일: 2026-08-04
- 대상: AI 기능 1~6단계(실행 환경, 시작 전 점검, 교사용 챗봇, 퀴즈 초안, 자동 모니터링·밸런싱, 단기·장기 투자 시나리오와 매수)
- 기준 브랜치: `feat/ai-features`

이 문서는 독립 AI MVP 평가와 Spring·교사 웹·학생 Expo 앱을 연결한 뒤 수행한
릴리스 전 통합 검증의 결과를 기록한다. 실제 공급자 품질 평가는 자격증명과 별도 사람
검토가 필요하므로, 아래 자동 검증 통과와 구분한다.

## 1. 자동 검증 결과

| 영역 | 결과 | 근거 |
| --- | --- | --- |
| 내부 AI 계약 | 통과 | 생성 경로 4개, 평가 사례 350개, 투자 뉴스 사례 32개 |
| HTTP·WebSocket 계약 | 통과 | OpenAPI 109개 작업, AsyncAPI 채널 10개·이벤트 48개 |
| API·스키마 매핑 | 통과 | 엄격 MVP·지원 계약 82개, 계획 계약 27개 |
| Spring·OpenAPI 연결 | 통과 | Spring 엔드포인트 64개와 계약 64개 일치 |
| 문서 정합성 | 통과 | 로컬 링크·오래된 버전 링크·정의되지 않은 ID 0건 |
| AI 서비스 회귀 | 통과 | `548 passed`, 실제 공급자 smoke 5개는 선택 실행이므로 skip |
| AI 출시 게이트 | 통과 | fake provider 350개 사례, 구조·안전 게이트 모두 PASS |
| 교사 웹 | 통과 | TypeScript 검사와 Next.js 프로덕션 빌드 |
| 학생 Expo 앱 | 통과 | TypeScript 검사와 웹 export(1,408 modules) |
| 백엔드 전체 Gradle 테스트 | 6단계에서 통과 | 시나리오 생성·시장 시작·학생 매수까지를 포함한 전체 회귀 |
| 백엔드 배포 산출물 | 통과 | Java 21 Docker `bootJar` 빌드와 `/actuator/health` `UP` |

이번 7단계에서 전체 Gradle 테스트를 Docker로 다시 실행했지만, 테스트 컨테이너가 도구의
5분 제한 안에 종료되지 않았다. 이 실행은 새 실패로 판정하지 않으며, 6단계에서 통과한
전체 회귀 결과와 이번 배포 빌드·헬스 체크를 분리해 기록한다. CI에서는 시간 제한 없이
`backend/gradlew test --no-daemon`을 다시 실행해 최종 병합 게이트로 사용한다.

Windows에서는 pytest 기본 임시 디렉터리의 권한이나 CP949 콘솔 인코딩 때문에 검증이
실패할 수 있다. 이 경우 프로젝트 내부의 임시 경로와 UTF-8 출력을 지정해 실행한다.

```powershell
$tempPath = Join-Path (Get-Location) '.validation-tmp'
New-Item -ItemType Directory -Path $tempPath -Force | Out-Null
$env:TEMP = $tempPath
$env:TMP = $tempPath
$env:PYTHONIOENCODING = 'utf-8'
.venv/Scripts/python.exe -m pytest
.venv/Scripts/python.exe -m ecovillage_ai.evaluation
Remove-Item -LiteralPath $tempPath -Recurse -Force
```

`validate-document-consistency.mjs`는 이름이 확장된 `.pytest_cache*` 생성 디렉터리도
문서 탐색에서 제외한다. 따라서 로컬 테스트 캐시가 있어도 문서 검증 결과가 달라지지 않는다.

## 2. 통합 흐름 확인 범위

### AI 공통 기능

- AI 실행 환경, 학급 AI 설정·감사 이력, 시작 전 점검, 챗봇, 퀴즈 초안, 경제 이벤트,
  자동 모니터링·밸런싱은 Spring API가 권한·규칙·저장을 소유하고 FastAPI는 구조화된
  설명 또는 초안만 생성한다.
- 교사 웹은 생성 결과를 조회하고 검토·승인·거절하며, 학생 데이터와 토큰은 AI 입력에
  포함하지 않는다.

### 투자 흐름

- 교사는 투자 모듈의 `SHORT_TERM` 또는 `LONG_TERM` 설정과 같은 모드로 시나리오를
  생성하고 시장을 시작한다.
- 장기는 FRED S&P 500의 고정 기간 변화율을 우선 사용하며, 데이터를 가져오지 못하면
  검증된 생성 가격 경로로 폴백한다.
- 현재 가격 단계와 숨은 바로 다음 결과는 AI 뉴스의 생성 입력으로만 사용한다. 학생에게는
  교사가 승인한 현재 단계 이야기와 이미 공개된 가격 이력만 반환한다.
- 학생 매수는 가격 버전·수량·잔액을 다시 검증하고, 지갑 차감·투자 청산 계정·주문·보유
  포지션을 하나의 원장 트랜잭션으로 확정한다.

## 3. 의도적으로 남긴 후속 범위

이 검증 대상은 학생이 매수 버튼을 누른 시점까지다. 다음 기능은 별도 단계에서 구현·E2E
검증한다.

- 단기 투자 다음 날 전량 의무 정산
- 장기 투자 일별 매도·보유·추가 매수와 마지막 날 전량 정산
- 시장 `PAUSED`·`CLOSED` 전이와 교실 종료 정산
- 실제 Bedrock/OpenAI 자격증명 smoke와 사람 문장 품질 평가

후속 기능이 추가되면 이 문서의 투자 범위와 백엔드 전체 Gradle 테스트 결과를 함께 갱신한다.
