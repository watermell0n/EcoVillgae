import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useRoomSessionStore } from '../../../stores/roomSession'
import FreeActivityStageView from './FreeActivityStageView.vue'

const shopApi = vi.hoisted(() => ({
  getStudentPurchaseHistory: vi.fn(),
  getStudentShopProducts: vi.fn(),
  getTeacherShopSummary: vi.fn(),
  purchaseShopProduct: vi.fn(),
}))

vi.mock('../../../features/shop/api/shop', () => shopApi)

const learningApi = vi.hoisted(() => ({
  getStudentLearningTopics: vi.fn(),
  getStudentLearningTopic: vi.fn(),
}))

vi.mock('../../../features/learning/api/learning', () => learningApi)

const economyApi = vi.hoisted(() => ({
  getEconomyOverview: vi.fn(),
  getEconomyTransactions: vi.fn(),
  getCurrentDeposit: vi.fn(),
  getDepositHistory: vi.fn(),
  saveDepositDraft: vi.fn(),
  cancelDepositDraft: vi.fn(),
  confirmDeposit: vi.fn(),
  getCurrentInvestment: vi.fn(),
  getInvestmentHistory: vi.fn(),
  saveInvestmentDraft: vi.fn(),
  cancelInvestmentDraft: vi.fn(),
  confirmInvestment: vi.fn(),
}))

vi.mock('../../../features/economy/api/economy', () => economyApi)

const catalog = {
  walletBalance: 100,
  products: [
    {
      productId: 'product-1',
      name: '연필 세트',
      description: '수업에서 사용하는 연필 세트예요.',
      imageUrl: null,
      originalPrice: 25,
      price: 25,
      initialStock: 10,
      currentStock: 10,
      status: 'ON_SALE' as const,
      displayOrder: 1,
    },
  ],
}

describe('FreeActivityStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    shopApi.getStudentShopProducts.mockResolvedValue(structuredClone(catalog))
    shopApi.getStudentPurchaseHistory.mockResolvedValue({ purchases: [] })
    shopApi.purchaseShopProduct.mockResolvedValue({
      purchaseId: 'purchase-1',
      productId: 'product-1',
      productName: '연필 세트',
      quantity: 1,
      unitPrice: 25,
      totalPrice: 25,
      walletBalance: 75,
      remainingStock: 9,
      productStatus: 'ON_SALE',
      purchasedAt: '2026-07-16T00:00:00Z',
      replayed: false,
    })
    learningApi.getStudentLearningTopics.mockResolvedValue([
      {
        topicId: 'topic-1',
        code: 'DEPOSIT',
        title: '예금과 이자',
        description: '돈을 안전하게 맡기는 방법',
        pageCount: 1,
      },
    ])
    learningApi.getStudentLearningTopic.mockResolvedValue({
      topicId: 'topic-1',
      code: 'DEPOSIT',
      title: '예금과 이자',
      description: '돈을 안전하게 맡기는 방법',
      pageCount: 1,
      pages: [{
        pageId: 'page-1',
        topicCode: 'DEPOSIT',
        topicTitle: '예금과 이자',
        pageOrder: 1,
        title: '예금은 무엇일까요?',
        content: '돈을 맡기고 이자를 받아요.',
        keyPoints: ['원금과 이자를 확인해요.'],
      }],
    })
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
    economyApi.getDepositHistory.mockResolvedValue([])
    economyApi.getCurrentDeposit.mockResolvedValue({
      depositId: null,
      status: 'NONE',
      principal: 0,
      interestRateBps: 500,
      expectedInterest: 0,
      expectedMaturityAmount: 0,
      walletBalance: 100,
      updatedAt: null,
      confirmedAt: null,
      replayed: false,
    })
    economyApi.getCurrentInvestment.mockResolvedValue({
      orderId: null,
      status: 'NONE',
      totalAmount: 0,
      walletBalance: 100,
      scenarioLocked: false,
      assets: [
        {
          assetId: 'asset-1',
          code: 'SAFE_BOND',
          name: '안심 채권',
          description: '위험이 낮은 투자처예요.',
          companyInfo: '안심 채권은 꾸준한 수익을 기대하는 상품이에요.',
          riskLevel: 'LOW',
          minReturnBps: -200,
          maxReturnBps: 400,
          displayOrder: 1,
        },
      ],
      allocations: [],
      updatedAt: null,
      confirmedAt: null,
      replayed: false,
    })
    economyApi.getInvestmentHistory.mockResolvedValue([])
  })

  async function openTab(wrapper: ReturnType<typeof mount>, label: string) {
    const button = wrapper.findAll('.economy-module-tabs button')
      .find((item) => item.text() === label)
    expect(button).toBeDefined()
    await button!.trigger('click')
  }

  it('confirms a purchase and updates the wallet and stock', async () => {
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'refreshStudent').mockResolvedValue(undefined as never)
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('내 비빔을 어떻게 사용할까요?')
    expect(wrapper.text()).toContain('100비빔')
    economyApi.getEconomyOverview.mockResolvedValue({
      walletBalance: 75,
      depositBalance: 0,
      investmentBalance: 0,
      purchaseCount: 1,
      depositStatus: 'NONE',
      investmentStatus: 'NONE',
      totalAssets: 75,
    })
    await openTab(wrapper, '상점')
    await wrapper.get('.shop-product-card button').trigger('click')
    expect(wrapper.text()).toContain('환불하거나 교환할 수 없어요')
    await wrapper.get('.confirm-modal__actions button:not(.button-secondary)').trigger('click')
    await flushPromises()

    expect(shopApi.purchaseShopProduct).toHaveBeenCalledWith(
      'product-1',
      1,
      expect.any(String),
    )
    expect(wrapper.text()).toContain('연필 세트 1개를 구매했어요!')
    expect(wrapper.text()).toContain('75비빔')
    expect(wrapper.text()).toContain('재고 9개')
  })

  it('lets the student review a learning topic during free activity', async () => {
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '금융지식')
    expect(wrapper.text()).toContain('금융지식 다시 보기')
    await wrapper.get('.learning-topic-grid button').trigger('click')
    await flushPromises()

    expect(learningApi.getStudentLearningTopic).toHaveBeenCalledWith('topic-1')
    expect(wrapper.text()).toContain('예금은 무엇일까요?')
    expect(wrapper.text()).toContain('돈을 맡기고 이자를 받아요.')
  })

  it('saves and confirms a deposit draft', async () => {
    economyApi.saveDepositDraft.mockResolvedValue({
      ...(await economyApi.getCurrentDeposit()),
      depositId: 'deposit-1',
      status: 'DRAFT',
      principal: 20,
      expectedInterest: 1,
      expectedMaturityAmount: 21,
    })
    economyApi.confirmDeposit.mockResolvedValue({
      ...(await economyApi.getCurrentDeposit()),
      depositId: 'deposit-1',
      status: 'CONFIRMED',
      principal: 20,
      expectedInterest: 1,
      expectedMaturityAmount: 21,
      walletBalance: 80,
      confirmedAt: '2026-07-16T00:00:00Z',
    })
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'refreshStudent').mockResolvedValue(undefined as never)
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '예금')
    const actions = wrapper.findAll('.finance-actions button')
    await actions[1]!.trigger('click')
    await flushPromises()
    expect(economyApi.saveDepositDraft).toHaveBeenCalledWith(20)
    await actions[2]!.trigger('click')
    await flushPromises()

    expect(economyApi.confirmDeposit).toHaveBeenCalledWith(expect.any(String))
    expect(wrapper.text()).toContain('예금을 확정했어요')
  })

  it('allows another deposit after a previous deposit was confirmed', async () => {
    economyApi.getEconomyOverview.mockResolvedValue({
      walletBalance: 80,
      depositBalance: 20,
      investmentBalance: 0,
      purchaseCount: 0,
      depositStatus: 'CONFIRMED',
      investmentStatus: 'NONE',
      totalAssets: 100,
    })
    economyApi.getCurrentDeposit.mockResolvedValue({
      depositId: 'deposit-1',
      status: 'CONFIRMED',
      principal: 20,
      interestRateBps: 500,
      expectedInterest: 1,
      expectedMaturityAmount: 21,
      walletBalance: 80,
      updatedAt: '2026-07-16T00:00:00Z',
      confirmedAt: '2026-07-16T00:00:00Z',
      replayed: false,
    })
    economyApi.saveDepositDraft.mockResolvedValue({
      ...(await economyApi.getCurrentDeposit()),
      depositId: 'deposit-2',
      status: 'DRAFT',
      confirmedAt: null,
    })
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '예금')
    expect(wrapper.text()).toContain('지금까지 20비빔을 예금했어요')
    const amount = wrapper.get('#deposit-principal')
    expect(amount.element.tagName).toBe('OUTPUT')
    expect(amount.text()).toBe('20비빔')
    await wrapper.findAll('.finance-actions button')[1]!.trigger('click')
    await flushPromises()

    expect(economyApi.saveDepositDraft).toHaveBeenCalledWith(20)
  })

  it('saves an investment allocation in 10 Bibim units', async () => {
    economyApi.saveInvestmentDraft.mockResolvedValue({
      ...(await economyApi.getCurrentInvestment()),
      orderId: 'order-1',
      status: 'DRAFT',
      totalAmount: 30,
      allocations: [{ assetId: 'asset-1', assetName: '안심 채권', riskLevel: 'LOW', amount: 30 }],
    })
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '투자')
    const increaseButton = wrapper.get('[aria-label="안심 채권 투자 금액 10비빔 늘리기"]')
    await increaseButton.trigger('click')
    await increaseButton.trigger('click')
    await increaseButton.trigger('click')
    const actions = wrapper.findAll('.finance-actions button')
    await actions[1]!.trigger('click')
    await flushPromises()

    expect(economyApi.saveInvestmentDraft).toHaveBeenCalledWith([
      { assetId: 'asset-1', amount: 30 },
    ])
    expect(wrapper.text()).toContain('투자 초안을 저장했어요')
  })

  it('opens the selected company information without changing the investment amount', async () => {
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '투자')
    await wrapper.get('.investment-asset-card').trigger('click')

    const dialog = wrapper.get('.investment-detail-modal')
    expect(dialog.attributes('role')).toBe('dialog')
    expect(dialog.text()).toContain('안심 채권')
    expect(dialog.text()).toContain('안심 채권은 꾸준한 수익을 기대하는 상품이에요.')
    expect(dialog.text()).toContain('예상 결과 -2% ~ +4%')
    expect(wrapper.get('.amount-spinner__value').text()).toBe('0비빔')

    await dialog.get('[aria-label="기업 정보 닫기"]').trigger('click')
    expect(wrapper.find('.investment-detail-modal').exists()).toBe(false)
  })

  it('allows another investment after a previous investment was confirmed', async () => {
    economyApi.getEconomyOverview.mockResolvedValue({
      walletBalance: 70,
      depositBalance: 0,
      investmentBalance: 30,
      purchaseCount: 0,
      depositStatus: 'NONE',
      investmentStatus: 'CONFIRMED',
      totalAssets: 100,
    })
    economyApi.getCurrentInvestment.mockResolvedValue({
      orderId: 'order-1',
      status: 'CONFIRMED',
      totalAmount: 30,
      walletBalance: 70,
      scenarioLocked: true,
      assets: [
        {
          assetId: 'asset-1',
          code: 'SAFE_BOND',
          name: '안심 채권',
          description: '위험이 낮은 투자처예요.',
          companyInfo: '안심 채권은 꾸준한 수익을 기대하는 상품이에요.',
          riskLevel: 'LOW',
          minReturnBps: -200,
          maxReturnBps: 400,
          displayOrder: 1,
        },
      ],
      allocations: [
        { assetId: 'asset-1', assetName: '안심 채권', riskLevel: 'LOW', amount: 30 },
      ],
      updatedAt: '2026-07-16T00:00:00Z',
      confirmedAt: '2026-07-16T00:00:00Z',
      replayed: false,
    })
    economyApi.saveInvestmentDraft.mockResolvedValue({
      ...(await economyApi.getCurrentInvestment()),
      orderId: 'order-2',
      status: 'DRAFT',
      totalAmount: 20,
      allocations: [
        { assetId: 'asset-1', assetName: '안심 채권', riskLevel: 'LOW', amount: 20 },
      ],
      confirmedAt: null,
    })
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '투자')
    expect(wrapper.text()).toContain('지금까지 30비빔을 투자했어요')
    expect(wrapper.find('.investment-asset-grid input').exists()).toBe(false)
    const increaseButton = wrapper.get('[aria-label="안심 채권 투자 금액 10비빔 늘리기"]')
    await increaseButton.trigger('click')
    await increaseButton.trigger('click')
    await wrapper.findAll('.finance-actions button')[1]!.trigger('click')
    await flushPromises()

    expect(economyApi.saveInvestmentDraft).toHaveBeenCalledWith([
      { assetId: 'asset-1', amount: 20 },
    ])
  })

  it('uses button-only amount controls and never exceeds the available wallet balance', async () => {
    economyApi.getEconomyOverview.mockResolvedValue({
      walletBalance: 25,
      depositBalance: 0,
      investmentBalance: 0,
      purchaseCount: 0,
      depositStatus: 'NONE',
      investmentStatus: 'NONE',
      totalAssets: 25,
    })
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '예금')
    expect(wrapper.find('.finance-form-card input').exists()).toBe(false)
    expect(wrapper.get('#deposit-principal').text()).toBe('20비빔')
    expect(wrapper.get('[aria-label="예금 금액 10비빔 줄이기"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="예금 금액 10비빔 늘리기"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('확정 후 지갑')
    expect(wrapper.text()).toContain('5비빔')

    await openTab(wrapper, '투자')
    expect(wrapper.find('.investment-asset-grid input').exists()).toBe(false)
    const increaseButton = wrapper.get('[aria-label="안심 채권 투자 금액 10비빔 늘리기"]')
    await increaseButton.trigger('click')
    await increaseButton.trigger('click')

    expect(wrapper.text()).toContain('총투자금')
    expect(wrapper.text()).toContain('20비빔')
    expect(increaseButton.attributes('disabled')).toBeDefined()
  })

  it('shows every confirmed deposit and investment allocation', async () => {
    economyApi.getDepositHistory.mockResolvedValue([
      {
        depositId: 'deposit-1',
        principal: 40,
        interestRateBps: 500,
        expectedInterest: 2,
        expectedMaturityAmount: 42,
        confirmedAt: '2026-07-16T00:00:00Z',
      },
    ])
    economyApi.getInvestmentHistory.mockResolvedValue([
      {
        orderId: 'order-1',
        totalAmount: 30,
        confirmedAt: '2026-07-16T00:00:00Z',
        allocations: [
          { assetId: 'asset-1', assetName: '튼튼 채권', riskLevel: 'LOW', amount: 30 },
        ],
      },
    ])
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    await openTab(wrapper, '예금')
    expect(wrapper.text()).toContain('내가 맡긴 예금')
    expect(wrapper.text()).toContain('40비빔 예금')
    expect(wrapper.text()).toContain('만기 예상')
    expect(wrapper.text()).toContain('42비빔')

    await openTab(wrapper, '투자')
    expect(wrapper.text()).toContain('내가 선택한 투자처')
    expect(wrapper.text()).toContain('튼튼 채권')
    expect(wrapper.text()).toContain('30비빔')
  })
})
