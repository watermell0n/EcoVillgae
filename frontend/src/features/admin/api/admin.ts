import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export type ConnectionStatus = 'ONLINE' | 'UNSTABLE' | 'AWAY' | 'OFFLINE' | 'NEVER_CONNECTED'
export type SyncStatus = 'SYNCED' | 'DELAYED' | 'PERSONAL_PROGRESS' | 'UNKNOWN'

export interface AdminRoomMonitoring {
  roomId: string
  roomName: string
  roomStatus: string
  currentDay: number
  currentStage: string
  version: number
  syncVersion: number
  expectedStudentCount: number
  onlineCount: number
  unstableCount: number
  awayCount: number
  offlineCount: number
  neverConnectedCount: number
  updatedAt: string
}

export interface AdminStudentMonitoring {
  studentId: string
  participantNumber: number
  participationCode: string
  nickname: string | null
  studentStatus: string
  connectionStatus: ConnectionStatus
  currentPage: string | null
  currentModule: string | null
  reportedStage: string | null
  roomStage: string
  clientSyncVersion: number
  roomSyncVersion: number
  syncStatus: SyncStatus
  flowMode: string
  effectiveStage: string
  completedActivityCount: number
  totalActivityCount: number
  rewardRateBps: number
  walletBalance: number
  firstSeenAt: string | null
  lastSeenAt: string | null
  reconnectedAt: string | null
}

export interface AdminRoomStudents {
  roomId: string
  roomName: string
  currentStage: string
  syncVersion: number
  students: AdminStudentMonitoring[]
}

export interface AdminActivity {
  eventId: string
  actorType: string
  eventType: string
  stage: string | null
  module: string | null
  page: string | null
  requestId: string | null
  metadata: Record<string, unknown>
  occurredAt: string
}

export async function getAdminRooms() {
  const response = await http.get<ApiResponse<AdminRoomMonitoring[]>>('/admin/rooms')
  return response.data.data
}

export async function getAdminRoomStudents(roomId: string) {
  const response = await http.get<ApiResponse<AdminRoomStudents>>(
    `/admin/rooms/${roomId}/students`,
  )
  return response.data.data
}

export async function getAdminStudentActivities(roomId: string, studentId: string) {
  const response = await http.get<ApiResponse<AdminActivity[]>>(
    `/admin/rooms/${roomId}/students/${studentId}/activities`,
  )
  return response.data.data
}

export async function terminateAdminStudentSession(
  roomId: string,
  studentId: string,
  reason: string,
) {
  const response = await http.post<
    ApiResponse<{ studentId: string; revokedSessionCount: number; terminatedAt: string }>
  >(`/admin/rooms/${roomId}/students/${studentId}/session-termination`, { reason })
  return response.data.data
}

export async function sendAdminRoomCommand(
  roomId: string,
  command: string,
  targetStage: string,
  expectedVersion: number,
) {
  const response = await http.post<ApiResponse<{ state: { version: number; currentStage: string } }>>(
    `/admin/rooms/${roomId}/commands`,
    { command, targetStage, expectedVersion },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return response.data.data
}

export async function forceAdminStudentToCurrentStage(roomId: string, studentId: string) {
  const response = await http.post<ApiResponse<unknown>>(
    `/admin/rooms/${roomId}/students/${studentId}/force-current-stage`,
  )
  return response.data.data
}

export async function forceAdminStudentView(roomId: string, studentId: string, targetStage: string) {
  const response = await http.post<ApiResponse<unknown>>(
    `/admin/rooms/${roomId}/students/${studentId}/force-view`,
    { targetStage },
  )
  return response.data.data
}

export async function adjustAdminStudentWallet(
  roomId: string,
  studentId: string,
  amount: number,
  reason: string,
) {
  const response = await http.post<ApiResponse<{
    studentId: string
    balance: number
    adjustedAmount: number
    adjustedAt: string
  }>>(`/admin/rooms/${roomId}/students/${studentId}/wallet-adjustments`, { amount, reason })
  return response.data.data
}

export async function deleteAdminRoom(roomId: string) {
  const response = await http.delete<ApiResponse<AdminRoomMonitoring>>(`/admin/rooms/${roomId}`)
  return response.data.data
}
