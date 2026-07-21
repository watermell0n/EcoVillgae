import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TeacherRoomsPage from './TeacherRoomsPage.vue'

const authApi = vi.hoisted(() => ({
  enterStudent: vi.fn(),
  getStudentMe: vi.fn(),
  getTeacherMe: vi.fn(),
  loginStudent: vi.fn(),
  loginTeacher: vi.fn(),
  setupStudent: vi.fn(),
}))

const roomApi = vi.hoisted(() => ({
  createTeacherRoom: vi.fn(),
  deleteTeacherRoom: vi.fn(),
}))

vi.mock('../../features/auth/api/auth', () => authApi)
vi.mock('../../features/room/api/room', () => roomApi)

const teacher = {
  teacherId: 'teacher-1',
  displayName: '이코빌리지 선생님',
  rooms: [],
}

const createdRoom = {
  roomId: 'room-new',
  name: '6학년 3반 경제교실',
  expectedStudentCount: 4,
  connectedStudentCount: 0,
  status: 'PREPARING',
  currentStage: 'DAY1_LOBBY',
  students: [],
}

describe('TeacherRoomsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    authApi.getTeacherMe.mockResolvedValue(structuredClone(teacher))
    roomApi.createTeacherRoom.mockResolvedValue(structuredClone(createdRoom))
    roomApi.deleteTeacherRoom.mockResolvedValue(structuredClone(createdRoom))
  })

  it('creates a room and moves to its waiting room', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/teacher/rooms', name: 'teacher-rooms', component: { template: '<div />' } },
        {
          path: '/teacher/rooms/:roomId/control',
          name: 'teacher-room-control',
          component: { template: '<div />' },
        },
        { path: '/teacher/login', name: 'teacher-login', component: { template: '<div />' } },
        { path: '/', name: 'home', component: { template: '<div />' } },
      ],
    })
    await router.push({ name: 'teacher-rooms' })
    await router.isReady()

    const wrapper = mount(TeacherRoomsPage, {
      global: { plugins: [router] },
    })
    await flushPromises()

    await wrapper.get('.dashboard-title-row button').trigger('click')
    await wrapper.get('input[name="roomName"]').setValue('6학년 3반 경제교실')
    expect(wrapper.text()).toContain('24명의 학생 번호')
    await wrapper.get('.room-create-modal form').trigger('submit')
    await flushPromises()

    expect(roomApi.createTeacherRoom).toHaveBeenCalledWith('6학년 3반 경제교실', 24)
    expect(authApi.getTeacherMe).toHaveBeenCalledTimes(2)
    expect(router.currentRoute.value).toMatchObject({
      name: 'teacher-room-control',
      params: { roomId: 'room-new' },
    })
  })

  it('removes a deleted room from the list immediately', async () => {
    authApi.getTeacherMe.mockReset()
    authApi.getTeacherMe
      .mockResolvedValueOnce({ ...structuredClone(teacher), rooms: [structuredClone(createdRoom)] })
      .mockResolvedValueOnce(structuredClone(teacher))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/teacher/rooms', name: 'teacher-rooms', component: { template: '<div />' } },
        { path: '/teacher/rooms/:roomId/control', name: 'teacher-room-control', component: { template: '<div />' } },
        { path: '/teacher/login', name: 'teacher-login', component: { template: '<div />' } },
        { path: '/', name: 'home', component: { template: '<div />' } },
      ],
    })
    await router.push({ name: 'teacher-rooms' })
    await router.isReady()
    const wrapper = mount(TeacherRoomsPage, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.get('.room-delete-button').trigger('click')
    await flushPromises()

    expect(roomApi.deleteTeacherRoom).toHaveBeenCalledWith('room-new')
    expect(wrapper.find('.room-card').exists()).toBe(false)
    expect(wrapper.text()).toContain('학급 목록에서 삭제했습니다')
  })
})
