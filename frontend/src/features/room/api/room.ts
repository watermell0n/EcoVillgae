import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface ModuleState {
  type: string
  state: 'LOCKED' | 'GUIDED' | 'ACTIVE' | 'CLOSED'
}

export type GuidedActivityView = 'HOME' | 'SHOP' | 'DEPOSIT' | 'INVESTMENT' | 'KNOWLEDGE' | 'WALLET'

export interface TeacherRoomState {
  roomId: string
  roomName: string
  roomStatus: string
  currentDay: number
  currentStage: string
  mode: string
  guidedActivityView?: GuidedActivityView
  version: number
  syncVersion: number
  expectedStudentCount: number
  connectedStudentCount: number
  modules: ModuleState[]
  changed: boolean
  updatedAt: string
}

export interface StudentRoomState {
  roomId: string
  roomStatus: string
  currentDay: number
  currentStage: string
  roomStage?: string
  effectiveStage?: string
  mode: string
  guidedActivityView?: GuidedActivityView
  walletBalance: number
  modules: ModuleState[]
  syncVersion: number
  changed: boolean
  flowMode?: 'LIVE' | 'CATCH_UP' | 'AWAITING_JOIN' | 'LOCKED'
  joinStatus?: string
  attemptType?: 'LIVE' | 'LATE' | 'RECOVERY'
  rewardRateBps?: number
  completedActivityCount?: number
  totalActivityCount?: number
}

export interface TeacherRoomStudent {
  studentId: string
  participantNumber: number
  participationCode: string
  nickname: string | null
  status: string
  online: boolean
  connectionStatus: 'ONLINE' | 'UNSTABLE' | 'AWAY' | 'OFFLINE' | 'NEVER_CONNECTED'
  lastSeenAt: string | null
}

export interface RoomPreparation {
  roomId: string
  name: string
  expectedStudentCount: number
  connectedStudentCount: number
  status: string
  currentStage: string
  students: TeacherRoomStudent[]
}

export interface RoomCommandResponse {
  state: TeacherRoomState
  replayed: boolean
}

export async function getTeacherRoomState(roomId: string, syncVersion?: number) {
  const response = await http.get<ApiResponse<TeacherRoomState>>(
    `/teacher/rooms/${roomId}/state`,
    { params: syncVersion === undefined ? undefined : { syncVersion } },
  )
  return response.data.data
}

export async function getStudentRoomState(syncVersion?: number) {
  const response = await http.get<ApiResponse<StudentRoomState>>('/student/room-state', {
    params: syncVersion === undefined ? undefined : { syncVersion },
  })
  return response.data.data
}

export async function createTeacherRoom(name: string, expectedStudentCount: number) {
  const response = await http.post<ApiResponse<RoomPreparation>>('/teacher/rooms', {
    name,
    expectedStudentCount,
  })
  return response.data.data
}

export async function deleteTeacherRoom(roomId: string) {
  const response = await http.delete<ApiResponse<RoomPreparation>>(`/teacher/rooms/${roomId}`)
  return response.data.data
}

export async function getTeacherRoomStudents(roomId: string) {
  const response = await http.get<ApiResponse<RoomPreparation>>(
    `/teacher/rooms/${roomId}/students`,
  )
  return response.data.data
}

export async function setRoomStage(roomId: string, targetStage: string, expectedVersion: number) {
  return sendRoomCommand(roomId, 'SET_STAGE', targetStage, expectedVersion)
}

export async function setTeacherGuidedActivityView(
  roomId: string,
  view: GuidedActivityView,
  expectedVersion: number,
) {
  const response = await http.post<ApiResponse<TeacherRoomState>>(
    `/teacher/rooms/${roomId}/guided-activity`,
    { view, expectedVersion },
  )
  return response.data.data
}

export async function sendRoomCommand(
  roomId: string,
  command: 'SET_STAGE' | 'OPEN_QUIZ' | 'CLOSE_QUIZ' | 'FORCE_CLOSE_QUIZ' | 'CLOSE_DAY1' | 'START_DAY2' | 'COMPLETE_ROOM',
  targetStage: string,
  expectedVersion: number,
) {
  const idempotencyKey = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
  const response = await http.post<ApiResponse<RoomCommandResponse>>(
    `/teacher/rooms/${roomId}/commands`,
    {
      command,
      targetStage,
      expectedVersion,
    },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}
