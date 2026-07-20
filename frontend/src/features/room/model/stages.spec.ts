import { describe, expect, it } from 'vitest'

import {
  getAdjacentStages,
  getRoomStage,
  getStageNavigationBlockReason,
  isNextStageDisabled,
  ROOM_STAGES,
} from './stages'

describe('room stages', () => {
  it('keeps the complete two-day stage order from the API specification', () => {
    expect(ROOM_STAGES).toHaveLength(29)
    expect(ROOM_STAGES[0]?.id).toBe('DAY1_LOBBY')
    expect(ROOM_STAGES.at(-1)?.id).toBe('DAY2_COMPLETED')
  })

  it('returns only the previous and next stage for navigation', () => {
    expect(getAdjacentStages('DAY1_QUIZ').previous?.id).toBe('DAY1_PROFILE')
    expect(getAdjacentStages('DAY1_QUIZ').next?.id).toBe('DAY1_QUIZ_RESULT')
    expect(getAdjacentStages('DAY1_QUIZ_RESULT').next?.id).toBe('DAY1_INCOME_SETTLEMENT')
    expect(getAdjacentStages('DAY1_PAYSLIP_GUIDED').next?.id).toBe('DAY1_KNOWLEDGE_GUIDED')
    expect(getAdjacentStages('DAY1_KNOWLEDGE_GUIDED').next?.id).toBe('DAY1_FINANCE_GUIDED')
    expect(getAdjacentStages('DAY1_FINANCE_GUIDED').next?.id).toBe('DAY1_SHOP_GUIDED')
    expect(getAdjacentStages('DAY2_QUIZ').next?.id).toBe('DAY2_QUIZ_RESULT')
    expect(getAdjacentStages('DAY2_QUIZ_RESULT').next?.id).toBe('DAY2_QUIZ_SETTLEMENT')
    expect(getAdjacentStages('DAY1_LOBBY').previous).toBeUndefined()
  })

  it('provides a Korean label and description for every stage', () => {
    expect(getRoomStage('DAY1_QUIZ')).toMatchObject({
      label: '독서 퀴즈',
      description: '10문제 독서 퀴즈로 문제당 5비빔의 정답수당을 얻습니다.',
    })
    expect(getRoomStage('DAY2_QUIZ')).toMatchObject({
      label: '경제 퀴즈',
      description: '10문제 경제 퀴즈로 배운 내용을 확인합니다.',
    })
    expect(getRoomStage('DAY1_FREE_ACTIVITY')).toMatchObject({
      label: '자유 경제활동',
      day: 1,
    })
    expect(getRoomStage('DAY1_PAYSLIP_GUIDED')?.label).toBe('월급명세서 확인')
    expect(getRoomStage('DAY1_KNOWLEDGE_GUIDED')?.label).toBe('월급명세서 학습')
    expect(getRoomStage('DAY1_FINANCE_GUIDED')?.label).toBe('금융지식 교육')
    expect(getRoomStage('DAY2_TAX_INFLATION_GUIDED')).toMatchObject({
      label: '금융지식 교육',
      description: '금융세와 인플레이션을 2페이지로 배웁니다.',
    })
    expect(ROOM_STAGES.every((stage) => stage.label && stage.description)).toBe(true)
  })

  it('enables navigation after the day 2 quiz reward settlement completes', () => {
    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_QUIZ_SETTLEMENT',
        hasNextStage: true,
        changingStage: false,
        day2QuizRewardSettlementStatus: 'COMPLETED',
      }),
    ).toBe(false)
  })

  it('keeps settlement and active quiz stages blocked until their own prerequisite completes', () => {
    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_QUIZ_SETTLEMENT',
        hasNextStage: true,
        changingStage: false,
        day2QuizRewardSettlementStatus: 'NOT_STARTED',
      }),
    ).toBe(true)

    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_QUIZ',
        hasNextStage: true,
        changingStage: false,
        quizStatus: 'OPEN',
      }),
    ).toBe(true)
  })

  it('requires a prepared fund vote and a decided result before advancing', () => {
    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_FUND_COST',
        hasNextStage: true,
        changingStage: false,
        fundExpenseApplied: false,
        votePrepared: false,
      }),
    ).toBe(true)

    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_FUND_COST',
        hasNextStage: true,
        changingStage: false,
        fundExpenseApplied: true,
        votePrepared: true,
      }),
    ).toBe(false)

    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_FUND_COST',
        hasNextStage: true,
        changingStage: false,
        fundExpenseApplied: false,
        votePrepared: true,
      }),
    ).toBe(false)

    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_FUND_VOTE',
        hasNextStage: true,
        changingStage: false,
        voteStatus: 'CLOSED',
      }),
    ).toBe(true)

    expect(
      isNextStageDisabled({
        currentStage: 'DAY2_FUND_VOTE',
        hasNextStage: true,
        changingStage: false,
        voteStatus: 'DECIDED',
      }),
    ).toBe(false)

    expect(getAdjacentStages('DAY2_FUND_VOTE').next?.id).toBe('DAY2_FUND_RESULT')
    expect(getAdjacentStages('DAY2_FUND_RESULT').next?.id).toBe('DAY2_FINAL_RESULT')
  })

  it('allows direct same-day selection while keeping explicit day boundaries', () => {
    expect(getStageNavigationBlockReason({
      currentStage: 'DAY1_PAYSLIP_GUIDED',
      targetStage: 'DAY1_FINANCE_GUIDED',
      changingStage: false,
      day1IncomeSettlementStatus: 'COMPLETED',
    })).toBeUndefined()

    expect(getStageNavigationBlockReason({
      currentStage: 'DAY1_SUMMARY',
      targetStage: 'DAY2_LOGIN',
      changingStage: false,
    })).toContain('전용 시작 버튼')
  })

  it('blocks direct selection that would bypass an unfinished checkpoint', () => {
    expect(getStageNavigationBlockReason({
      currentStage: 'DAY1_QUIZ',
      targetStage: 'DAY1_INCOME_SETTLEMENT',
      changingStage: false,
      quizStatus: 'CLOSED',
    })).toContain('퀴즈 결과')

    expect(getStageNavigationBlockReason({
      currentStage: 'DAY2_QUIZ',
      targetStage: 'DAY2_QUIZ_SETTLEMENT',
      changingStage: false,
      quizStatus: 'CLOSED',
    })).toContain('퀴즈 결과')

    expect(getStageNavigationBlockReason({
      currentStage: 'DAY2_SETTLEMENT_WAIT',
      targetStage: 'DAY2_RESULT_GUIDED',
      changingStage: false,
      day2FinanceSettlementStatus: 'NOT_STARTED',
    })).toContain('금융 정산')

    expect(getStageNavigationBlockReason({
      currentStage: 'DAY2_FUND_COST',
      targetStage: 'DAY2_FINAL_RESULT',
      changingStage: false,
      votePrepared: true,
      voteStatus: 'OPEN',
    })).toContain('투표 결과')
  })
})
