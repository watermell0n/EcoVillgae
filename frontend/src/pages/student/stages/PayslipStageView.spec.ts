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
      roleName: '라이츄',
      quizSubmitted: true,
      correctCount: 3,
      roleIncome: 150,
      participationReward: 0,
      correctReward: 15,
      grossIncome: 165,
      incomeTax: 16,
      netIncome: 149,
      walletBalance: 149,
      issuedAt: '2026-07-16T00:00:00Z',
    })
  })

  it('shows the student income, tax, net income, and wallet balance', async () => {
    const wrapper = mount(PayslipStageView)
    await flushPromises()

    expect(settlementApi.getDay1Payslip).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('받은 돈과 세금을 확인해요')
    expect(wrapper.text()).toContain('라이츄 소득')
    expect(wrapper.text()).toContain('3문제')
    expect(wrapper.text()).toContain('퀴즈 참여수당')
    expect(wrapper.text()).toContain('내 지갑 기록에 저장됐어요')
    expect(wrapper.text()).toContain('165비빔')
    expect(wrapper.text()).toContain('16비빔')
    expect(wrapper.text()).toContain('149비빔')
  })
})
