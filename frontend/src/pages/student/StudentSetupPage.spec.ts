import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth'
import StudentSetupPage from './StudentSetupPage.vue'

const router = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => router,
}))

describe('StudentSetupPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  function mountSetupPage() {
    const authStore = useAuthStore()
    authStore.pendingStudentEntry = {
      participationCode: 'ROOM-01',
      roomId: 'room-1',
      roomName: '비빔마을',
      participantNumber: 1,
      requiredAction: 'SETUP',
    }
    const setupStudent = vi.spyOn(authStore, 'setupStudent').mockResolvedValue({
      studentId: 'student-1',
      roomId: 'room-1',
      roomName: '비빔마을',
      participantNumber: 1,
      nickname: '비빔이',
      roomStatus: 'DAY1_ACTIVE',
      currentDay: 1,
      currentStage: 'DAY1_WAITING',
      mode: 'GUIDED',
      walletBalance: 0,
      syncVersion: 1,
      connectionStatus: 'ONLINE',
    })
    const wrapper = mount(StudentSetupPage, {
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    return { setupStudent, wrapper }
  }

  it('removes whitespace from both password fields before setup', async () => {
    const { setupStudent, wrapper } = mountSetupPage()
    const passwordInputs = wrapper.findAll('input[type="password"]')

    await wrapper.get('input[placeholder="친구들이 알아볼 이름"]').setValue('비빔이')
    await passwordInputs[0]!.setValue('12 34')
    await passwordInputs[1]!.setValue('1\t234')

    expect((passwordInputs[0]!.element as HTMLInputElement).value).toBe('1234')
    expect((passwordInputs[1]!.element as HTMLInputElement).value).toBe('1234')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(setupStudent).toHaveBeenCalledWith('비빔이', '1234')
  })

  it('does not accept one character padded with spaces as a password', async () => {
    const { setupStudent, wrapper } = mountSetupPage()
    const passwordInputs = wrapper.findAll('input[type="password"]')

    await wrapper.get('input[placeholder="친구들이 알아볼 이름"]').setValue('비빔이')
    await passwordInputs[0]!.setValue('    a')
    await passwordInputs[1]!.setValue('    a')
    await wrapper.get('form').trigger('submit')

    expect((passwordInputs[0]!.element as HTMLInputElement).value).toBe('a')
    expect((passwordInputs[1]!.element as HTMLInputElement).value).toBe('a')
    expect(wrapper.get('[role="alert"]').text()).toContain('공백을 제외하고 4자 이상')
    expect(setupStudent).not.toHaveBeenCalled()
  })
})
