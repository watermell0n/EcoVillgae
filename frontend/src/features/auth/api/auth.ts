import {
  clearStudentSessionToken,
  http,
  setStudentSessionToken,
} from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface TeacherRoom {
  roomId: string
  name: string
  expectedStudentCount: number
  connectedStudentCount: number
  status: string
  currentDay: number
  currentStage: string
  mode: string
  syncVersion: number
  updatedAt: string
}

export interface TeacherSession {
  teacherId: string
  displayName: string
  rooms: TeacherRoom[]
}

export type StudentRequiredAction = 'SETUP' | 'LOGIN' | 'WAIT' | 'ROOM_CLOSED'

export interface StudentEntry {
  participationCode: string
  roomId: string
  roomName: string
  participantNumber: number
  requiredAction: StudentRequiredAction
}

export interface StudentSession {
  studentId: string
  roomId: string
  roomName: string
  participantNumber: number
  nickname: string
  roomStatus: string
  currentDay: number
  currentStage: string
  mode: string
  walletBalance: number
  syncVersion: number
  connectionStatus: string
}

export async function loginTeacher(loginId: string, password: string) {
  const response = await http.post<ApiResponse<TeacherSession>>('/auth/teacher/login', {
    loginId,
    password,
  })
  return response.data.data
}

export async function getTeacherMe() {
  const response = await http.get<ApiResponse<TeacherSession>>('/auth/me')
  return response.data.data
}

export async function enterStudent(participationCode: string) {
  const normalizedCode = participationCode.trim().toUpperCase()
  const response = await http.post<ApiResponse<Omit<StudentEntry, 'participationCode'>>>(
    '/student/auth/enter',
    { participationCode: normalizedCode },
  )
  clearStudentSessionToken()
  return { participationCode: normalizedCode, ...response.data.data }
}

export async function setupStudent(
  participationCode: string,
  nickname: string,
  password: string,
) {
  const response = await http.post<ApiResponse<StudentSession>>('/student/auth/setup', {
    participationCode,
    nickname,
    password,
  })
  storeStudentSessionToken(response.headers['x-student-session-token'])
  return response.data.data
}

export async function loginStudent(participationCode: string, password: string) {
  const response = await http.post<ApiResponse<StudentSession>>('/student/auth/login', {
    participationCode,
    password,
  })
  storeStudentSessionToken(response.headers['x-student-session-token'])
  return response.data.data
}

export async function getStudentMe() {
  const response = await http.get<ApiResponse<StudentSession>>('/student/me')
  return response.data.data
}

function storeStudentSessionToken(value: unknown) {
  if (typeof value === 'string' && value.trim()) {
    setStudentSessionToken(value.trim())
  }
}
