import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface StudentIncome {
  studentId: string
  participantNumber: number
  nickname: string | null
  quizSubmitted: boolean
  correctCount: number
  roleIncome: number
  participationReward: number
  correctReward: number
  grossIncome: number
  incomeTax: number
  netIncome: number
}

export interface Day1IncomePreview {
  roomId: string
  settlementStatus: 'NOT_STARTED' | 'COMPLETED'
  incomeTaxBps: number
  targetCount: number
  totalGrossIncome: number
  totalIncomeTax: number
  totalNetIncome: number
  classFundBalance: number
  completedAt: string | null
  students: StudentIncome[]
}

export interface Day1IncomeSettlementResponse {
  settlement: Day1IncomePreview
  replayed: boolean
}

export interface Day1Payslip {
  settlementRunId: string
  studentId: string
  participantNumber: number
  nickname: string
  roleName: string | null
  quizSubmitted: boolean
  correctCount: number
  roleIncome: number
  participationReward: number
  correctReward: number
  grossIncome: number
  incomeTax: number
  netIncome: number
  walletBalance: number
  issuedAt: string
}

export async function getDay1IncomePreview(roomId: string) {
  const response = await http.get<ApiResponse<Day1IncomePreview>>(
    `/teacher/rooms/${roomId}/settlements/day-1-income/preview`,
  )
  return response.data.data
}

export async function settleDay1Income(roomId: string, idempotencyKey: string) {
  const response = await http.post<ApiResponse<Day1IncomeSettlementResponse>>(
    `/teacher/rooms/${roomId}/settlements/day-1-income`,
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}

export async function getDay1Payslip() {
  const response = await http.get<ApiResponse<Day1Payslip>>('/student/payslips/day-1')
  return response.data.data
}
