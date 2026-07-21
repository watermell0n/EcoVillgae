import { describe, expect, it } from 'vitest'

import type { TeacherStudentDay1Summary } from '../../features/day1/api/day1'
import type { TeacherQuizStudent } from '../../features/quiz/api/quiz'
import { getConnectedDay1Counts, getConnectedQuizCounts } from './liveParticipation'

describe('live participation counts', () => {
  it('counts quiz progress using only currently connected students', () => {
    const students = [
      { online: true, submissionStatus: 'SUBMITTED' },
      { online: true, submissionStatus: 'IN_PROGRESS' },
      { online: true, submissionStatus: 'NOT_STARTED' },
      { online: false, submissionStatus: 'NOT_STARTED' },
    ] as TeacherQuizStudent[]

    expect(getConnectedQuizCounts(students)).toMatchObject({
      total: 3,
      submitted: 1,
      inProgress: 1,
      notStarted: 1,
    })
  })

  it('counts day-one choices using only currently connected students', () => {
    const students = [
      { online: true, inactive: false, depositConfirmed: true, investmentConfirmed: false, purchased: true },
      { online: true, inactive: true, depositConfirmed: false, investmentConfirmed: false, purchased: false },
      { online: false, inactive: true, depositConfirmed: false, investmentConfirmed: false, purchased: false },
    ] as TeacherStudentDay1Summary[]

    expect(getConnectedDay1Counts(students)).toEqual({
      total: 2,
      active: 1,
      depositConfirmed: 1,
      investmentConfirmed: 0,
      purchaseParticipants: 1,
      inactive: 1,
    })
  })
})
