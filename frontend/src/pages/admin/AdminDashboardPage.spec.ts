import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth'
import AdminDashboardPage from './AdminDashboardPage.vue'

const adminApi = vi.hoisted(() => ({
  getAdminRooms: vi.fn(),
  getAdminRoomStudents: vi.fn(),
  getAdminStudentActivities: vi.fn(),
  terminateAdminStudentSession: vi.fn(),
}))

vi.mock('../../features/admin/api/admin', () => adminApi)

describe('AdminDashboardPage', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().admin = { adminId: 'admin-1', displayName: '운영자' }

    adminApi.getAdminRooms.mockResolvedValue([
      {
        roomId: 'room-1',
        roomName: '6학년 2반',
        roomStatus: 'DAY1_ACTIVE',
        currentDay: 1,
        currentStage: 'DAY1_QUIZ',
        syncVersion: 4,
        expectedStudentCount: 3,
        onlineCount: 1,
        unstableCount: 1,
        awayCount: 0,
        offlineCount: 0,
        neverConnectedCount: 1,
        updatedAt: '2026-07-16T00:00:00Z',
      },
    ])
    adminApi.getAdminRoomStudents.mockResolvedValue({
      roomId: 'room-1',
      roomName: '6학년 2반',
      currentStage: 'DAY1_QUIZ',
      syncVersion: 4,
      students: [
        {
          studentId: 'student-1',
          participantNumber: 1,
          nickname: '비빔이',
          studentStatus: 'ACTIVE',
          connectionStatus: 'UNSTABLE',
          currentPage: 'QUIZ_QUESTION_3',
          currentModule: 'QUIZ',
          reportedStage: 'DAY1_QUIZ',
          roomStage: 'DAY1_QUIZ',
          clientSyncVersion: 3,
          roomSyncVersion: 4,
          syncStatus: 'DELAYED',
          walletBalance: 120,
          firstSeenAt: '2026-07-16T00:00:00Z',
          lastSeenAt: '2026-07-16T00:01:00Z',
          reconnectedAt: null,
        },
      ],
    })
    adminApi.getAdminStudentActivities.mockResolvedValue([])
  })

  it('shows each student connection, current page, and synchronization status', async () => {
    const wrapper = mount(AdminDashboardPage, {
      global: {
        plugins: [pinia],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('1번 비빔이')
    expect(wrapper.text()).toContain('연결 불안정')
    expect(wrapper.text()).toContain('퀴즈 3번 문제')
    expect(wrapper.text()).toContain('1단계 지연')
    expect(wrapper.text()).toContain('120비빔')

    wrapper.unmount()
  })
})
