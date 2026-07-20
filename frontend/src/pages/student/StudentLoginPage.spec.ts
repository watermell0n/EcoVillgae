import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth'
import StudentLoginPage from './StudentLoginPage.vue'

const router = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => router,
}))

describe('StudentLoginPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('removes whitespace from the password input and login request', async () => {
    const authStore = useAuthStore()
    authStore.pendingStudentEntry = {
      participationCode: 'ROOM-01',
      roomId: 'room-1',
      roomName: '비빔마을',
      participantNumber: 1,
      requiredAction: 'LOGIN',
    }
    const loginStudent = vi.spyOn(authStore, 'loginStudent').mockResolvedValue({
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
    const wrapper = mount(StudentLoginPage, {
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    const passwordInput = wrapper.get('input[type="password"]')

    await passwordInput.setValue('12 3\t4')
    expect((passwordInput.element as HTMLInputElement).value).toBe('1234')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(loginStudent).toHaveBeenCalledWith('1234')
    expect(router.push).toHaveBeenCalledWith({ name: 'student-room', params: { roomId: 'room-1' } })
  })
})
