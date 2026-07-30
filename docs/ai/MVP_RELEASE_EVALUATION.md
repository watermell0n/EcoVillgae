# AI 독립 MVP 통합 평가와 출시 게이트

- 기준일: 2026-07-30
- 상태: 독립 AI MVP 10단계 구현 완료
- 출시 프로필: `ai-mvp-release-v1`
- 기본 범위: API 키가 필요 없는 `FAKE_AI_MVP`

## 1. 무엇을 확인하는가

통합 실행기는 Spring·DB·웹 없이 AI 서비스의 요청, 생성, 출력 검증과 오류 처리를 한 번에
확인합니다. 기본 실행은 다음 항목을 수행합니다.

1. 일곱 평가 카탈로그의 버전과 평가 건수 검사
2. 실제 공급자를 제외한 전체 pytest 회귀 검사
3. Ruff 정적 검사
4. 스키마·근거·범위·민감정보·금융조언·미래정보·승인 경계 자동 게이트 판정
5. 선택한 공급자·모델·schema·프롬프트 버전 기록

실제 공급자, Docker와 사람 평가는 기본 실행에서 호출하지 않습니다. 실행하지 않은 항목은
성공으로 계산하지 않고 각각 `SKIPPED` 또는 `NOT_REVIEWED`로 표시합니다.

## 2. 평가 350개 구성

| 평가 묶음 | 사례 수 |
| --- | ---: |
| 공통 계약·인증·문맥·안전·장애 | 60 |
| 설정 추천·시작 전 점검 | 48 |
| 자동 모니터링·밸런싱 | 60 |
| 교사용 챗봇 | 60 |
| 경제 이벤트 | 42 |
| 투자 뉴스 | 32 |
| 퀴즈 초안 | 48 |
| 합계 | 350 |

공통 60개는 여섯 AI 기능군에 정상, 추가 필드, 필수 필드 누락, 인증 누락, 실행 문맥
불일치, deadline 만료, 민감정보 출력, 비밀값 출력, 승인 우회와 공급자 장애를 각각
적용합니다. 기능별 평가는 각 기능의 학년·근거·범위·구조·콘텐츠 경계를 추가로 검사합니다.

## 3. 가장 간단한 실행 방법

`ai-service` 디렉터리에서 실행합니다.

Git Bash:

```bash
export ECOVILLAGE_AI_PROVIDER=FAKE
.venv/Scripts/python.exe -m ecovillage_ai.evaluation
```

PowerShell:

```powershell
$env:ECOVILLAGE_AI_PROVIDER='FAKE'
.venv\Scripts\python.exe -m ecovillage_ai.evaluation
```

이 명령에는 API 키가 필요하지 않습니다. 성공하면 최상위 `status`와
`automated_gates.status`가 `PASS`이고 `selected_evaluation_cases`가 `350`입니다.
실제 모델과 사람 평가를 실행하지 않았으므로 `production_release_ready`는 `false`가
정상입니다.

결과를 파일로 보관하려면 다음처럼 실행합니다.

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation `
  --report build\ai-mvp-evaluation.json
```

## 4. 기능 하나만 빠르게 검사

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation --suite teacher-chat
.venv\Scripts\python.exe -m ecovillage_ai.evaluation --suite investment-news
```

사용 가능한 값은 `common`, `setup-precheck`, `balancing`, `teacher-chat`,
`economic-event`, `investment-news`, `quiz-draft`입니다. 기능별 실행은 해당 평가의 성공을
확인하지만 전체 출시 판정이 아니므로 `automated_gates.status=SKIPPED`로 표시합니다.

매개변수 ID를 알고 있는 사례 한 건만 재현하려면 `--case`를 함께 사용합니다.

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation `
  --suite common --case teacher-chat-success
```

## 5. 선택 검사

### 5.1 Docker smoke

Docker Desktop이 실행 중일 때 다음 옵션을 추가합니다.

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation --docker-smoke
```

실행기는 `ai-service/Dockerfile`로 이미지를 만들고 임시 컨테이너의 `/healthz`에서 다음을
검사한 뒤 그 컨테이너만 제거합니다.

- `status=UP`, `schema_version=1.1`
- 기능별 선택 공급자와 모델 ID
- `setup-precheck-v1`, `balancing-v1`, `teacher-chat-v1`, `economic-event-v1`,
  `quiz-draft-v1`

### 5.2 실제 공급자 smoke

실제 호출은 사용량과 비용이 발생할 수 있으므로 `--provider-smoke`가 있을 때만 실행합니다.
먼저 [실제 공급자 어댑터](PROVIDER_ADAPTERS.md)의 환경변수를 설정해야 합니다.

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation --provider-smoke
```

자격증명이 없거나 `FAKE`가 선택된 상태에서 이 옵션을 주면 성공으로 건너뛰지 않고
`FAIL`로 종료합니다.

### 5.3 사람 평가

실제 모델의 검토 결과는 다음 구조의 JSON으로 준비합니다. 사례마다 서로 다른 검토자 두
명의 점수가 필요하며 점수 차이가 있으면 낮은 점수를 사용합니다.

```json
{
  "review_version": "human-review-v1",
  "provider": "OPENAI",
  "model_id": "configured-model-id",
  "prompt_versions": ["teacher-chat-v1"],
  "cases": [
    {
      "case_id": "teacher-chat-grade-5-case-01",
      "reviews": [
        {
          "reviewer_id": "reviewer-a",
          "factual_consistency": 2,
          "evidence_traceability": 2,
          "grade_appropriateness": 2,
          "actionability": 2,
          "uncertainty_honesty": 2
        },
        {
          "reviewer_id": "reviewer-b",
          "factual_consistency": 2,
          "evidence_traceability": 2,
          "grade_appropriateness": 1,
          "actionability": 2,
          "uncertainty_honesty": 2
        }
      ]
    }
  ]
}
```

```powershell
.venv\Scripts\python.exe -m ecovillage_ai.evaluation `
  --human-review path\to\human-review.json
```

각 평가 항목에서 1점 이상인 사례가 95% 이상이고 전체 평균이 1.6/2.0 이상이어야
`human_review.status=PASS`입니다.

## 6. 결과 해석

| 결과 | 의미 |
| --- | --- |
| 최상위 `status=PASS` | 요청한 검사들이 모두 통과 |
| `automated_gates.status=PASS` | 전체 FAKE 회귀와 자동 안전 게이트 통과 |
| `provider_smoke.status=SKIPPED` | 실제 공급자를 요청하지 않음 |
| `docker_smoke.status=SKIPPED` | Docker 검사를 요청하지 않음 |
| `human_review.status=NOT_REVIEWED` | 사람 평가 파일을 지정하지 않음 |
| `production_release_ready=true` | 전체 자동·실제 공급자·Docker·사람 평가가 모두 통과 |

기본 `FAKE` 결과는 AI 모듈의 계약과 안전 구현이 배포 후보라는 뜻이며, 실제 모델의 문장
품질이나 운영 배포 승인을 대신하지 않습니다.

## 7. fixture 추가와 변경·롤백

- 새 실패 사례는 가장 가까운 기능별 `tests/fixtures/*_evaluation_v1.json`에 추가합니다.
- 모든 기능에 공통인 실패는 `common_evaluation_v1.json`에 추가하고 출시 프로필 건수도
  함께 변경합니다.
- 모델이나 프롬프트를 변경하면 실제 공급자 smoke와 영향받는 기능별 평가, 사람 평가를
  다시 실행합니다.
- 새 조합이 자동 또는 사람 게이트에 실패하면 배포 설정을 바꾸지 않고 직전 승인된
  공급자·모델·프롬프트 버전을 유지합니다.
- 기존 평가 결과와 당시 버전을 덮어쓰지 않고 새 결과 파일로 보관합니다.
