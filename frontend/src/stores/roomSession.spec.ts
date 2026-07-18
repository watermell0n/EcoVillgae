import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useRoomSessionStore } from './roomSession'

const roomApi = vi.hoisted(() => ({
  getStudentRoomState: vi.fn(),
  getTeacherRoomState: vi.fn(),
  sendRoomCommand: vi.fn(),
  setRoomStage: vi.fn(),
}))

vi.mock('../features/room/api/room', () => roomApi)

function teacherState(roomId: string, roomName: string, syncVersion: number) {
  return {
    roomId,
    roomName,
    roomStatus: 'DAY1_ACTIVE',
    currentDay: 1,
    currentStage: 'DAY1_LOBBY',
    mode: 'GUIDED',
    version: syncVersion,
    syncVersion,
    expectedStudentCount: 3,
    connectedStudentCount: 0,
    modules: [],
    changed: true,
    updatedAt: '2026-07-18T00:00:00Z',
  }
}

describe('roomSession store teacher room context', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('replaces the previous room even when the new room has a lower sync version', async () => {
    roomApi.getTeacherRoomState.mockImplementation((roomId: string) => Promise.resolve(
      roomId === 'room-b201'
        ? teacherState('room-b201', 'B201반', 30)
        : teacherState('room-new', '새 교실', 1),
    ))
    const store = useRoomSessionStore()

    await store.refreshTeacher('room-b201')
    await store.refreshTeacher('room-new')

    expect(store.teacherState?.roomId).toBe('room-new')
    expect(store.teacherState?.roomName).toBe('새 교실')
  })

  it('ignores a late response from a room that is no longer active', async () => {
    let resolveOldRoom!: (state: ReturnType<typeof teacherState>) => void
    const oldRoomResponse = new Promise<ReturnType<typeof teacherState>>((resolve) => {
      resolveOldRoom = resolve
    })
    roomApi.getTeacherRoomState.mockImplementation((roomId: string) => (
      roomId === 'room-old'
        ? oldRoomResponse
        : Promise.resolve(teacherState('room-current', '현재 교실', 2))
    ))
    const store = useRoomSessionStore()

    const oldRequest = store.refreshTeacher('room-old')
    await store.refreshTeacher('room-current')
    resolveOldRoom(teacherState('room-old', '이전 교실', 100))
    await oldRequest

    expect(store.teacherState?.roomId).toBe('room-current')
    expect(store.teacherState?.roomName).toBe('현재 교실')
  })
})
