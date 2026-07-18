import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'
import type { StudentFlow } from './flow'

export interface TeacherStudentFlow {
  studentId: string
  participantNumber: number
  nickname: string | null
  studentStatus: string
  online: boolean
  flow: StudentFlow | null
}

export interface TeacherFlowList {
  roomId: string
  roomStage: string
  students: TeacherStudentFlow[]
}

export async function getTeacherStudentFlows(roomId: string) {
  const response = await http.get<ApiResponse<TeacherFlowList>>(
    `/teacher/rooms/${roomId}/student-flows`,
  )
  return response.data.data
}

export async function approveStudentJoin(roomId: string, studentId: string) {
  const response = await http.post<ApiResponse<StudentFlow>>(
    `/teacher/rooms/${roomId}/student-flows/${studentId}/approve`,
  )
  return response.data.data
}

export async function waiveStudentActivity(
  roomId: string,
  studentId: string,
  activityCode: string,
  reason: string,
) {
  const response = await http.post<ApiResponse<StudentFlow>>(
    `/teacher/rooms/${roomId}/student-flows/${studentId}/activities/${activityCode}/waive`,
    { reason },
  )
  return response.data.data
}

export async function reopenStudentActivity(
  roomId: string,
  studentId: string,
  activityCode: string,
  reason: string,
) {
  const response = await http.post<ApiResponse<StudentFlow>>(
    `/teacher/rooms/${roomId}/student-flows/${studentId}/activities/${activityCode}/reopen`,
    { reason },
  )
  return response.data.data
}
