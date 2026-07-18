import { describe, expect, it } from 'vitest'

import type { TeacherRoomStudent } from '../api/room'
import { getLobbyAttendance } from './attendance'

function student(
  participantNumber: number,
  status: string,
  online: boolean,
): TeacherRoomStudent {
  return {
    studentId: `student-${participantNumber}`,
    participantNumber,
    participationCode: `CODE-${participantNumber}`,
    nickname: status === 'ACTIVE' ? `학생${participantNumber}` : null,
    status,
    online,
    lastSeenAt: null,
  }
}

describe('getLobbyAttendance', () => {
  it('separates students who never joined from active students who disconnected', () => {
    const result = getLobbyAttendance([
      student(1, 'INVITED', false),
      student(2, 'ACTIVE', false),
      student(3, 'ACTIVE', true),
      student(4, 'ABSENT', false),
    ])

    expect(result.notJoined.map((item) => item.participantNumber)).toEqual([1])
    expect(result.disconnected.map((item) => item.participantNumber)).toEqual([2])
  })
})

