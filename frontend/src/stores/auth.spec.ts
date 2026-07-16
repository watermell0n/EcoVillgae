import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import * as authApi from '../features/auth/api/auth'
import { useAuthStore } from './auth'

vi.mock('../features/auth/api/auth', () => ({
  enterStudent: vi.fn(),
  getStudentMe: vi.fn(),
  getTeacherMe: vi.fn(),
  loginStudent: vi.fn(),
  loginTeacher: vi.fn(),
  setupStudent: vi.fn(),
}))

const entry: authApi.StudentEntry = {
  participationCode: 'ECO-02',
  roomId: 'room-1',
  roomName: '6학년 2반 경제교실',
  participantNumber: 2,
  requiredAction: 'SETUP',
}

const student: authApi.StudentSession = {
  studentId: 'student-2',
  roomId: 'room-1',
  roomName: '6학년 2반 경제교실',
  participantNumber: 2,
  nickname: '비빔이',
  roomStatus: 'DAY1_ACTIVE',
  currentDay: 1,
  currentStage: 'DAY1_LOBBY',
  mode: 'GUIDED',
  walletBalance: 0,
  syncVersion: 0,
  connectionStatus: 'CONNECTED',
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('remembers a verified participation code until setup is complete', async () => {
    vi.mocked(authApi.enterStudent).mockResolvedValue(entry)
    vi.mocked(authApi.setupStudent).mockResolvedValue(student)
    const store = useAuthStore()

    await store.enterStudent('eco-02')

    expect(store.pendingStudentEntry).toEqual(entry)
    expect(sessionStorage.getItem('ecovillage.pendingStudentEntry')).toContain('ECO-02')

    await store.setupStudent('비빔이', '1234')

    expect(store.student).toEqual(student)
    expect(store.pendingStudentEntry).toBeNull()
    expect(sessionStorage.getItem('ecovillage.pendingStudentEntry')).toBeNull()
  })
})
