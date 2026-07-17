import { beforeEach, describe, expect, it, vi } from 'vitest'

import { loginStudent } from './auth'

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
})
