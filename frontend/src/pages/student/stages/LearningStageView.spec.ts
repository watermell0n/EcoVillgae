import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useRoomSessionStore } from '../../../stores/roomSession'
import LearningStageView from './LearningStageView.vue'

const learningApi = vi.hoisted(() => ({
  changeTeacherLearningPage: vi.fn(),
  getStudentLearning: vi.fn(),
  getStudentLearningTopic: vi.fn(),
  getStudentLearningTopics: vi.fn(),
  getTeacherLearning: vi.fn(),
}))

vi.mock('../../../features/learning/api/learning', () => learningApi)

function learningFixture(title: string, syncVersion: number) {
  const page = {
    pageId: `page-${syncVersion}`,
    topicCode: 'DEPOSIT',
    topicTitle: '예금과 이자',
    pageOrder: syncVersion,
    title,
    content: '예금의 원리를 알아봐요.',
    keyPoints: ['원금과 이자를 확인해요.'],
  }
  return {
    stage: 'DAY1_FINANCE_GUIDED',
    mode: 'GUIDED',
    syncVersion,
    currentIndex: 0,
    pages: [page],
    currentPage: page,
  }
}

describe('LearningStageView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    learningApi.getStudentLearning
      .mockResolvedValueOnce(learningFixture('예금은 무엇일까요?', 1))
      .mockResolvedValueOnce(learningFixture('이자는 기다린 대가예요', 2))
  })

  it('refreshes the page when the room sync version changes', async () => {
    const roomSessionStore = useRoomSessionStore()
    roomSessionStore.studentState = {
      roomId: 'room-1',
      roomStatus: 'DAY1_ACTIVE',
      currentDay: 1,
      currentStage: 'DAY1_FINANCE_GUIDED',
      mode: 'GUIDED',
      walletBalance: 100,
      modules: [],
      syncVersion: 1,
      changed: true,
    }
    const wrapper = mount(LearningStageView)
    await flushPromises()
    expect(wrapper.text()).toContain('예금은 무엇일까요?')

    roomSessionStore.studentState = {
      ...roomSessionStore.studentState,
      syncVersion: 2,
    }
    await flushPromises()

    expect(learningApi.getStudentLearning).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('이자는 기다린 대가예요')
  })
})
