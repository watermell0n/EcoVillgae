import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface FundExpense {
  expenseId: string
  costOptionId: string
  name: string
  amount: number
  appliedAt: string
}

export interface FundOverview {
  roomId: string
  balance: number
  totalIncomeTax: number
  totalFinancialTax: number
  totalExpenses: number
  appliedExpense: FundExpense | null
}

export interface FundCostOption {
  optionId: string
  name: string
  amount: number
  enabled: boolean
  affordable: boolean
  applied: boolean
}

export interface VoteOption {
  optionId: string
  title: string
  description: string
  requiredAmount: number
  affordable: boolean
  voteCount: number | null
  tiedForFirst: boolean
  decided: boolean
}

export interface VoteParticipation {
  studentId: string
  participantNumber: number
  nickname: string | null
}

export interface VoteSession {
  voteSessionId: string
  roomId: string
  question: string
  fundBalanceSnapshot: number
  status: 'READY' | 'OPEN' | 'CLOSED' | 'DECIDED'
  eligibleStudentCount: number
  submittedCount: number
  eligible: boolean
  hasVoted: boolean
  selectedOptionId: string | null
  decidedOptionId: string | null
  openedAt: string | null
  closedAt: string | null
  pendingStudents: VoteParticipation[]
  options: VoteOption[]
}

export interface VoteOptionRequest {
  title: string
  description: string
  requiredAmount: number
}

export interface CreateVoteRequest {
  question: string
  options: VoteOptionRequest[]
}

export async function getTeacherFund(roomId: string) {
  const response = await http.get<ApiResponse<FundOverview>>(`/teacher/rooms/${roomId}/class-fund`)
  return response.data.data
}

export async function getStudentFund() {
  const response = await http.get<ApiResponse<FundOverview>>('/student/class-fund')
  return response.data.data
}

export async function addTeacherFund(roomId: string, amount: number, reason: string, idempotencyKey: string) {
  const response = await http.post<ApiResponse<FundOverview>>(
    `/teacher/rooms/${roomId}/class-fund/additions`,
    { amount, reason },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}

export async function getTeacherFundCostOptions(roomId: string) {
  const response = await http.get<ApiResponse<FundCostOption[]>>(
    `/teacher/rooms/${roomId}/fund-cost-options`,
  )
  return response.data.data
}

export async function applyTeacherFundExpense(
  roomId: string,
  costOptionId: string,
  idempotencyKey: string,
) {
  const response = await http.post<ApiResponse<{ fund: FundOverview; replayed: boolean }>>(
    `/teacher/rooms/${roomId}/fund-expenses`,
    { costOptionId },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}

export async function createTeacherVote(roomId: string, request: CreateVoteRequest) {
  const response = await http.post<ApiResponse<VoteSession>>(
    `/teacher/rooms/${roomId}/vote-sessions`,
    request,
  )
  return response.data.data
}

export async function getTeacherCurrentVote(roomId: string) {
  const response = await http.get<ApiResponse<VoteSession>>(
    `/teacher/rooms/${roomId}/vote-sessions/current`,
  )
  return response.data.data
}

export async function openTeacherVote(roomId: string, voteId: string) {
  const response = await http.post<ApiResponse<VoteSession>>(
    `/teacher/rooms/${roomId}/vote-sessions/${voteId}/open`,
  )
  return response.data.data
}

export async function closeTeacherVote(roomId: string, voteId: string, force = false) {
  const response = await http.post<ApiResponse<VoteSession>>(
    `/teacher/rooms/${roomId}/vote-sessions/${voteId}/close`,
    undefined,
    { params: { force } },
  )
  return response.data.data
}

export async function decideTeacherVote(roomId: string, voteId: string, optionId: string) {
  const response = await http.post<ApiResponse<VoteSession>>(
    `/teacher/rooms/${roomId}/vote-sessions/${voteId}/decide`,
    { optionId },
  )
  return response.data.data
}

export async function getStudentCurrentVote() {
  const response = await http.get<ApiResponse<VoteSession>>('/student/votes/current')
  return response.data.data
}

export async function castStudentVote(voteId: string, optionId: string) {
  const response = await http.post<ApiResponse<VoteSession>>(`/student/votes/${voteId}`, { optionId })
  return response.data.data
}
