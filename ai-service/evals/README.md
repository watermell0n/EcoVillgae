# 퀴즈 품질 평가 작업 폴더

퀴즈 초안의 **내용 품질**을 사람이 채점해 기록하는 곳입니다.
기존 `python -m ecovillage_ai.evaluation`은 구조와 계약이 맞는지만 검사하므로,
"넌센스가 넌센스다운가", "학년에 맞는가" 같은 것은 여기서 잽니다.

채점 기준과 실행 순서는 [`docs/ai/QUIZ_GOLDEN_SET.md`](../../docs/ai/QUIZ_GOLDEN_SET.md)를 따릅니다.
입력 세트와 기준 예시는 [`tests/fixtures/quiz_golden_set_v1.json`](../tests/fixtures/quiz_golden_set_v1.json)에 있습니다.

## 폴더 구조

```
evals/
  runs/
    run-0-baseline/          현재 프롬프트 + 현재 모델 (기준선)
      generated.json         생성 결과
      review-a.json          검토자 A 점수
      review-b.json          검토자 B 점수
      summary.json           집계 결과
      blind-key.json         비교할 때 좌우가 어느 실행인지 (채점 후 개봉)
    run-a-prompt/            개선 프롬프트 + 현재 모델
    run-b-model/             현재 프롬프트 + 상위 모델
    run-c-both/              개선 프롬프트 + 상위 모델
```

`review-template.json`과 `comparison-template.json`을 복사해서 쓰세요.

## 한 번 실행하는 법

1. 실행 폴더를 만듭니다. 예: `evals/runs/run-a-prompt/`
2. `quiz_golden_set_v1.json`의 12건을 그대로 교사 화면이나 API로 요청해 결과를 `generated.json`에 모읍니다.
3. 검토자 두 명이 각자 `review-template.json`을 복사해 `review-a.json`, `review-b.json`으로 채웁니다.
4. 두 점수 중 **낮은 쪽**을 채택해 집계합니다. 기존 사람 평가 하네스와 같은 방식입니다.
5. 결과를 `summary.json`에 남깁니다.

## 주의

- 이 폴더는 `pytest` 대상이 아닙니다. 실제 공급자 호출과 사람 채점이 필요합니다.
- `tests/fixtures/ai_mvp_release_v1.json`의 `suites`에 골든셋을 추가하지 마세요.
  `_catalog_case_count`가 모르는 규칙을 만나 예외를 던지고 **기존 출시 게이트 전체가 깨집니다.**
- `generated.json`은 실행마다 쌓이므로 저장소가 무거워지면 `.gitignore` 처리를 검토하세요.
  단, `review-*.json`과 `summary.json`은 기록으로 남기는 편이 좋습니다.
