export interface RoomStageDefinition {
  id: string
  day: 1 | 2
  label: string
  description: string
}

export interface NextStageGuardOptions {
  currentStage?: string
  hasNextStage: boolean
  changingStage: boolean
  quizStatus?: string
  day2FinanceSettlementStatus?: string
  day2QuizRewardSettlementStatus?: string
}

export const ROOM_STAGES: readonly RoomStageDefinition[] = [
  { id: 'DAY1_LOBBY', day: 1, label: '입장 대기', description: '학생 접속을 확인하고 수업을 준비합니다.' },
  { id: 'DAY1_PROFILE', day: 1, label: '활동 안내', description: '오늘의 역할과 활동 순서를 안내합니다.' },
  { id: 'DAY1_QUIZ', day: 1, label: '월급 퀴즈', description: '6문제 퀴즈로 참여수당과 정답수당을 얻습니다.' },
  { id: 'DAY1_INCOME_SETTLEMENT', day: 1, label: '소득·세금 정산', description: '역할 소득과 퀴즈 보상, 소득세를 계산합니다.' },
  { id: 'DAY1_PAYSLIP_GUIDED', day: 1, label: '월급명세서 교육', description: '각자의 월급명세서를 선생님과 함께 확인합니다.' },
  { id: 'DAY1_KNOWLEDGE_GUIDED', day: 1, label: '금융지식 교육', description: '예금과 투자, 위험과 수익을 배웁니다.' },
  { id: 'DAY1_SHOP_GUIDED', day: 1, label: '상점 안내', description: '상품 가격과 재고, 구매 방법을 확인합니다.' },
  { id: 'DAY1_FINANCE_GUIDED', day: 1, label: '예금·투자 안내', description: '금융 활동 규칙과 확정 방법을 확인합니다.' },
  { id: 'DAY1_FREE_ACTIVITY', day: 1, label: '자유 경제활동', description: '상점, 예금, 투자를 자유롭게 이용합니다.' },
  { id: 'DAY1_SUMMARY', day: 1, label: '1일차 요약', description: '오늘의 활동과 자산 변화를 정리합니다.' },
  { id: 'DAY1_CLOSED', day: 1, label: '1일차 마감', description: '1일차 거래를 종료하고 결과를 저장합니다.' },
  { id: 'DAY2_LOGIN', day: 2, label: '2일차 접속', description: '기존 계정으로 접속해 저장된 정보를 불러옵니다.' },
  { id: 'DAY2_SETTLEMENT_WAIT', day: 2, label: '금융 정산 대기', description: '예금과 투자 결과 정산을 기다립니다.' },
  { id: 'DAY2_FINANCE_SETTLEMENT', day: 2, label: '금융 결과 정산', description: '예금 이자와 투자 손익을 반영합니다.' },
  { id: 'DAY2_RESULT_GUIDED', day: 2, label: '금융 결과 교육', description: '개인별 금융 결과를 선생님과 확인합니다.' },
  { id: 'DAY2_QUIZ', day: 2, label: '2일차 퀴즈', description: '배운 내용을 확인하는 퀴즈를 진행합니다.' },
  { id: 'DAY2_QUIZ_SETTLEMENT', day: 2, label: '퀴즈 보상 정산', description: '참여수당과 정답수당을 반영합니다.' },
  { id: 'DAY2_TAX_INFLATION_GUIDED', day: 2, label: '금융세·인플레이션 교육', description: '수익에 붙는 세금과 물가 변화를 배웁니다.' },
  { id: 'DAY2_INFLATION_APPLY', day: 2, label: '물가상승 적용', description: '상점 가격에 물가상승률을 반영합니다.' },
  { id: 'DAY2_FINAL_SHOP', day: 2, label: '최종 상점 활동', description: '변경된 가격으로 마지막 소비 활동을 합니다.' },
  { id: 'DAY2_FUND_COST', day: 2, label: '공동기금 비용 확인', description: '공동기금으로 선택할 수 있는 활동을 확인합니다.' },
  { id: 'DAY2_FUND_VOTE', day: 2, label: '공동기금 투표', description: '공동기금 사용처를 함께 선택합니다.' },
  { id: 'DAY2_FINAL_RESULT', day: 2, label: '최종 결과', description: '2일 동안의 경제활동 결과를 확인합니다.' },
  { id: 'DAY2_SURVEY', day: 2, label: '마무리 설문', description: '외부 설문으로 수업 경험을 돌아봅니다.' },
  { id: 'DAY2_COMPLETED', day: 2, label: '수업 종료', description: '모든 활동을 종료하고 결과를 저장합니다.' },
]

export function getRoomStage(stageId?: string) {
  return ROOM_STAGES.find((stage) => stage.id === stageId)
}

export function getAdjacentStages(stageId?: string) {
  const currentIndex = ROOM_STAGES.findIndex((stage) => stage.id === stageId)
  return {
    previous: currentIndex > 0 ? ROOM_STAGES[currentIndex - 1] : undefined,
    next:
      currentIndex >= 0 && currentIndex < ROOM_STAGES.length - 1
        ? ROOM_STAGES[currentIndex + 1]
        : undefined,
  }
}

export function isNextStageDisabled({
  currentStage,
  hasNextStage,
  changingStage,
  quizStatus,
  day2FinanceSettlementStatus,
  day2QuizRewardSettlementStatus,
}: NextStageGuardOptions) {
  if (!hasNextStage || changingStage) {
    return true
  }

  if (currentStage === 'DAY1_SUMMARY' || currentStage === 'DAY1_CLOSED') {
    return true
  }

  if (
    (currentStage === 'DAY1_QUIZ' || currentStage === 'DAY2_QUIZ') &&
    quizStatus !== 'CLOSED'
  ) {
    return true
  }

  if (
    currentStage === 'DAY2_FINANCE_SETTLEMENT' &&
    day2FinanceSettlementStatus !== 'COMPLETED'
  ) {
    return true
  }

  return (
    currentStage === 'DAY2_QUIZ_SETTLEMENT' &&
    day2QuizRewardSettlementStatus !== 'COMPLETED'
  )
}
