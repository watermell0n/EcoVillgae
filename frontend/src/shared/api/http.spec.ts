import { beforeEach, describe, expect, it } from 'vitest'

import {
  clearStudentSessionToken,
  http,
  setStudentSessionToken,
} from './http'

describe('student session request isolation', () => {
  beforeEach(() => {
    sessionStorage.clear()
    clearStudentSessionToken()
  })

  it('sends the window-specific student token on student requests only', async () => {
    setStudentSessionToken('student-window-1')
    let studentAuthorization: string | undefined
    let teacherAuthorization: string | undefined

    await http.get('/student/me', {
      adapter: async (config) => {
        studentAuthorization = config.headers.get('Authorization') as string | undefined
        return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
      },
    })
    await http.get('/auth/me', {
      adapter: async (config) => {
        teacherAuthorization = config.headers.get('Authorization') as string | undefined
        return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
      },
    })

    expect(studentAuthorization).toBe('Bearer student-window-1')
    expect(teacherAuthorization).toBeUndefined()
  })
})
