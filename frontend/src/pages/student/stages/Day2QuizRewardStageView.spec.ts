import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Day2QuizRewardStageView from './Day2QuizRewardStageView.vue'

const quizRewardApi = vi.hoisted(() => ({
  getStudentDay2QuizReward: vi.fn(),
}))

vi.mock('../../../features/day2/api/quizReward', () => quizRewardApi)

describe('Day2QuizRewardStageView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    quizRewardApi.getStudentDay2QuizReward.mockResolvedValue({
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      submitted: true,
      correctCount: 2,
      participationReward: 0,
      correctReward: 10,
      totalReward: 10,
      walletAfter: 77,
    })
  })

  it('shows the score and explains how the reward was calculated', async () => {
    const wrapper = mount(Day2QuizRewardStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('경제 퀴즈 보상 정산 결과')
    expect(wrapper.text()).toContain('2문제를 맞혔어요!')
    expect(wrapper.text()).toContain('2문제 × 5비빔')
    expect(wrapper.text()).toContain('내가 받은 총 보상')
    expect(wrapper.text()).toContain('10비빔')
    expect(wrapper.text()).not.toContain('참여수당')
    expect(wrapper.text()).toContain('보상은 한 번만 지급돼요.')
  })
})
