import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SettlementStageView from './SettlementStageView.vue'

const quizApi = vi.hoisted(() => ({
  getDayOneQuizResult: vi.fn(),
}))

vi.mock('../../../features/quiz/api/quiz', () => quizApi)

describe('SettlementStageView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    quizApi.getDayOneQuizResult.mockResolvedValue({
      quizId: 'quiz-1',
      title: '1일차 월급 퀴즈',
      questionCount: 2,
      answeredCount: 1,
      correctCount: 1,
      questions: [
        {
          questionId: 'question-1',
          orderNo: 1,
          prompt: '역할의 일을 하고 받는 돈은 무엇일까요?',
          options: [
            { id: 'A', text: '역할 소득' },
            { id: 'B', text: '상품 가격' },
          ],
          selectedAnswer: 'A',
          correctAnswer: 'A',
          correct: true,
          explanation: '역할을 수행하고 받는 돈을 역할 소득이라고 해요.',
        },
        {
          questionId: 'question-2',
          orderNo: 2,
          prompt: '내야 하는 세금은 무엇일까요?',
          options: [
            { id: 'A', text: '소득세' },
            { id: 'B', text: '이자' },
          ],
          selectedAnswer: null,
          correctAnswer: 'A',
          correct: false,
          explanation: '소득이 생기면 소득세를 내요.',
        },
      ],
    })
  })

  it('shows per-question answers and explanations before the payslip', async () => {
    const wrapper = mount(SettlementStageView)
    await flushPromises()

    expect(quizApi.getDayOneQuizResult).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('맞힌 문제')
    expect(wrapper.text()).toContain('1 / 2')
    expect(wrapper.text()).toContain('A. 역할 소득')
    expect(wrapper.text()).toContain('답하지 않음')
    expect(wrapper.text()).toContain('소득이 생기면 소득세를 내요.')
    expect(wrapper.text()).toContain('선생님이 월급을 계산하고 있어요.')
  })

  it('offers retry when the result cannot be loaded', async () => {
    quizApi.getDayOneQuizResult.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(SettlementStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('퀴즈 결과를 불러오지 못했어요.')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(quizApi.getDayOneQuizResult).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('정답과 해설')
  })
})
