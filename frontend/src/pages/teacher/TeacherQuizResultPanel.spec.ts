import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TeacherQuizResultPanel from './TeacherQuizResultPanel.vue'

describe('TeacherQuizResultPanel', () => {
  it('shows class averages, question rates, and student question results', () => {
    const wrapper = mount(TeacherQuizResultPanel, {
      props: {
        dayNumber: 2,
        nextStageLabel: '퀴즈 보상 정산',
        summary: {
          quizId: 'quiz-1',
          title: '경제 퀴즈',
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
    expect(wrapper.text()).toContain('경제 퀴즈 결과')
    expect(wrapper.text()).toContain('퀴즈 보상 정산 단계')
    expect(wrapper.text()).toContain('100%')
    expect(wrapper.text()).toContain('50%')
    expect(wrapper.text()).toContain('1번 하이')
    expect(wrapper.text()).toContain('문제 2 · 오답')
  })

  it('hides explanations from the day-one reading quiz results', () => {
    const wrapper = mount(TeacherQuizResultPanel, {
      props: {
        dayNumber: 1,
        nextStageLabel: '소득·세금 정산',
        summary: {
          quizId: 'quiz-1',
          title: '독서 퀴즈',
          status: 'CLOSED',
          questionCount: 1,
          expectedStudentCount: 1,
          eligibleStudentCount: 1,
          submittedCount: 1,
          inProgressCount: 0,
          notStartedCount: 0,
          notSubmittedCount: 0,
          students: [],
          questions: [{
            questionId: 'question-1',
            orderNo: 1,
            prompt: '주인공의 이름은 무엇인가요?',
            options: [{ id: 'C', text: '푸실' }],
            correctAnswer: 'C',
            explanation: '화면에 표시되면 안 되는 해설입니다.',
            answeredCount: 1,
            correctCount: 1,
          }],
        },
      },
    })

    expect(wrapper.text()).toContain('독서 퀴즈')
    expect(wrapper.text()).toContain('학생들과 정답을 확인해 주세요.')
    expect(wrapper.text()).not.toContain('해설')
  })
})
