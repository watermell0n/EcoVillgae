import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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
  afterEach(() => {
    vi.useRealTimers()
  })

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

  it('shows a dismissible sold-out notice when another student buys the last item first', async () => {
    shopApi.getStudentShopProducts.mockResolvedValue({
      walletBalance: 100,
      products: [{
        productId: 'product-1',
        name: '연필 세트',
        description: '공부에 필요한 연필이에요.',
        imageUrl: null,
        originalPrice: 20,
        price: 20,
        initialStock: 1,
        currentStock: 1,
        status: 'ON_SALE',
        displayOrder: 1,
      }],
    })
    shopApi.purchaseShopProduct.mockRejectedValue({
      isAxiosError: true,
      response: { data: { error: { code: 'OUT_OF_STOCK', message: '상품 재고가 부족합니다.' } } },
    })

    const wrapper = mount(Day2FinalShopStageView)
    await flushPromises()
    await wrapper.get('.shop-product-card button').trigger('click')
    await flushPromises()
    await wrapper.get('.confirm-modal__actions button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alertdialog"]').text()).toContain('상품이 방금 품절됐어요')
    expect(wrapper.get('[role="alertdialog"] button').text()).toBe('확인')
  })

  it('disables quantity increase at the wallet-based purchase limit', async () => {
    shopApi.getStudentShopProducts.mockResolvedValue({
      walletBalance: 100,
      products: [{
        productId: 'product-1', name: '필통', description: '튼튼한 필통', imageUrl: null,
        originalPrice: 30, price: 30, initialStock: 10, currentStock: 10,
        status: 'ON_SALE', displayOrder: 1,
      }],
    })

    const wrapper = mount(Day2FinalShopStageView)
    await flushPromises()
    await wrapper.get('.shop-product-card button').trigger('click')
    await flushPromises()

    const increaseButton = wrapper.get('button[aria-label="수량 늘리기"]')
    await increaseButton.trigger('click')
    await increaseButton.trigger('click')

    expect(wrapper.get('.shop-quantity strong').text()).toBe('3개')
    expect(increaseButton.attributes('disabled')).toBeDefined()
  })

  it('shows a purchase notice only in the shop and removes it after three seconds', async () => {
    vi.useFakeTimers()
    shopApi.getStudentShopProducts.mockResolvedValue({
      walletBalance: 100,
      products: [{
        productId: 'product-1', name: '마이쭈 딸기', description: '딸기맛 간식', imageUrl: null,
        originalPrice: 20, price: 20, initialStock: 2, currentStock: 2,
        status: 'ON_SALE', displayOrder: 1,
      }],
    })
    shopApi.purchaseShopProduct.mockResolvedValue({
      purchaseId: 'purchase-1', productId: 'product-1', productName: '마이쭈 딸기',
      quantity: 1, unitPrice: 20, totalPrice: 20, walletBalance: 80,
      remainingStock: 1, productStatus: 'ON_SALE', purchasedAt: '2026-07-20T00:00:00Z', replayed: false,
    })

    const wrapper = mount(Day2FinalShopStageView)
    await flushPromises()
    await wrapper.get('.shop-product-card button').trigger('click')
    await flushPromises()
    await wrapper.get('.confirm-modal__actions button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('마이쭈 딸기 1개를 구매했습니다.')
    await wrapper.findAll('.economy-module-tabs button')[1]!.trigger('click')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    await wrapper.findAll('.economy-module-tabs button')[0]!.trigger('click')
    await vi.advanceTimersByTimeAsync(3_000)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)

    wrapper.unmount()
  })
})
