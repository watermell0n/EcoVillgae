import axios from 'axios'

export const STUDENT_SESSION_TOKEN_KEY = 'ecovillage.studentSessionToken'

export function getStudentSessionToken() {
  return sessionStorage.getItem(STUDENT_SESSION_TOKEN_KEY)
}

export function setStudentSessionToken(token: string) {
  sessionStorage.setItem(STUDENT_SESSION_TOKEN_KEY, token)
}

export function clearStudentSessionToken() {
  sessionStorage.removeItem(STUDENT_SESSION_TOKEN_KEY)
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 5000,
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  if (config.url?.startsWith('/student/')) {
    const token = getStudentSessionToken()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
  }
  return config
})

