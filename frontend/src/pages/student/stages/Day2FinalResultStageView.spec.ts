import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useRoomSessionStore } from '../../../stores/roomSession'
import Day2FinalResultStageView from './Day2FinalResultStageView.vue'

const finalResultApi = vi.hoisted(() => ({
  getStudentFinalResult: vi.fn(),
}))

vi.mock('../../../features/day2/api/finalResult', () => finalResultApi)

const resultFixture = {
  roomId: 'room-1',
  studentId: 'student-1',
  participantNumber: 1,
  nickname: '비빔이',
  grossIncome: 100,
  incomeTax: 10,
  netIncome: 90,
  depositPrincipal: 20,
  depositInterest: 1,
  investmentPrincipal: 20,
  investmentProfit: 2,
  financialTax: 0,
  day2QuizReward: 15,
  totalPurchaseAmount: 30,
  finalWalletBalance: 58,
  purchases: [
    {
      productId: 'product-1',
      productName: '연필 세트',
      quantity: 2,
      totalPrice: 22,
    },
  ],
  voteResult: null,
  surveyUrl: 'https://example.com/survey',
}

describe('Day2FinalResultStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    finalResultApi.getStudentFinalResult.mockResolvedValue(structuredClone(resultFixture))
  })

  it('waits to expose the survey link while the class is on the final result stage', async () => {
    useRoomSessionStore().studentState = {
      currentStage: 'DAY2_FINAL_RESULT',
      syncVersion: 1,
    } as never

    const wrapper = mount(Day2FinalResultStageView)
    await flushPromises()

    expect(wrapper.find('.survey-link-button').exists()).toBe(false)
    expect(wrapper.text()).toContain('선생님이 마무리 설문을 열어 줄 때까지 기다려 주세요.')
    expect(wrapper.findAll('.final-purchase-list li')).toHaveLength(1)
    expect(wrapper.text()).toContain('연필 세트 × 2')
    expect(wrapper.text()).toContain('22비빔')
  })

  it.each(['DAY2_SURVEY', 'DAY2_COMPLETED'])('shows the survey link at %s', async (stage) => {
    useRoomSessionStore().studentState = {
      currentStage: stage,
      syncVersion: 1,
    } as never

    const wrapper = mount(Day2FinalResultStageView)
    await flushPromises()

    expect(wrapper.get<HTMLAnchorElement>('.survey-link-button').attributes('href'))
      .toBe('https://example.com/survey')
    expect(wrapper.find('.survey-wait-notice').exists()).toBe(false)
  })
})
