import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface Day2AllocationResult {
  assetId: string
  assetName: string
  riskLevel: string
  principal: number
  returnBps: number
  profit: number
  finalValue: number
}

export interface StudentDay2FinanceResult {
  studentId: string
  participantNumber: number
  nickname: string | null
  settlementStatus: 'NOT_STARTED' | 'COMPLETED'
  walletBefore: number
  depositPrincipal: number
  depositInterest: number
  investmentPrincipal: number
  investmentProfit: number
  investmentValue: number
  taxableProfit: number
  financialTax: number
  walletAfter: number
  allocations: Day2AllocationResult[]
  completedAt: string | null
}

export interface TeacherDay2FinancePreview {
  roomId: string
  settlementStatus: 'NOT_STARTED' | 'COMPLETED'
  financialTaxBps: number
  targetCount: number
  totalDepositPrincipal: number
  totalDepositInterest: number
  totalInvestmentPrincipal: number
  totalInvestmentProfit: number
  totalFinancialTax: number
  totalWalletAfter: number
  classFundBalance: number
  completedAt: string | null
  students: StudentDay2FinanceResult[]
}

export interface Day2FinanceSettlementResponse {
  settlement: TeacherDay2FinancePreview
  replayed: boolean
}

export async function getStudentDay2FinanceResult() {
  const response = await http.get<ApiResponse<StudentDay2FinanceResult>>(
    '/student/day-2/finance-result',
  )
  return response.data.data
}

export async function getTeacherDay2FinancePreview(roomId: string) {
  const response = await http.get<ApiResponse<TeacherDay2FinancePreview>>(
    `/teacher/rooms/${roomId}/settlements/day-2-finance/preview`,
  )
  return response.data.data
}

export async function settleTeacherDay2Finance(roomId: string, idempotencyKey: string) {
  const response = await http.post<ApiResponse<Day2FinanceSettlementResponse>>(
    `/teacher/rooms/${roomId}/settlements/day-2-finance`,
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}
