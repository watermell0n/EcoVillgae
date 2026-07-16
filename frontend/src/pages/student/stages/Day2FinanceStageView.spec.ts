import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useRoomSessionStore } from '../../../stores/roomSession'
import Day2FinanceStageView from './Day2FinanceStageView.vue'

const financeApi = vi.hoisted(() => ({
  getStudentDay2FinanceResult: vi.fn(),
  getTeacherDay2FinancePreview: vi.fn(),
  settleTeacherDay2Finance: vi.fn(),
}))

vi.mock('../../../features/day2/api/finance', () => financeApi)

describe('Day2FinanceStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    financeApi.getStudentDay2FinanceResult.mockResolvedValue({
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      settlementStatus: 'COMPLETED',
      resultAvailable: true,
      walletBefore: 25,
      depositPrincipal: 20,
      depositInterest: 1,
      investmentPrincipal: 20,
      investmentProfit: 1,
      investmentValue: 21,
      taxableProfit: 2,
      financialTax: 0,
      walletAfter: 67,
      allocations: [
        {
          assetId: 'asset-1',
          assetName: '튼튼 채권',
          riskLevel: 'LOW',
          principal: 20,
          returnBps: 200,
          profit: 0,
          finalValue: 20,
        },
      ],
      completedAt: '2026-07-17T00:00:00Z',
    })
    const roomStore = useRoomSessionStore()
    roomStore.studentState = {
      roomId: 'room-1',
      roomStatus: 'DAY2_ACTIVE',
      currentDay: 2,
      currentStage: 'DAY2_RESULT_GUIDED',
      mode: 'GUIDED',
      walletBalance: 67,
      modules: [],
      syncVersion: 20,
      changed: true,
    }
  })

  it('shows restored assets, finance tax, and the final wallet balance', async () => {
    const wrapper = mount(Day2FinanceStageView)
    await flushPromises()

    expect(financeApi.getStudentDay2FinanceResult).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('내 예금과 투자 결과를 확인해요.')
    expect(wrapper.text()).toContain('예금 원금')
    expect(wrapper.text()).toContain('투자 손익')
    expect(wrapper.text()).toContain('금융세')
    expect(wrapper.text()).toContain('67비빔')
    expect(wrapper.text()).toContain('튼튼 채권')
  })

  it('does not show predicted amounts before settlement is completed', async () => {
    financeApi.getStudentDay2FinanceResult.mockResolvedValue({
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      settlementStatus: 'NOT_STARTED',
      resultAvailable: false,
      walletBefore: 0,
      depositPrincipal: 0,
      depositInterest: 0,
      investmentPrincipal: 0,
      investmentProfit: 0,
      investmentValue: 0,
      taxableProfit: 0,
      financialTax: 0,
      walletAfter: 0,
      allocations: [],
      completedAt: null,
    })

    const wrapper = mount(Day2FinanceStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('금융 정산을 기다리고 있어요.')
    expect(wrapper.text()).not.toContain('예상 금액')
    expect(wrapper.text()).not.toContain('예금 원금')
    expect(wrapper.text()).not.toContain('0비빔')
  })
})
