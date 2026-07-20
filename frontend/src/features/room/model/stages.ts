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
  fundExpenseApplied?: boolean
  votePrepared?: boolean
  voteStatus?: string
}

export interface StageNavigationGuardOptions extends Omit<NextStageGuardOptions, 'hasNextStage'> {
  targetStage?: string
  day1IncomeSettlementStatus?: string
  inflationApplied?: boolean
}

export const ROOM_STAGES: readonly RoomStageDefinition[] = [
  { id: 'DAY1_LOBBY', day: 1, label: '입장 대기', description: '학생 접속을 확인하고 수업을 준비합니다.' },
  { id: 'DAY1_PROFILE', day: 1, label: '활동 안내', description: '오늘의 역할과 활동 순서를 안내합니다.' },
  { id: 'DAY1_QUIZ', day: 1, label: '독서 퀴즈', description: '10문제 독서 퀴즈로 문제당 5비빔의 정답수당을 얻습니다.' },
  { id: 'DAY1_QUIZ_RESULT', day: 1, label: '퀴즈 결과 확인', description: '문항별 정답과 학급 퀴즈 결과를 함께 확인합니다.' },
  { id: 'DAY1_INCOME_SETTLEMENT', day: 1, label: '소득·세금 정산', description: '역할 기본 월급과 퀴즈 정답수당에서 소득세 10%를 계산합니다.' },
  { id: 'DAY1_PAYSLIP_GUIDED', day: 1, label: '월급명세서 확인', description: '각자의 총소득과 세금, 실제 실수령액을 확인합니다.' },
  { id: 'DAY1_KNOWLEDGE_GUIDED', day: 1, label: '월급명세서 학습', description: '월급과 실수령액, 세금과 활용 방법을 3페이지로 배웁니다.' },
  { id: 'DAY1_FINANCE_GUIDED', day: 1, label: '금융지식 교육', description: '소비와 저축부터 투자와 분산투자까지 5페이지로 배웁니다.' },
  { id: 'DAY1_SHOP_GUIDED', day: 1, label: '상점 안내', description: '상품 가격과 재고, 구매 방법을 확인합니다.' },
  { id: 'DAY1_FREE_ACTIVITY_GUIDED', day: 1, label: '자유 경제활동 안내', description: '학생 화면을 잠근 상태에서 상점·예금·투자 이용 방법을 함께 살펴봅니다.' },
  { id: 'DAY1_FREE_ACTIVITY', day: 1, label: '자유 경제활동', description: '상점, 예금, 투자를 자유롭게 이용합니다.' },
  { id: 'DAY1_SUMMARY', day: 1, label: '1일차 요약', description: '오늘의 활동과 자산 변화를 정리합니다.' },
  { id: 'DAY1_CLOSED', day: 1, label: '1일차 마감', description: '1일차 거래를 종료하고 결과를 저장합니다.' },
  { id: 'DAY2_LOGIN', day: 2, label: '2일차 접속', description: '기존 계정으로 접속해 저장된 정보를 불러옵니다.' },
  { id: 'DAY2_SETTLEMENT_WAIT', day: 2, label: '금융 정산 대기', description: '예금과 투자 결과 정산을 기다립니다.' },
  { id: 'DAY2_FINANCE_SETTLEMENT', day: 2, label: '금융 결과 정산', description: '예금 이자와 투자 손익을 반영합니다.' },
  { id: 'DAY2_RESULT_GUIDED', day: 2, label: '금융 결과 교육', description: '개인별 금융 결과를 선생님과 확인합니다.' },
  { id: 'DAY2_QUIZ', day: 2, label: '경제 퀴즈', description: '10문제 경제 퀴즈로 배운 내용을 확인합니다.' },
  { id: 'DAY2_QUIZ_RESULT', day: 2, label: '퀴즈 결과 확인', description: '문항별 정답과 학급 퀴즈 결과를 함께 확인합니다.' },
  { id: 'DAY2_QUIZ_SETTLEMENT', day: 2, label: '퀴즈 보상 돈 계산', description: '문제당 5비빔의 정답수당에서 소득세 10%를 뺀 금액을 반영합니다.' },
  { id: 'DAY2_TAX_INFLATION_GUIDED', day: 2, label: '금융지식 교육', description: '금융세와 인플레이션을 2페이지로 배웁니다.' },
  { id: 'DAY2_INFLATION_APPLY', day: 2, label: '물가상승 적용', description: '상점 가격에 물가상승률을 반영합니다.' },
  { id: 'DAY2_FINAL_SHOP', day: 2, label: '최종 상점 활동', description: '변경된 가격으로 마지막 소비 활동을 합니다.' },
  { id: 'DAY2_FUND_COST', day: 2, label: '공동기금 비용 확인', description: '공동기금으로 선택할 수 있는 활동을 확인합니다.' },
  { id: 'DAY2_FUND_VOTE', day: 2, label: '공동기금 투표', description: '공동기금 사용처를 함께 선택합니다.' },
  { id: 'DAY2_FUND_RESULT', day: 2, label: '공동기금 투표 결과', description: '우리 반이 선택한 공동기금 사용처와 득표 결과를 확인합니다.' },
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

function movesForwardAcross(currentStage: string, targetStage: string, checkpoint: string) {
  const currentIndex = ROOM_STAGES.findIndex((stage) => stage.id === currentStage)
  const targetIndex = ROOM_STAGES.findIndex((stage) => stage.id === targetStage)
  const checkpointIndex = ROOM_STAGES.findIndex((stage) => stage.id === checkpoint)
  return currentIndex <= checkpointIndex && targetIndex > checkpointIndex
}

export function getStageNavigationBlockReason({
  currentStage,
  targetStage,
  changingStage,
  quizStatus,
  day1IncomeSettlementStatus,
  day2FinanceSettlementStatus,
  day2QuizRewardSettlementStatus,
  fundExpenseApplied,
  votePrepared,
  voteStatus,
  inflationApplied,
}: StageNavigationGuardOptions) {
  const current = getRoomStage(currentStage)
  const target = getRoomStage(targetStage)
  if (!current || !target) return '단계 정보를 확인할 수 없습니다.'
  if (changingStage) return '단계를 변경하고 있습니다.'
  if (current.id === target.id) return '현재 진행 중인 단계입니다.'
  if (current.id === 'DAY1_CLOSED' || target.id === 'DAY2_LOGIN') {
    return '2일차는 전용 시작 버튼으로 시작해 주세요.'
  }
  if (current.id === 'DAY2_COMPLETED') return '종료된 수업의 단계는 변경할 수 없습니다.'
  if (target.id === 'DAY1_CLOSED') return '1일차 마감은 전용 마감 버튼으로 진행해 주세요.'
  if (target.id === 'DAY2_COMPLETED') return '수업 종료는 전용 종료 버튼으로 진행해 주세요.'
  if (current.day !== target.day) return '다른 일차로는 단계 목록에서 이동할 수 없습니다.'

  if (
    (movesForwardAcross(current.id, target.id, 'DAY1_QUIZ')
      || movesForwardAcross(current.id, target.id, 'DAY2_QUIZ'))
    && quizStatus !== 'CLOSED'
  ) {
    return '퀴즈를 마감한 뒤 이후 단계로 이동해 주세요.'
  }
  const day1QuizResultIndex = ROOM_STAGES.findIndex((stage) => stage.id === 'DAY1_QUIZ_RESULT')
  const currentIndex = ROOM_STAGES.indexOf(current)
  const targetIndex = ROOM_STAGES.indexOf(target)
  if (currentIndex < day1QuizResultIndex && targetIndex > day1QuizResultIndex) {
    return '1일차 퀴즈 결과를 확인한 뒤 소득 정산 단계로 이동해 주세요.'
  }
  const day2QuizResultIndex = ROOM_STAGES.findIndex((stage) => stage.id === 'DAY2_QUIZ_RESULT')
  if (currentIndex < day2QuizResultIndex && targetIndex > day2QuizResultIndex) {
    return '경제 퀴즈 결과를 확인한 뒤 보상 정산 단계로 이동해 주세요.'
  }
  if (
    movesForwardAcross(current.id, target.id, 'DAY1_INCOME_SETTLEMENT')
    && day1IncomeSettlementStatus !== 'COMPLETED'
  ) {
    return '소득 정산을 완료한 뒤 이후 단계로 이동해 주세요.'
  }
  if (
    movesForwardAcross(current.id, target.id, 'DAY2_FINANCE_SETTLEMENT')
    && day2FinanceSettlementStatus !== 'COMPLETED'
  ) {
    return '금융 정산을 완료한 뒤 이후 단계로 이동해 주세요.'
  }
  if (
    movesForwardAcross(current.id, target.id, 'DAY2_QUIZ_SETTLEMENT')
    && day2QuizRewardSettlementStatus !== 'COMPLETED'
  ) {
    return '퀴즈 보상 정산을 완료한 뒤 이후 단계로 이동해 주세요.'
  }
  if (
    movesForwardAcross(current.id, target.id, 'DAY2_INFLATION_APPLY')
    && !inflationApplied
  ) {
    return '물가상승을 적용한 뒤 이후 단계로 이동해 주세요.'
  }
  if (movesForwardAcross(current.id, target.id, 'DAY2_FUND_COST') && !votePrepared) {
    return '공동기금 투표 선택지를 준비한 뒤 이후 단계로 이동해 주세요.'
  }
  if (movesForwardAcross(current.id, target.id, 'DAY2_FUND_VOTE') && voteStatus !== 'DECIDED') {
    return '공동기금 투표 결과를 확정한 뒤 이후 단계로 이동해 주세요.'
  }

  if (targetIndex < currentIndex) {
    if (
      targetIndex < ROOM_STAGES.findIndex((stage) => stage.id === 'DAY1_INCOME_SETTLEMENT')
      && current.day === 1
      && day1IncomeSettlementStatus === 'COMPLETED'
    ) {
      return '완료된 소득 정산 이전 단계로는 돌아갈 수 없습니다.'
    }
    if (voteStatus === 'DECIDED'
      && targetIndex < ROOM_STAGES.findIndex((stage) => stage.id === 'DAY2_FUND_RESULT')) {
      return '확정된 공동기금 투표 결과 이전 단계로는 돌아갈 수 없습니다.'
    }
    if (voteStatus && voteStatus !== 'READY'
      && targetIndex < ROOM_STAGES.findIndex((stage) => stage.id === 'DAY2_FUND_VOTE')) {
      return '투표를 시작한 뒤에는 공동비용 이전 단계로 돌아갈 수 없습니다.'
    }
    if ((votePrepared || fundExpenseApplied)
      && targetIndex < ROOM_STAGES.findIndex((stage) => stage.id === 'DAY2_FUND_COST')) {
      return '공동기금 처리 후에는 최종 상점 이전 단계로 돌아갈 수 없습니다.'
    }
  }
  return undefined
}

export function isNextStageDisabled({
  currentStage,
  hasNextStage,
  changingStage,
  quizStatus,
  day2FinanceSettlementStatus,
  day2QuizRewardSettlementStatus,
  votePrepared,
  voteStatus,
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

  if (currentStage === 'DAY2_FUND_COST' && !votePrepared) {
    return true
  }

  if (currentStage === 'DAY2_FUND_VOTE' && voteStatus !== 'DECIDED') {
    return true
  }

  if (currentStage === 'DAY2_SURVEY') {
    return true
  }

  return (
    currentStage === 'DAY2_QUIZ_SETTLEMENT' &&
    day2QuizRewardSettlementStatus !== 'COMPLETED'
  )
}
