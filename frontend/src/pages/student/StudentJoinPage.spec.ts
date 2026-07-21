import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth'
import StudentJoinPage from './StudentJoinPage.vue'

const router = vi.hoisted(() => ({ push: vi.fn() }))
const route = vi.hoisted(() => ({ query: { roomId: 'room-1' } as Record<string, string> }))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

describe('StudentJoinPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    route.query = { roomId: 'room-1' }
  })

  it('uses the class link and the student number to enter', async () => {
    const authStore = useAuthStore()
    const enterStudent = vi.spyOn(authStore, 'enterStudent').mockResolvedValue({
      participationCode: 'ECO-ROOM-07',
      roomId: 'room-1',
      roomName: '햇살반',
      participantNumber: 7,
      nickname: null,
      requiredAction: 'SETUP',
    })
    const wrapper = mount(StudentJoinPage)

    expect(wrapper.get('input[name="studentEntry"]').attributes('placeholder')).toBe('예: 01')
    await wrapper.get('input[name="studentEntry"]').setValue('07')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(enterStudent).toHaveBeenCalledWith('07', 'room-1')
    expect(router.push).toHaveBeenCalledWith({ name: 'student-setup' })
    expect(wrapper.text()).toContain('자기 번호만 입력')
  })
})
