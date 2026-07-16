import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PayslipStageView from './PayslipStageView.vue'

const settlementApi = vi.hoisted(() => ({
  getDay1Payslip: vi.fn(),
}))

vi.mock('../../../features/settlement/api/settlement', () => settlementApi)

describe('PayslipStageView', () => {
  beforeEach(() => {
    settlementApi.getDay1Payslip.mockResolvedValue({
      settlementRunId: 'run-1',
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      quizSubmitted: true,
      correctCount: 3,
      roleIncome: 100,
      participationReward: 10,
      correctReward: 30,
      grossIncome: 140,
      incomeTax: 14,
      netIncome: 126,
      walletBalance: 126,
      issuedAt: '2026-07-16T00:00:00Z',
    })
  })

  it('shows the student income, tax, net income, and wallet balance', async () => {
    const wrapper = mount(PayslipStageView)
    await flushPromises()

    expect(settlementApi.getDay1Payslip).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('비빔이님의 소득을 확인해요.')
    expect(wrapper.text()).toContain('3문제 정답')
    expect(wrapper.text()).toContain('140비빔')
    expect(wrapper.text()).toContain('14비빔')
    expect(wrapper.text()).toContain('126비빔')
  })
})
