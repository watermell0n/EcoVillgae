import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface EconomyRules {
  roleIncome: number
  day1CorrectReward: number
  incomeTaxBps: number
  depositInterestBps: number
  financialTaxBps: number
  day2CorrectReward: number
  inflationBps: number
  depositMinAmount: number
  depositUnit: number
  investmentMinAmount: number
  investmentUnit: number
  initialFundAmount: number
}

export interface QuizOption { id: string; text: string }
export interface QuizQuestion {
  questionId: string
  orderNo: number
  prompt: string
  options: QuizOption[]
  correctAnswer: string
  explanation: string
}
export interface QuizSettings { dayNumber: number; status: string; questions: QuizQuestion[] }
export interface InvestmentSetting {
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
export interface ProductSetting {
  productId: string
  name: string
  description: string
  price: number
  imageUrl: string | null
  currentStock: number
  displayOrder: number
}
export interface ClassSettings {
  roomId: string
  roomName: string
  roomStatus: string
  editingLiveClass: boolean
  economy: EconomyRules
  quizzes: QuizSettings[]
  investments: InvestmentSetting[]
  products: ProductSetting[]
}
export interface ManagedStudent {
  studentId: string
  participantNumber: number
  participationCode: string
  nickname: string | null
  status: 'INVITED' | 'ACTIVE' | 'ABSENT' | 'DISABLED'
  online: boolean
  lastSeenAt: string | null
}

export async function getClassSettings(roomId: string) {
  const response = await http.get<ApiResponse<ClassSettings>>(`/teacher/rooms/${roomId}/management/settings`)
  return response.data.data
}

export async function saveClassSettings(roomId: string, settings: ClassSettings) {
  const response = await http.put<ApiResponse<ClassSettings>>(
    `/teacher/rooms/${roomId}/management/settings`,
    { roomName: settings.roomName, economy: settings.economy, quizzes: settings.quizzes, investments: settings.investments, products: settings.products },
  )
  return response.data.data
}

export async function getManagedStudents(roomId: string) {
  const response = await http.get<ApiResponse<ManagedStudent[]>>(`/teacher/rooms/${roomId}/management/students`)
  return response.data.data
}

export async function addManagedStudent(roomId: string, participantNumber: number, nickname: string) {
  const response = await http.post<ApiResponse<ManagedStudent>>(
    `/teacher/rooms/${roomId}/management/students`,
    { participantNumber, nickname: nickname || null },
  )
  return response.data.data
}

export async function updateManagedStudent(roomId: string, student: ManagedStudent) {
  const response = await http.patch<ApiResponse<ManagedStudent>>(
    `/teacher/rooms/${roomId}/management/students/${student.studentId}`,
    { participantNumber: student.participantNumber, nickname: student.nickname, status: student.status },
  )
  return response.data.data
}

export async function resetManagedStudentPassword(roomId: string, studentId: string) {
  const response = await http.post<ApiResponse<ManagedStudent>>(
    `/teacher/rooms/${roomId}/management/students/${studentId}/reset-password`,
  )
  return response.data.data
}
