import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { TeacherQuizSummary } from '../../features/quiz/api/quiz'
import TeacherQuizMirror from './TeacherQuizMirror.vue'

const summary: TeacherQuizSummary = {
  quizId: 'quiz-1',
  title: '독서 퀴즈',
  status: 'READY',
  questionCount: 2,
  expectedStudentCount: 24,
  eligibleStudentCount: 2,
  submittedCount: 0,
  inProgressCount: 0,
  notStartedCount: 2,
  notSubmittedCount: 2,
  students: [
    {
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      online: true,
      submissionStatus: 'NOT_STARTED',
      correctCount: null,
      submittedAt: null,
      questions: [],
    },
    {
      studentId: 'student-2',
      participantNumber: 2,
      nickname: '비빔밥',
      online: false,
      submissionStatus: 'NOT_STARTED',
      correctCount: null,
      submittedAt: null,
      questions: [],
    },
  ],
  questions: [1, 2].map((orderNo) => ({
    questionId: `question-${orderNo}`,
    orderNo,
    prompt: `${orderNo}번 문제`,
    options: [{ id: 'A', text: '선택지' }],
    correctAnswer: 'A',
    explanation: null,
    answeredCount: 0,
    correctCount: 0,
  })),
}

describe('TeacherQuizMirror', () => {
  it('emphasizes quiz start and keeps question navigation visually secondary', async () => {
    const wrapper = mount(TeacherQuizMirror, { props: { summary } })

    expect(wrapper.get('.teacher-quiz-ready-message').text()).toBe(
      '학생 화면은 잠겨 있습니다. 설명을 마쳤다면 퀴즈를 시작하세요.',
    )

    const startButton = wrapper.get('.teacher-quiz-start-button')
    expect(startButton.text()).toBe('퀴즈 시작하기')
    await startButton.trigger('click')
    expect(wrapper.emitted('open')).toHaveLength(1)

    const navigationButtons = wrapper.findAll('.teacher-quiz-step-button')
    expect(navigationButtons).toHaveLength(2)
    expect(navigationButtons.every((button) => button.classes().includes('button-secondary'))).toBe(true)

    await navigationButtons[1]!.trigger('click')
    expect(wrapper.text()).toContain('2번 문제')

    await wrapper.setProps({ summary: { ...summary, status: 'OPEN' } })
    const closeButton = wrapper.get('.teacher-quiz-close-button')
    expect(closeButton.text()).toBe('퀴즈 마감하기')
    await closeButton.trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
