import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SettlementStageView from './SettlementStageView.vue'

const quizApi = vi.hoisted(() => ({
  getDayOneQuizResult: vi.fn(),
  getDayTwoQuizResult: vi.fn(),
}))

vi.mock('../../../features/quiz/api/quiz', () => quizApi)

describe('SettlementStageView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    quizApi.getDayOneQuizResult.mockResolvedValue({
      quizId: 'quiz-1',
      title: '독서 퀴즈',
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

  it('shows day-one answers without explanations before the payslip', async () => {
    const wrapper = mount(SettlementStageView)
    await flushPromises()

    expect(quizApi.getDayOneQuizResult).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('맞힌 문제')
    expect(wrapper.text()).toContain('1 / 2')
    expect(wrapper.text()).toContain('A. 역할 소득')
    expect(wrapper.text()).toContain('답하지 않음')
    expect(wrapper.text()).toContain('정답 확인')
    expect(wrapper.text()).not.toContain('해설')
    expect(wrapper.text()).not.toContain('소득이 생기면 소득세를 내요.')
    expect(wrapper.text()).toContain('퀴즈 결과를 함께 확인하는 시간이에요.')
    expect(wrapper.get('.day1-quiz-result-stage').classes()).toContain('has-fixed-notice')

    await wrapper.get('button[aria-label="안내 닫기"]').trigger('click')

    expect(wrapper.find('.settlement-wait-card').exists()).toBe(false)
    expect(wrapper.get('.day1-quiz-result-stage').classes()).not.toContain('has-fixed-notice')
  })

  it('offers retry when the result cannot be loaded', async () => {
    quizApi.getDayOneQuizResult.mockRejectedValueOnce(new Error('network'))
    const wrapper = mount(SettlementStageView)
    await flushPromises()

    expect(wrapper.text()).toContain('퀴즈 결과를 불러오지 못했어요.')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(quizApi.getDayOneQuizResult).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('정답 확인')
    expect(wrapper.text()).not.toContain('해설')
  })

  it('renders all ten question cards inside the result detail section', async () => {
    quizApi.getDayOneQuizResult.mockResolvedValueOnce({
      quizId: 'quiz-10',
      title: '열 문제 퀴즈',
      questionCount: 10,
      answeredCount: 10,
      correctCount: 5,
      questions: Array.from({ length: 10 }, (_, index) => ({
        questionId: `question-${index + 1}`,
        orderNo: index + 1,
        prompt: `${index + 1}번째 문제입니다.`,
        options: [
          { id: 'A', text: '첫 번째 답' },
          { id: 'B', text: '두 번째 답' },
        ],
        selectedAnswer: 'A',
        correctAnswer: index % 2 === 0 ? 'A' : 'B',
        correct: index % 2 === 0,
        explanation: '',
      })),
    })
    const wrapper = mount(SettlementStageView)
    await flushPromises()

    const detail = wrapper.get('.quiz-result-detail')
    expect(detail.findAll('ol > li')).toHaveLength(10)
    expect(detail.text()).toContain('문제 10')
    expect(detail.text()).toContain('10번째 문제입니다.')
  })

  it('loads day two results before showing the reward settlement', async () => {
    quizApi.getDayTwoQuizResult.mockResolvedValue({
      quizId: 'quiz-2',
      title: '경제 퀴즈',
      questionCount: 1,
      answeredCount: 1,
      correctCount: 1,
      questions: [{
        questionId: 'question-3',
        orderNo: 1,
        prompt: '위험을 나누는 투자 방법은?',
        options: [{ id: 'A', text: '분산투자' }],
        selectedAnswer: 'A',
        correctAnswer: 'A',
        correct: true,
        explanation: '분산투자는 위험을 나누는 방법이에요.',
      }],
    })
    const wrapper = mount(SettlementStageView, { props: { dayNumber: 2 } })
    await flushPromises()

    expect(quizApi.getDayTwoQuizResult).toHaveBeenCalledOnce()
    expect(quizApi.getDayOneQuizResult).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('경제 퀴즈 결과')
    expect(wrapper.text()).toContain('정답 수 × 5비빔')
    expect(wrapper.text()).toContain('우리 반 공동기금으로 모을 소득세')
    expect(wrapper.text()).not.toContain('%')
    expect(wrapper.text()).toContain('분산투자는 위험을 나누는 방법이에요.')
  })
})
