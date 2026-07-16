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
  })

  it('confirms a purchase and updates the wallet and stock', async () => {
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'refreshStudent').mockResolvedValue(undefined as never)
    const wrapper = mount(FreeActivityStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('내 지갑')
    expect(wrapper.text()).toContain('100비빔')
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
})
