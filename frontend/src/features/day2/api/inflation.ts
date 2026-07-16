import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface InflationProductPrice {
  productId: string
  name: string
  originalPrice: number
  priceAfterInflation: number
  increaseAmount: number
  currentStock: number
}

export interface InflationPreview {
  roomId: string
  inflationBps: number
  applied: boolean
  appliedAt: string | null
  products: InflationProductPrice[]
}

export interface InflationApplyResponse {
  inflation: InflationPreview
  replayed: boolean
}

export async function getTeacherInflationPreview(roomId: string) {
  const response = await http.get<ApiResponse<InflationPreview>>(
    `/teacher/rooms/${roomId}/inflation/preview`,
  )
  return response.data.data
}

export async function applyTeacherInflation(roomId: string) {
  const response = await http.post<ApiResponse<InflationApplyResponse>>(
    `/teacher/rooms/${roomId}/inflation`,
  )
  return response.data.data
}
