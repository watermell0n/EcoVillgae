import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import QuizStageView from './QuizStageView.vue'

const quizApi = vi.hoisted(() => ({
  getCurrentQuiz: vi.fn(),
  saveQuizAnswer: vi.fn(),
  submitQuiz: vi.fn(),
}))

vi.mock('../../../features/quiz/api/quiz', () => quizApi)

const quizFixture = {
  quizId: 'quiz-1',
  title: '1일차 월급 퀴즈',
  dayNumber: 1,
  questionCount: 1,
  submissionStatus: 'NOT_STARTED' as const,
  answeredCount: 0,
  submittedAt: null,
  questions: [
    {
      questionId: 'question-1',
      orderNo: 1,
      questionType: 'MULTIPLE_CHOICE',
      prompt: '역할의 일을 하고 받는 돈은 무엇일까요?',
      savedAnswer: null,
      options: [
        { id: 'A', text: '역할 소득' },
        { id: 'B', text: '상품 가격' },
      ],
    },
  ],
}

describe('QuizStageView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    quizApi.getCurrentQuiz.mockResolvedValue(structuredClone(quizFixture))
    quizApi.saveQuizAnswer.mockResolvedValue({
      questionId: 'question-1',
      answer: 'A',
      answeredCount: 1,
      savedAt: '2026-07-16T00:00:00Z',
    })
    quizApi.submitQuiz.mockResolvedValue({
      quizId: 'quiz-1',
      status: 'SUBMITTED',
      answeredCount: 1,
      questionCount: 1,
      submittedAt: '2026-07-16T00:01:00Z',
    })
  })

  it('saves an answer selected by the student', async () => {
    const wrapper = mount(QuizStageView)
    await flushPromises()

    const options = wrapper.findAll('button.quiz-option')
    expect(options).toHaveLength(2)
    await options[0]!.trigger('click')
    await flushPromises()

    expect(quizApi.saveQuizAnswer).toHaveBeenCalledWith('quiz-1', 'question-1', 'A')
    expect(wrapper.text()).toContain('답안이 저장되었습니다.')
    expect(options[0]!.classes()).toContain('is-selected')
  })

  it('locks the quiz after final submission', async () => {
    const wrapper = mount(QuizStageView)
    await flushPromises()

    await wrapper.get('button.quiz-option').trigger('click')
    await flushPromises()
    await wrapper.get('.quiz-player__footer button:not(.button-secondary)').trigger('click')
    await wrapper.get('.confirm-modal__actions button:not(.button-secondary)').trigger('click')
    await flushPromises()

    expect(quizApi.submitQuiz).toHaveBeenCalledWith('quiz-1')
    expect(wrapper.text()).toContain('퀴즈 제출이 완료되었습니다.')
    expect(wrapper.find('.quiz-options').exists()).toBe(false)
  })
})
