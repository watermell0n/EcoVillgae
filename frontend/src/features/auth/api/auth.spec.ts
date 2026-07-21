import { beforeEach, describe, expect, it, vi } from 'vitest'

import { enterStudent, loginStudent } from './auth'

const httpModule = vi.hoisted(() => ({
  post: vi.fn(),
  setStudentSessionToken: vi.fn(),
  clearStudentSessionToken: vi.fn(),
}))

vi.mock('../../../shared/api/http', () => ({
  http: { post: httpModule.post },
  setStudentSessionToken: httpModule.setStudentSessionToken,
  clearStudentSessionToken: httpModule.clearStudentSessionToken,
}))

describe('student auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('stores the issued student token in the current window session', async () => {
    httpModule.post.mockResolvedValue({
      data: { data: { studentId: 'student-1' } },
      headers: { 'x-student-session-token': 'window-token-1' },
    })

    await loginStudent('ECO-01', '1234')

    expect(httpModule.setStudentSessionToken).toHaveBeenCalledWith('window-token-1')
  })

  it('sends a two-digit classroom code as the student number without changing the stored code', async () => {
    httpModule.post.mockResolvedValue({
      data: { data: { participationCode: 'ECO-ROOM-01', participantNumber: 1 } },
      headers: {},
    })

    await enterStudent('01', 'room-1')

    expect(httpModule.post).toHaveBeenCalledWith('/student/auth/enter', {
      roomId: 'room-1',
      participantNumber: 1,
    })
  })

  it('does not silently turn an unscoped student number into an old demo code', async () => {
    httpModule.post.mockResolvedValue({
      data: { data: { participationCode: '01', participantNumber: 1 } },
      headers: {},
    })

    await enterStudent('01')

    expect(httpModule.post).toHaveBeenCalledWith('/student/auth/enter', {
      participationCode: '01',
    })
  })
})
