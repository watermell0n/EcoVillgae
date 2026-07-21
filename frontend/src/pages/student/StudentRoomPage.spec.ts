import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { StudentRoomState } from '../../features/room/api/room'
import { useRoomSessionStore } from '../../stores/roomSession'
import StudentRoomPage from './StudentRoomPage.vue'

const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: vi.fn(),
  useRoute: () => ({ params: { roomId: 'room-1' } }),
  useRouter: () => router,
}))

const authApi = vi.hoisted(() => ({ getStudentMe: vi.fn() }))
vi.mock('../../features/auth/api/auth', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../features/auth/api/auth')>(),
  getStudentMe: authApi.getStudentMe,
}))

const roomApi = vi.hoisted(() => ({ getStudentRoomState: vi.fn() }))
vi.mock('../../features/room/api/room', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../features/room/api/room')>(),
  getStudentRoomState: roomApi.getStudentRoomState,
}))

const presenceApi = vi.hoisted(() => ({ sendPresenceHeartbeat: vi.fn() }))
vi.mock('../../features/monitoring/api/presence', () => presenceApi)

const flowApi = vi.hoisted(() => ({ advanceStudentFlow: vi.fn() }))
vi.mock('../../features/flow/api/flow', () => flowApi)
vi.mock('./stages/stageComponents', () => ({
  getStudentStageComponent: () => ({ template: '<div class="stub-stage">활동 화면</div>' }),
  isPlaceholderStage: () => false,
}))

const liveState: StudentRoomState = {
  roomId: 'room-1',
  roomStatus: 'ACTIVE',
  currentDay: 1,
  currentStage: 'DAY1_SUMMARY',
  roomStage: 'DAY1_SUMMARY',
  effectiveStage: 'DAY1_SUMMARY',
  mode: 'LIVE',
  walletBalance: 100,
  modules: [],
  syncVersion: 1,
  changed: true,
  flowMode: 'LIVE',
}

describe('StudentRoomPage day-one closing', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    authApi.getStudentMe.mockResolvedValue({
      studentId: 'student-1',
      roomId: 'room-1',
      roomName: '푸른반',
      participantNumber: 3,
      nickname: '비비',
      roomStatus: 'ACTIVE',
      currentDay: 1,
      currentStage: 'DAY1_SUMMARY',
      mode: 'LIVE',
      walletBalance: 100,
      syncVersion: 1,
      connectionStatus: 'CONNECTED',
    })
    roomApi.getStudentRoomState.mockResolvedValue(structuredClone(liveState))
    presenceApi.sendPresenceHeartbeat.mockResolvedValue(undefined)
    flowApi.advanceStudentFlow.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function mountAtDayOneClose() {
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'startStudentPolling').mockImplementation(() => undefined)
    const stopPolling = vi.spyOn(roomSessionStore, 'stopPolling')
    const wrapper = mount(StudentRoomPage)
    await flushPromises()

    roomSessionStore.studentState = {
      ...structuredClone(liveState),
      roomStatus: 'DAY1_CLOSED',
      currentStage: 'DAY1_CLOSED',
      roomStage: 'DAY1_CLOSED',
      effectiveStage: 'DAY1_CLOSED',
      mode: 'CLOSED',
      syncVersion: 2,
    }
    await flushPromises()
    return { roomSessionStore, wrapper, stopPolling }
  }

  async function mountAtCatchUpQuiz() {
    roomApi.getStudentRoomState.mockResolvedValue({
      ...structuredClone(liveState),
      currentStage: 'DAY1_QUIZ',
      roomStage: 'DAY1_SUMMARY',
      effectiveStage: 'DAY1_QUIZ',
      flowMode: 'CATCH_UP',
      totalActivityCount: 3,
      completedActivityCount: 1,
      rewardRateBps: 8000,
    })
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'startStudentPolling').mockImplementation(() => undefined)
    const wrapper = mount(StudentRoomPage)
    await flushPromises()
    return wrapper
  }

  async function mountAwaitingJoin(joinStatus: 'PENDING' | 'APPROVED_PENDING_BOUNDARY') {
    roomApi.getStudentRoomState.mockResolvedValue({
      ...structuredClone(liveState),
      flowMode: 'AWAITING_JOIN',
      joinStatus,
    })
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'startStudentPolling').mockImplementation(() => undefined)
    const wrapper = mount(StudentRoomPage)
    await flushPromises()
    return wrapper
  }

  it('shows a large non-dismissible five-second closing notice without stopping day-one polling', async () => {
    const { wrapper, stopPolling } = await mountAtDayOneClose()

    const notice = wrapper.get('.student-closing-countdown')
    expect(notice.text()).toContain('5초 뒤에 종료됩니다.')
    expect(notice.find('button').exists()).toBe(false)
    expect(wrapper.find('.student-context-bar').exists()).toBe(false)
    expect(stopPolling).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('hides the fixed catch-up action as soon as it is clicked', async () => {
    roomApi.getStudentRoomState.mockResolvedValue({
      ...structuredClone(liveState),
      currentStage: 'DAY1_PROFILE',
      roomStage: 'DAY1_SUMMARY',
      effectiveStage: 'DAY1_PROFILE',
      flowMode: 'CATCH_UP',
      totalActivityCount: 3,
      completedActivityCount: 0,
    })
    const roomSessionStore = useRoomSessionStore()
    vi.spyOn(roomSessionStore, 'startStudentPolling').mockImplementation(() => undefined)
    const wrapper = mount(StudentRoomPage)
    await flushPromises()

    const action = wrapper.get('.catch-up-next')
    await action.trigger('click')
    await flushPromises()

    expect(flowApi.advanceStudentFlow).toHaveBeenCalledOnce()
    expect(wrapper.find('.catch-up-next').exists()).toBe(false)
    wrapper.unmount()
  })

  it('automatically opens the day-one closing page after five seconds', async () => {
    const { wrapper, stopPolling } = await mountAtDayOneClose()

    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.text()).toContain('1일차 활동이 마감되었습니다')
    expect(wrapper.find('.student-closing-countdown').exists()).toBe(false)
    expect(stopPolling).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('automatically opens day two when the teacher starts it after the day-one close', async () => {
    const { roomSessionStore, wrapper, stopPolling } = await mountAtDayOneClose()

    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()
    roomSessionStore.studentState = {
      ...structuredClone(liveState),
      currentDay: 2,
      currentStage: 'DAY2_LOGIN',
      roomStage: 'DAY2_LOGIN',
      effectiveStage: 'DAY2_LOGIN',
      syncVersion: 3,
    }
    await flushPromises()

    expect(wrapper.text()).not.toContain('1일차 활동이 마감되었습니다')
    expect(wrapper.find('.stub-stage').exists()).toBe(true)
    expect(stopPolling).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('lets a late student dismiss the catch-up quiz notice', async () => {
    const wrapper = await mountAtCatchUpQuiz()

    const notice = wrapper.get('.catch-up-quiz-notice')
    expect(notice.text()).toContain('퀴즈 제출을 완료하면 다음 활동으로 이동할 수 있어요.')
    await notice.get('[aria-label="퀴즈 제출 안내 닫기"]').trigger('click')

    expect(wrapper.find('.catch-up-quiz-notice').exists()).toBe(false)
    wrapper.unmount()
  })

  it('automatically hides the catch-up quiz notice after five seconds', async () => {
    const wrapper = await mountAtCatchUpQuiz()

    expect(wrapper.find('.catch-up-quiz-notice').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.find('.catch-up-quiz-notice').exists()).toBe(false)
    wrapper.unmount()
  })

  it('briefly shows the completed catch-up notice without live-waiting labels', async () => {
    const wrapper = await mountAwaitingJoin('PENDING')

    expect(wrapper.text()).toContain('필수 활동을 모두 마쳤어요')
    expect(wrapper.text()).not.toContain('실시간 대기')
    expect(wrapper.text()).not.toContain('현재 수업 단계 마무리 대기')
    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.find('.catch-up-banner--waiting').exists()).toBe(false)
    wrapper.unmount()
  })

  it('briefly shows the teacher approval notice at the same size', async () => {
    const wrapper = await mountAwaitingJoin('APPROVED_PENDING_BOUNDARY')

    expect(wrapper.text()).toContain('선생님 승인이 완료됐어요')
    expect(wrapper.get('.catch-up-banner--waiting').find('span').exists()).toBe(false)
    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.find('.catch-up-banner--waiting').exists()).toBe(false)
    wrapper.unmount()
  })
})
