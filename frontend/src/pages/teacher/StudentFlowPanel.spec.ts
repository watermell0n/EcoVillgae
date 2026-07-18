import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import StudentFlowPanel from './StudentFlowPanel.vue'

const flowApi = vi.hoisted(() => ({
  approveStudentJoin: vi.fn(),
  getTeacherStudentFlows: vi.fn(),
  reopenStudentActivity: vi.fn(),
  waiveStudentActivity: vi.fn(),
}))

vi.mock('../../features/flow/api/teacherFlow', () => flowApi)

function flow(joinStatus: string, effectiveStage: string) {
  return {
    roomId: 'room-1',
    roomStage: 'DAY2_QUIZ_SETTLEMENT',
    students: [{
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '하이',
      studentStatus: 'ACTIVE',
      online: true,
      flow: {
        studentId: 'student-1',
        roomId: 'room-1',
        roomStage: 'DAY2_QUIZ_SETTLEMENT',
        effectiveStage,
        flowMode: 'AWAITING_JOIN',
        joinStatus,
        attemptType: 'LATE',
        rewardRateBps: 8000,
        completedCount: 4,
        totalCount: 4,
        canAdvance: false,
      },
    }],
  }
}

describe('StudentFlowPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    flowApi.getTeacherStudentFlows
      .mockResolvedValueOnce(flow('PENDING', 'DAY2_QUIZ'))
      .mockResolvedValue(flow('APPROVED_PENDING_BOUNDARY', 'DAY2_QUIZ_SETTLEMENT'))
    flowApi.approveStudentJoin.mockResolvedValue({})
  })

  it('shows that boundary approval moved the student to the current stage', async () => {
    const wrapper = mount(StudentFlowPanel, { props: { roomId: 'room-1' } })
    await flushPromises()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(flowApi.approveStudentJoin).toHaveBeenCalledWith('room-1', 'student-1')
    expect(wrapper.text()).toContain('학생을 현재 수업 화면으로 이동시켰습니다.')
    expect(wrapper.text()).toContain('승인 완료 · 학생 화면은 현재 단계로 이동')
    expect(wrapper.find('button').exists()).toBe(false)
    wrapper.unmount()
  })
})
