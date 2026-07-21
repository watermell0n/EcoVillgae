import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useRoomSessionStore } from '../../../stores/roomSession'
import Day2FundStageView from './Day2FundStageView.vue'

const fundApi = vi.hoisted(() => ({
  castStudentVote: vi.fn(),
  getStudentCurrentVote: vi.fn(),
  getStudentFund: vi.fn(),
  getStudentFundCostOptions: vi.fn(),
}))

vi.mock('../../../features/day2/api/fundVote', () => fundApi)

describe('Day2FundStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    fundApi.getStudentFund.mockResolvedValue({
      roomId: 'room-1',
      balance: 50,
      totalIncomeTax: 40,
      totalFinancialTax: 20,
      totalAdditions: 10,
      totalExpenses: 10,
      appliedExpense: null,
    })
    fundApi.getStudentCurrentVote.mockResolvedValue({
      voteSessionId: 'vote-1',
      roomId: 'room-1',
      question: '남은 공동기금은 어디에 사용할까요?',
      fundBalanceSnapshot: 50,
      status: 'DECIDED',
      eligibleStudentCount: 3,
      submittedCount: 3,
      hasVoted: true,
      selectedOptionId: 'option-1',
      decidedOptionId: 'option-1',
      openedAt: '2026-07-17T00:00:00Z',
      closedAt: '2026-07-17T00:05:00Z',
      pendingStudents: [],
      options: [
        {
          optionId: 'option-1',
          title: '다음 활동을 위해 저축',
          description: '다음 공동 활동을 위해 남겨요.',
          requiredAmount: 0,
          affordable: true,
          voteCount: 2,
          tiedForFirst: false,
          decided: true,
        },
        {
          optionId: 'option-2',
          title: '간식 나눔',
          description: '함께 간식을 나눠요.',
          requiredAmount: 40,
          affordable: true,
          voteCount: 1,
          tiedForFirst: false,
          decided: false,
        },
      ],
    })
    fundApi.getStudentFundCostOptions.mockResolvedValue([])
  })

  it('shows a read-only vote result before the final result stage', async () => {
    const roomSessionStore = useRoomSessionStore()
    roomSessionStore.studentState = {
      currentStage: 'DAY2_FUND_RESULT',
      syncVersion: 1,
    } as never

    const wrapper = mount(Day2FundStageView)
    await flushPromises()

    expect(fundApi.getStudentCurrentVote).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('우리 반 공동기금 투표 결과')
    expect(wrapper.text()).toContain('다음 활동을 위해 저축')
    expect(wrapper.text()).toContain('2표를 받아 우리 반의 선택으로 결정되었어요')
    expect(wrapper.find('input').exists()).toBe(false)

    wrapper.unmount()
  })

  it('hides decided vote counts until the teacher opens the result stage', async () => {
    const roomSessionStore = useRoomSessionStore()
    roomSessionStore.studentState = {
      currentStage: 'DAY2_FUND_VOTE',
      syncVersion: 1,
    } as never

    const wrapper = mount(Day2FundStageView)
    await flushPromises()

    expect(wrapper.text()).toContain(
      '소득세 40비빔 + 금융세 20비빔 + 추가 기금 10비빔 − 공동비용 10비빔',
    )
    expect(wrapper.find('#student-vote-result-title').exists()).toBe(false)
    expect(wrapper.find('.student-vote-options b').exists()).toBe(false)
    expect(wrapper.find('.student-vote-options .is-decided').exists()).toBe(false)
    expect(wrapper.text()).toContain('선생님이 결과를 공개할 때까지 기다려 주세요')

    wrapper.unmount()
  })

  it('clears a previous radio selection when the fund stage is opened again', async () => {
    fundApi.getStudentCurrentVote.mockResolvedValue({
      voteSessionId: 'vote-1',
      roomId: 'room-1',
      question: '어디에 사용할까요?',
      fundBalanceSnapshot: 50,
      status: 'OPEN',
      eligibleStudentCount: 3,
      submittedCount: 0,
      eligible: true,
      hasVoted: false,
      selectedOptionId: null,
      decidedOptionId: null,
      openedAt: null,
      closedAt: null,
      pendingStudents: [],
      options: [{
        optionId: 'option-1', title: '간식', description: '함께 나눠요.',
        requiredAmount: 40, affordable: true, voteCount: null, tiedForFirst: false, decided: false,
      }],
    })
    const roomSessionStore = useRoomSessionStore()
    roomSessionStore.studentState = { currentStage: 'DAY2_FUND_VOTE', syncVersion: 1 } as never
    const wrapper = mount(Day2FundStageView)
    await flushPromises()

    await wrapper.get('input[type="radio"]').setValue(true)
    expect((wrapper.get('input[type="radio"]').element as HTMLInputElement).checked).toBe(true)
    roomSessionStore.studentState = { currentStage: 'DAY2_FUND_COST', syncVersion: 2 } as never
    await flushPromises()
    roomSessionStore.studentState = { currentStage: 'DAY2_FUND_VOTE', syncVersion: 3 } as never
    await flushPromises()

    expect((wrapper.get('input[type="radio"]').element as HTMLInputElement).checked).toBe(false)
    wrapper.unmount()
  })

  it('keeps an unsubmitted radio selection while the same vote is polled', async () => {
    vi.useFakeTimers()
    fundApi.getStudentCurrentVote.mockResolvedValue({
      voteSessionId: 'vote-1',
      roomId: 'room-1',
      question: '어디에 사용할까요?',
      fundBalanceSnapshot: 50,
      status: 'OPEN',
      eligibleStudentCount: 3,
      submittedCount: 0,
      eligible: true,
      hasVoted: false,
      selectedOptionId: null,
      decidedOptionId: null,
      openedAt: '2026-07-17T00:00:00Z',
      closedAt: null,
      pendingStudents: [],
      options: [{
        optionId: 'option-1', title: '간식', description: '함께 나눠요.',
        requiredAmount: 40, affordable: true, voteCount: null, tiedForFirst: false, decided: false,
      }],
    })
    const roomSessionStore = useRoomSessionStore()
    roomSessionStore.studentState = { currentStage: 'DAY2_FUND_VOTE', syncVersion: 1 } as never
    const wrapper = mount(Day2FundStageView)
    await flushPromises()

    const option = wrapper.get('input[type="radio"]')
    await option.setValue(true)
    await vi.advanceTimersByTimeAsync(2_000)
    await flushPromises()

    expect(fundApi.getStudentCurrentVote).toHaveBeenCalledTimes(2)
    expect((option.element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
    vi.useRealTimers()
  })
})
