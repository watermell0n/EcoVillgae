import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TeacherQuizResultPanel from './TeacherQuizResultPanel.vue'

describe('TeacherQuizResultPanel', () => {
  it('shows class averages, question rates, and student question results', () => {
    const wrapper = mount(TeacherQuizResultPanel, {
      props: {
        summary: {
          quizId: 'quiz-1',
          title: '월급 퀴즈',
          status: 'CLOSED',
          questionCount: 2,
          expectedStudentCount: 2,
          eligibleStudentCount: 2,
          submittedCount: 2,
          inProgressCount: 0,
          notStartedCount: 0,
          notSubmittedCount: 0,
          students: [
            {
              studentId: 'student-1',
              participantNumber: 1,
              nickname: '하이',
              online: true,
              submissionStatus: 'SUBMITTED',
              correctCount: 2,
              submittedAt: '2026-07-18T00:00:00Z',
              questions: [
                { questionId: 'question-1', orderNo: 1, answered: true, correct: true },
                { questionId: 'question-2', orderNo: 2, answered: true, correct: true },
              ],
            },
            {
              studentId: 'student-2',
              participantNumber: 2,
              nickname: '비빔',
              online: true,
              submissionStatus: 'SUBMITTED',
              correctCount: 1,
              submittedAt: '2026-07-18T00:01:00Z',
              questions: [
                { questionId: 'question-1', orderNo: 1, answered: true, correct: true },
                { questionId: 'question-2', orderNo: 2, answered: true, correct: false },
              ],
            },
          ],
          questions: [
            {
              questionId: 'question-1',
              orderNo: 1,
              prompt: '일한 대가로 받는 돈은?',
              options: [{ id: 'A', text: '소득' }],
              correctAnswer: 'A',
              explanation: '일한 대가를 소득이라고 해요.',
              answeredCount: 2,
              correctCount: 2,
            },
            {
              questionId: 'question-2',
              orderNo: 2,
              prompt: '공동체를 위해 모으는 돈은?',
              options: [{ id: 'B', text: '세금' }],
              correctAnswer: 'B',
              explanation: '세금은 공동체를 위해 사용해요.',
              answeredCount: 2,
              correctCount: 1,
            },
          ],
        },
      },
    })

    expect(wrapper.text()).toContain('1.5 / 2')
    expect(wrapper.text()).toContain('100%')
    expect(wrapper.text()).toContain('50%')
    expect(wrapper.text()).toContain('1번 하이')
    expect(wrapper.text()).toContain('문제 2 · 오답')
  })
})
