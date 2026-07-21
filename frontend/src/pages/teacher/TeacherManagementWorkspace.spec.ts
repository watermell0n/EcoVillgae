import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TeacherManagementWorkspace from './TeacherManagementWorkspace.vue'

const managementApi = vi.hoisted(() => ({
  addManagedStudent: vi.fn(),
  getClassSettings: vi.fn(),
  getManagedStudents: vi.fn(),
  resetManagedStudentPassword: vi.fn(),
  saveClassSettings: vi.fn(),
  updateManagedStudent: vi.fn(),
  uploadProductImage: vi.fn(),
}))

vi.mock('../../features/management/api/management', () => managementApi)

const classSettings = {
  roomId: 'room-1',
  roomName: '햇살반 경제교실',
  roomStatus: 'PREPARING',
  editingLiveClass: false,
  investmentResultsLocked: false,
  economy: {
    roleIncome: 150,
    day1CorrectReward: 5,
    incomeTaxBps: 1000,
    depositInterestBps: 1000,
    financialTaxBps: 1000,
    day2CorrectReward: 10,
    inflationBps: 1000,
    depositMinAmount: 20,
    depositUnit: 10,
    investmentMinAmount: 10,
    investmentUnit: 10,
    initialFundAmount: 0,
  },
  quizzes: [],
  investments: [],
  products: [{
    productId: 'product-1',
    name: '연필 세트',
    description: '필기할 때 쓰는 연필이에요.',
    price: 30,
    imageUrl: null,
    currentStock: 10,
    displayOrder: 1,
  }],
  fundCostOptions: [],
  roleCatalog: [],
}

function shopTab(wrapper: ReturnType<typeof mount>) {
  const button = wrapper.findAll('button').find((item) => item.text() === '상점 상품')
  if (!button) throw new Error('상점 상품 탭을 찾지 못했습니다.')
  return button
}

describe('TeacherManagementWorkspace product images', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    managementApi.getClassSettings.mockResolvedValue(structuredClone(classSettings))
    managementApi.getManagedStudents.mockResolvedValue([])
    managementApi.uploadProductImage.mockResolvedValue('/api/v1/product-images/uploaded.png')
    managementApi.saveClassSettings.mockImplementation(
      async (_roomId: string, settings: typeof classSettings) =>
        JSON.parse(JSON.stringify(settings)) as typeof classSettings,
    )
  })

  it('uploads the image separately and saves only its short URL in class settings', async () => {
    const wrapper = mount(TeacherManagementWorkspace, {
      props: { roomId: 'room-1', view: 'SETTINGS' },
    })
    await flushPromises()
    await shopTab(wrapper).trigger('click')

    const file = new File(['image bytes'], 'product.png', { type: 'image/png' })
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()

    expect(managementApi.uploadProductImage).toHaveBeenCalledWith('room-1', file)
    expect(wrapper.get('.settings-product-preview').attributes('src'))
      .toBe('/api/v1/product-images/uploaded.png')

    const saveButton = wrapper.findAll('button').find((item) => item.text() === '변경사항 저장')
    await saveButton?.trigger('click')
    const confirmButton = wrapper.findAll('button').find((item) => item.text() === '저장하기')
    await confirmButton?.trigger('click')
    await flushPromises()

    expect(managementApi.saveClassSettings).toHaveBeenCalledWith(
      'room-1',
      expect.objectContaining({
        products: [expect.objectContaining({ imageUrl: '/api/v1/product-images/uploaded.png' })],
      }),
      [],
    )
    expect(wrapper.text()).toContain('수업 설정을 저장했습니다.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('closes the confirmation dialog and shows an error when saving fails', async () => {
    managementApi.saveClassSettings.mockRejectedValueOnce(new Error('save failed'))
    const wrapper = mount(TeacherManagementWorkspace, {
      props: { roomId: 'room-1', view: 'SETTINGS' },
    })
    await flushPromises()

    const saveButton = wrapper.findAll('button').find((item) => item.text() === '변경사항 저장')
    await saveButton?.trigger('click')
    const confirmButton = wrapper.findAll('button').find((item) => item.text() === '저장하기')
    await confirmButton?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('수업 설정을 저장하지 못했습니다.')
  })
})
