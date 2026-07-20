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
    icon: '🏦',
    example: '100비빔 예금 → 110비빔',
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

  it('shows the learning icon and example in the page card', async () => {
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

    expect(wrapper.text()).toContain('🏦')
    expect(wrapper.text()).toContain('📌 예시')
    expect(wrapper.text()).toContain('100비빔 예금 → 110비빔')
  })

  it('shows only the requested plain guidance on the rational consumption page', async () => {
    const rational = learningFixture('필요와 원하는 것을 구분해요', 1)
    rational.currentPage.topicTitle = '합리적인 소비'
    rational.currentPage.content = '기존 설명 문구'
    rational.currentPage.keyPoints = ['기존 목록 문구']
    rational.currentPage.example = '기존 예시 문구'
    learningApi.getStudentLearning.mockReset().mockResolvedValue(rational)

    const wrapper = mount(LearningStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('생활에 꼭 필요한 것부터 선택해요. 가격과 지갑 잔액, 앞으로 쓸 돈을 함께 비교해요.')
    expect(wrapper.text()).not.toContain('기존 설명 문구')
    expect(wrapper.text()).not.toContain('기존 목록 문구')
    expect(wrapper.text()).not.toContain('기존 예시 문구')
    expect(wrapper.find('.learning-card ul').exists()).toBe(false)
  })
})
