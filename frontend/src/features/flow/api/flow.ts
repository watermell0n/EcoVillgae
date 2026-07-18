import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface StudentFlow {
  studentId: string
  roomId: string
  roomStage: string
  effectiveStage: string
  flowMode: 'LIVE' | 'CATCH_UP' | 'AWAITING_JOIN' | 'LOCKED'
  joinStatus: string
  attemptType: 'LIVE' | 'LATE' | 'RECOVERY'
  rewardRateBps: number
  completedCount: number
  totalCount: number
  canAdvance: boolean
}

export async function advanceStudentFlow() {
  const response = await http.post<ApiResponse<StudentFlow>>('/student/flow/advance')
  return response.data.data
}
