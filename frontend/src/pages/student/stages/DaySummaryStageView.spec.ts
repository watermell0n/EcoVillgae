import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DaySummaryStageView from './DaySummaryStageView.vue'

const day1Api = vi.hoisted(() => ({
  getStudentDay1Summary: vi.fn(),
  getTeacherDay1Summary: vi.fn(),
  getDay1ClosePreview: vi.fn(),
}))

vi.mock('../../../features/day1/api/day1', () => day1Api)

describe('DaySummaryStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    day1Api.getStudentDay1Summary.mockResolvedValue({
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      roomStage: 'DAY1_SUMMARY',
      walletBalance: 45,
      depositPrincipal: 20,
      expectedDepositInterest: 1,
      investmentPrincipal: 20,
      purchaseTotal: 25,
      totalAssets: 85,
      investments: [
        { assetId: 'asset-1', assetName: '안심 채권', riskLevel: 'LOW', amount: 20 },
      ],
      purchases: [
        { purchaseId: 'purchase-1', productName: '연필 세트', quantity: 1, totalPrice: 25 },
      ],
    })
  })

  it('renders the immutable day-one financial summary', async () => {
    const wrapper = mount(DaySummaryStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('비빔이님의 경제활동 결과')
    expect(wrapper.text()).toContain('현재 총자산')
    expect(wrapper.text()).toContain('85비빔')
    expect(wrapper.text()).toContain('안심 채권')
    expect(wrapper.text()).toContain('연필 세트')
  })
})
