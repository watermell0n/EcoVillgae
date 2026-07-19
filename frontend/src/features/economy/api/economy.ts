import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface EconomyOverview {
  walletBalance: number
  depositBalance: number
  investmentBalance: number
  purchaseCount: number
  depositStatus: 'NONE' | 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
  investmentStatus: 'NONE' | 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
  totalAssets: number
}

export interface EconomyTransaction {
  transactionId: string
  transactionType: string
  status: string
  description: string
  walletAmount: number
  metadata: Record<string, unknown>
  createdAt: string
}

export interface DepositState {
  depositId: string | null
  status: 'NONE' | 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
  principal: number
  interestRateBps: number
  expectedInterest: number
  expectedMaturityAmount: number
  walletBalance: number
  updatedAt: string | null
  confirmedAt: string | null
  replayed: boolean
}

export interface DepositHistoryItem {
  depositId: string
  principal: number
  interestRateBps: number
  expectedInterest: number
  expectedMaturityAmount: number
  confirmedAt: string
}

export interface InvestmentAsset {
  assetId: string
  code: string
  name: string
  description: string
  companyInfo: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  minReturnBps: number
  maxReturnBps: number
  displayOrder: number
}

export interface InvestmentAllocation {
  assetId: string
  assetName: string
  riskLevel: string
  amount: number
}

export interface InvestmentState {
  orderId: string | null
  status: 'NONE' | 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
  totalAmount: number
  walletBalance: number
  scenarioLocked: boolean
  assets: InvestmentAsset[]
  allocations: InvestmentAllocation[]
  updatedAt: string | null
  confirmedAt: string | null
  replayed: boolean
}

export interface InvestmentHistoryItem {
  orderId: string
  totalAmount: number
  allocations: InvestmentAllocation[]
  confirmedAt: string
}

export async function getEconomyOverview() {
  const response = await http.get<ApiResponse<EconomyOverview>>('/student/economy/overview')
  return response.data.data
}

export async function getEconomyTransactions() {
  const response = await http.get<ApiResponse<{ transactions: EconomyTransaction[] }>>(
    '/student/economy/transactions',
  )
  return response.data.data.transactions
}

export async function getCurrentDeposit() {
  const response = await http.get<ApiResponse<DepositState>>('/student/deposits/current')
  return response.data.data
}

export async function getDepositHistory() {
  const response = await http.get<ApiResponse<{ deposits: DepositHistoryItem[] }>>(
    '/student/deposits/history',
  )
  return response.data.data.deposits
}

export async function saveDepositDraft(principal: number) {
  const response = await http.put<ApiResponse<DepositState>>('/student/deposits/draft', {
    principal,
  })
  return response.data.data
}

export async function cancelDepositDraft() {
  const response = await http.delete<ApiResponse<DepositState>>('/student/deposits/draft')
  return response.data.data
}

export async function confirmDeposit(idempotencyKey: string) {
  const response = await http.post<ApiResponse<DepositState>>(
    '/student/deposits/confirm',
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}

export async function getCurrentInvestment() {
  const response = await http.get<ApiResponse<InvestmentState>>('/student/investments/current')
  return response.data.data
}

export async function getInvestmentHistory() {
  const response = await http.get<ApiResponse<{ investments: InvestmentHistoryItem[] }>>(
    '/student/investments/history',
  )
  return response.data.data.investments
}

export async function saveInvestmentDraft(allocations: Array<{ assetId: string; amount: number }>) {
  const response = await http.put<ApiResponse<InvestmentState>>('/student/investments/draft', {
    allocations,
  })
  return response.data.data
}

export async function cancelInvestmentDraft() {
  const response = await http.delete<ApiResponse<InvestmentState>>('/student/investments/draft')
  return response.data.data
}

export async function confirmInvestment(idempotencyKey: string) {
  const response = await http.post<ApiResponse<InvestmentState>>(
    '/student/investments/confirm',
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}
