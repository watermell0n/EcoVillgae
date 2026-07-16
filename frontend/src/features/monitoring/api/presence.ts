import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface PresenceHeartbeatResponse {
  studentId: string
  connectionStatus: 'ONLINE'
  serverStage: string
  serverSyncVersion: number
  synchronizedWithRoom: boolean
  receivedAt: string
}

export async function sendPresenceHeartbeat(input: {
  reportedStage: string
  currentModule: string | null
  currentPage: string
  clientSyncVersion: number
  visibility: 'VISIBLE' | 'HIDDEN'
}) {
  const response = await http.post<ApiResponse<PresenceHeartbeatResponse>>(
    '/student/presence/heartbeat',
    input,
  )
  return response.data.data
}
