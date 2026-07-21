import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface Day1PurchaseSummary {
  purchaseId: string
  productName: string
  quantity: number
  totalPrice: number
}

export interface Day1InvestmentSummary {
  assetId: string
  assetName: string
  riskLevel: string
  amount: number
}

export interface StudentDay1Summary {
  studentId: string
  participantNumber: number
  nickname: string | null
  roomStage: string
  walletBalance: number
  depositPrincipal: number
  expectedDepositInterest: number
  investmentPrincipal: number
  purchaseTotal: number
  totalAssets: number
  investments: Day1InvestmentSummary[]
  purchases: Day1PurchaseSummary[]
}

export interface TeacherStudentDay1Summary {
  studentId: string
  participantNumber: number
  nickname: string | null
  online: boolean
  walletBalance: number
  depositPrincipal: number
  investmentPrincipal: number
  purchaseTotal: number
  depositConfirmed: boolean
  investmentConfirmed: boolean
  purchased: boolean
  inactive: boolean
  depositStatus: 'NONE' | 'DRAFT' | 'CONFIRMED'
  depositDraftPrincipal: number
  investmentStatus: 'NONE' | 'DRAFT' | 'CONFIRMED'
  investmentDraftTotal: number
  investments: Day1InvestmentSummary[]
  purchases: Day1PurchaseSummary[]
}

export interface TeacherDay1Summary {
  roomId: string
  roomStage: string
  studentCount: number
  totalWalletBalance: number
  totalDepositPrincipal: number
  totalInvestmentPrincipal: number
  totalPurchaseAmount: number
  depositConfirmedCount: number
  investmentConfirmedCount: number
  purchaseParticipantCount: number
  inactiveStudentCount: number
  students: TeacherStudentDay1Summary[]
}

export interface Day1ClosePreview {
  roomId: string
  canClose: boolean
  incomeSettlementCompleted: boolean
  depositDraftCount: number
  investmentDraftCount: number
  pendingTransactionExists: boolean
  warnings: string[]
}

export async function getStudentDay1Summary() {
  const response = await http.get<ApiResponse<StudentDay1Summary>>('/student/day-1/summary')
  return response.data.data
}

export async function getTeacherDay1Summary(roomId: string) {
  const response = await http.get<ApiResponse<TeacherDay1Summary>>(
    `/teacher/rooms/${roomId}/day-1/summary`,
  )
  return response.data.data
}

export async function getDay1ClosePreview(roomId: string) {
  const response = await http.get<ApiResponse<Day1ClosePreview>>(
    `/teacher/rooms/${roomId}/day-1/close-preview`,
  )
  return response.data.data
}
