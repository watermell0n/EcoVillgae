import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  getStudentRoomState,
  getTeacherRoomState,
  sendRoomCommand,
  setTeacherGuidedActivityView as setTeacherGuidedActivityViewApi,
  setRoomStage as setRoomStageApi,
  type StudentRoomState,
  type TeacherRoomState,
  type GuidedActivityView,
} from '../features/room/api/room'

export type RoomConnectionStatus = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED'

const POLLING_INTERVAL_MS = 2_000

export const useRoomSessionStore = defineStore('roomSession', () => {
  const teacherState = ref<TeacherRoomState | null>(null)
  const studentState = ref<StudentRoomState | null>(null)
  const connectionStatus = ref<RoomConnectionStatus>('DISCONNECTED')
  const pollError = shallowRef<unknown>(null)

  let pollingTimer: ReturnType<typeof setInterval> | undefined
  let requestInFlight = false
  let activeTeacherRoomId: string | undefined

  function applyTeacherState(nextState: TeacherRoomState) {
    if (activeTeacherRoomId && nextState.roomId !== activeTeacherRoomId) return
    if (
      !teacherState.value
      || teacherState.value.roomId !== nextState.roomId
      || nextState.syncVersion >= teacherState.value.syncVersion
    ) {
      teacherState.value = nextState
    }
  }

  function applyStudentState(nextState: StudentRoomState) {
    if (!studentState.value || nextState.syncVersion >= studentState.value.syncVersion) {
      studentState.value = nextState
    }
  }

  async function refreshTeacher(roomId: string) {
    activeTeacherRoomId = roomId
    const currentSyncVersion =
      teacherState.value?.roomId === roomId ? teacherState.value.syncVersion : undefined
    const state = await getTeacherRoomState(roomId, currentSyncVersion)
    if (activeTeacherRoomId !== roomId) return state
    applyTeacherState(state)
    connectionStatus.value = 'CONNECTED'
    pollError.value = null
    return state
  }

  async function refreshStudent(initialSyncVersion?: number) {
    const state = await getStudentRoomState(studentState.value?.syncVersion ?? initialSyncVersion)
    applyStudentState(state)
    connectionStatus.value = 'CONNECTED'
    pollError.value = null
    return state
  }

  async function runPollingRequest(request: () => Promise<unknown>) {
    if (requestInFlight) return
    requestInFlight = true
    try {
      await request()
    } catch (error) {
      connectionStatus.value = 'RECONNECTING'
      pollError.value = error
    } finally {
      requestInFlight = false
    }
  }

  function startTeacherPolling(roomId: string) {
    stopPolling()
    activeTeacherRoomId = roomId
    connectionStatus.value = 'CONNECTED'
    pollingTimer = setInterval(() => {
      void runPollingRequest(() => refreshTeacher(roomId))
    }, POLLING_INTERVAL_MS)
  }

  function startStudentPolling() {
    stopPolling()
    connectionStatus.value = 'CONNECTED'
    pollingTimer = setInterval(() => {
      void runPollingRequest(() => refreshStudent())
    }, POLLING_INTERVAL_MS)
  }

  function stopPolling() {
    if (pollingTimer !== undefined) {
      clearInterval(pollingTimer)
      pollingTimer = undefined
    }
    requestInFlight = false
    activeTeacherRoomId = undefined
    connectionStatus.value = 'DISCONNECTED'
  }

  function resetTeacherContext() {
    stopPolling()
    teacherState.value = null
    pollError.value = null
  }

  function resetStudentContext() {
    stopPolling()
    studentState.value = null
    pollError.value = null
  }

  async function setStage(roomId: string, targetStage: string) {
    if (!teacherState.value || teacherState.value.roomId !== roomId) {
      await refreshTeacher(roomId)
    }
    const expectedVersion = teacherState.value?.version
    if (expectedVersion === undefined) {
      throw new Error('현재 방 상태를 불러오지 못했습니다.')
    }

    const result = await setRoomStageApi(roomId, targetStage, expectedVersion)
    applyTeacherState(result.state)
    return result
  }

  async function setGuidedActivityView(roomId: string, view: GuidedActivityView) {
    if (!teacherState.value || teacherState.value.roomId !== roomId) await refreshTeacher(roomId)
    const current = teacherState.value
    if (!current) throw new Error('현재 방 상태를 불러오지 못했습니다.')
    const next = await setTeacherGuidedActivityViewApi(roomId, view, current.version)
    applyTeacherState(next)
    return next
  }

  async function controlQuiz(
    roomId: string,
    command: 'OPEN_QUIZ' | 'CLOSE_QUIZ' | 'FORCE_CLOSE_QUIZ',
  ) {
    if (!teacherState.value || teacherState.value.roomId !== roomId) {
      await refreshTeacher(roomId)
    }
    const current = teacherState.value
    if (!current) throw new Error('현재 방 상태를 불러오지 못했습니다.')

    const result = await sendRoomCommand(
      roomId,
      command,
      current.currentStage,
      current.version,
    )
    applyTeacherState(result.state)
    return result
  }

  async function closeDayOne(roomId: string) {
    if (!teacherState.value || teacherState.value.roomId !== roomId) {
      await refreshTeacher(roomId)
    }
    const current = teacherState.value
    if (!current) throw new Error('현재 방 상태를 불러오지 못했습니다.')
    const result = await sendRoomCommand(
      roomId,
      'CLOSE_DAY1',
      current.currentStage,
      current.version,
    )
    applyTeacherState(result.state)
    return result
  }

  async function startDayTwo(roomId: string) {
    if (!teacherState.value || teacherState.value.roomId !== roomId) {
      await refreshTeacher(roomId)
    }
    const current = teacherState.value
    if (!current) throw new Error('현재 방 상태를 불러오지 못했습니다.')
    const result = await sendRoomCommand(
      roomId,
      'START_DAY2',
      'DAY2_LOGIN',
      current.version,
    )
    applyTeacherState(result.state)
    return result
  }

  async function completeRoom(roomId: string) {
    if (!teacherState.value || teacherState.value.roomId !== roomId) {
      await refreshTeacher(roomId)
    }
    const current = teacherState.value
    if (!current) throw new Error('현재 방 상태를 불러오지 못했습니다.')
    const result = await sendRoomCommand(
      roomId,
      'COMPLETE_ROOM',
      'DAY2_COMPLETED',
      current.version,
    )
    applyTeacherState(result.state)
    return result
  }

  return {
    teacherState,
    studentState,
    connectionStatus,
    pollError,
    refreshTeacher,
    refreshStudent,
    startTeacherPolling,
    startStudentPolling,
    stopPolling,
    resetTeacherContext,
    resetStudentContext,
    setStage,
    setGuidedActivityView,
    controlQuiz,
    closeDayOne,
    startDayTwo,
    completeRoom,
  }
})
