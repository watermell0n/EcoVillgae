import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface Day2QuizRewardStudent {
  studentId: string
  participantNumber: number
  nickname: string | null
  submitted: boolean
  correctCount: number
  participationReward: number
  correctReward: number
  totalReward: number
  walletAfter: number
}

export interface Day2QuizRewardPreview {
  roomId: string
  settlementStatus: 'NOT_STARTED' | 'COMPLETED'
  participationRewardPerStudent: number
  correctRewardPerQuestion: number
  targetCount: number
  submittedCount: number
  notSubmittedCount: number
  totalReward: number
  completedAt: string | null
  students: Day2QuizRewardStudent[]
}

export interface Day2QuizRewardSettlement {
  settlement: Day2QuizRewardPreview
  replayed: boolean
}

export async function getTeacherDay2QuizRewardPreview(roomId: string) {
  const response = await http.get<ApiResponse<Day2QuizRewardPreview>>(
    `/teacher/rooms/${roomId}/settlements/day-2-quiz-reward/preview`,
  )
  return response.data.data
}

export async function settleTeacherDay2QuizReward(roomId: string, idempotencyKey: string) {
  const response = await http.post<ApiResponse<Day2QuizRewardSettlement>>(
    `/teacher/rooms/${roomId}/settlements/day-2-quiz-reward`,
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}

export async function getStudentDay2QuizReward() {
  const response = await http.get<ApiResponse<Day2QuizRewardStudent>>(
    '/student/day-2/quiz-reward',
  )
  return response.data.data
}
