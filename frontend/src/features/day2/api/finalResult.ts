import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface PurchaseResult {
  purchaseId: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  purchasedAt: string
}

export interface VoteDecision {
  voteSessionId: string
  optionId: string
  title: string
  description: string
  requiredAmount: number
  voteCount: number
  submittedCount: number
}

export interface StudentFinalResult {
  roomId: string
  studentId: string
  participantNumber: number
  nickname: string | null
  grossIncome: number
  incomeTax: number
  netIncome: number
  depositPrincipal: number
  depositInterest: number
  investmentPrincipal: number
  investmentProfit: number
  financialTax: number
  day2QuizReward: number
  totalPurchaseAmount: number
  finalWalletBalance: number
  purchases: PurchaseResult[]
  voteResult: VoteDecision | null
  surveyUrl: string
}

export interface TeacherStudentResult {
  studentId: string
  participantNumber: number
  nickname: string | null
  grossIncome: number
  totalTax: number
  depositInterest: number
  investmentProfit: number
  quizReward: number
  purchaseAmount: number
  finalWalletBalance: number
}

export interface TeacherFinalSummary {
  roomId: string
  roomName: string
  studentCount: number
  totalGrossIncome: number
  totalIncomeTax: number
  totalDepositInterest: number
  totalInvestmentProfit: number
  totalFinancialTax: number
  totalQuizReward: number
  totalPurchaseAmount: number
  totalFinalWalletBalance: number
  classFundBalance: number
  classExpenseAmount: number
  voteResult: VoteDecision | null
  surveyUrl: string
  students: TeacherStudentResult[]
}

export interface ProductDistributionItem {
  productId: string
  productName: string
  studentId: string
  participantNumber: number
  nickname: string | null
  quantity: number
  paidAmount: number
}

export interface ProductDistribution {
  roomId: string
  totalQuantity: number
  items: ProductDistributionItem[]
}

export async function getStudentFinalResult() {
  const response = await http.get<ApiResponse<StudentFinalResult>>('/student/final-result')
  return response.data.data
}

export async function getTeacherFinalSummary(roomId: string) {
  const response = await http.get<ApiResponse<TeacherFinalSummary>>(
    `/teacher/rooms/${roomId}/results/summary`,
  )
  return response.data.data
}

export async function getTeacherProductDistribution(roomId: string) {
  const response = await http.get<ApiResponse<ProductDistribution>>(
    `/teacher/rooms/${roomId}/results/product-distribution`,
  )
  return response.data.data
}

