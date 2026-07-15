import { http } from './http'

export interface HealthData {
  status: string
  service: string
}

interface ApiResponse<T> {
  data: T
  meta: {
    requestId: string
  }
}

export async function getHealth(): Promise<HealthData> {
  const response = await http.get<ApiResponse<HealthData>>('/health')
  return response.data.data
}

