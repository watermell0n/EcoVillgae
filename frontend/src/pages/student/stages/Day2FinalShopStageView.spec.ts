import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Day2FinalShopStageView from './Day2FinalShopStageView.vue'

const shopApi = vi.hoisted(() => ({
  getStudentPurchaseHistory: vi.fn(),
  getStudentShopProducts: vi.fn(),
  getTeacherShopSummary: vi.fn(),
  purchaseShopProduct: vi.fn(),
}))

const economyApi = vi.hoisted(() => ({
  getEconomyOverview: vi.fn(),
  getEconomyTransactions: vi.fn(),
  getCurrentDeposit: vi.fn(),
  saveDepositDraft: vi.fn(),
  cancelDepositDraft: vi.fn(),
  confirmDeposit: vi.fn(),
  getCurrentInvestment: vi.fn(),
  saveInvestmentDraft: vi.fn(),
  cancelInvestmentDraft: vi.fn(),
  confirmInvestment: vi.fn(),
}))

vi.mock('../../../features/shop/api/shop', () => shopApi)
vi.mock('../../../features/economy/api/economy', () => economyApi)

describe('Day2FinalShopStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    shopApi.getStudentShopProducts.mockResolvedValue({ walletBalance: 100, products: [] })
    shopApi.getStudentPurchaseHistory.mockResolvedValue({ purchases: [] })
    economyApi.getEconomyOverview.mockResolvedValue({
      walletBalance: 100,
      depositBalance: 0,
      investmentBalance: 0,
      purchaseCount: 0,
      depositStatus: 'NONE',
      investmentStatus: 'NONE',
      totalAssets: 100,
    })
    economyApi.getEconomyTransactions.mockResolvedValue([])
  })

  it('renders the final shop when all data loads', async () => {
    const wrapper = mount(Day2FinalShopStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('2일차 최종 상점')
    expect(wrapper.text()).toContain('100비빔')
  })

  it('shows a retry action instead of a blank screen when a partial request fails', async () => {
    economyApi.getEconomyOverview.mockRejectedValueOnce(new Error('network error'))

    const wrapper = mount(Day2FinalShopStageView)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('최종 상점을 불러오지 못했어요.')
    expect(wrapper.get('[role="alert"] button').text()).toBe('다시 불러오기')
  })
})
