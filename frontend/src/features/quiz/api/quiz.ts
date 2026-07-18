import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface QuizOption {
  id: string
  text: string
}

export interface QuizQuestion {
  questionId: string
  orderNo: number
  questionType: string
  prompt: string
  options: QuizOption[]
  savedAnswer: string | null
}

export interface StudentQuiz {
  quizId: string
  title: string
  dayNumber: number
  questionCount: number
  submissionStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED'
  answeredCount: number
  submittedAt: string | null
  questions: QuizQuestion[]
}

export interface QuizResultQuestion {
  questionId: string
  orderNo: number
  prompt: string
  options: QuizOption[]
  selectedAnswer: string | null
  correctAnswer: string
  correct: boolean
  explanation: string | null
}

export interface StudentQuizResult {
  quizId: string
  title: string
  questionCount: number
  answeredCount: number
  correctCount: number
  questions: QuizResultQuestion[]
}

export interface SaveAnswerResponse {
  questionId: string
  answer: string
  answeredCount: number
  savedAt: string
}

export interface SubmitQuizResponse {
  quizId: string
  status: 'SUBMITTED'
  answeredCount: number
  questionCount: number
  submittedAt: string
}

export interface TeacherQuizStudent {
  studentId: string
  participantNumber: number
  nickname: string | null
  online: boolean
  submissionStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED'
  correctCount: number | null
  submittedAt: string | null
  questions: TeacherQuizStudentQuestion[]
}

export interface TeacherQuizStudentQuestion {
  questionId: string
  orderNo: number
  answered: boolean
  correct: boolean
}

export interface TeacherQuizQuestionSummary {
  questionId: string
  orderNo: number
  prompt: string
  options: QuizOption[]
  correctAnswer: string
  explanation: string | null
  answeredCount: number
  correctCount: number
}

export interface TeacherQuizSummary {
  quizId: string
  title: string
  status: 'READY' | 'OPEN' | 'CLOSED'
  questionCount: number
  expectedStudentCount: number
  eligibleStudentCount: number
  submittedCount: number
  inProgressCount: number
  notStartedCount: number
  notSubmittedCount: number
  students: TeacherQuizStudent[]
  questions: TeacherQuizQuestionSummary[]
}

export async function getCurrentQuiz() {
  const response = await http.get<ApiResponse<StudentQuiz>>('/student/quizzes/current')
  return response.data.data
}

export async function getDayOneQuizResult() {
  const response = await http.get<ApiResponse<StudentQuizResult>>(
    '/student/quizzes/day-1/result',
  )
  return response.data.data
}

export async function saveQuizAnswer(quizId: string, questionId: string, answer: string) {
  const response = await http.put<ApiResponse<SaveAnswerResponse>>(
    `/student/quizzes/${quizId}/answers/${questionId}`,
    { answer },
  )
  return response.data.data
}

export async function submitQuiz(quizId: string) {
  const response = await http.post<ApiResponse<SubmitQuizResponse>>(
    `/student/quizzes/${quizId}/submit`,
  )
  return response.data.data
}

export async function getTeacherQuizSummary(roomId: string) {
  const response = await http.get<ApiResponse<TeacherQuizSummary>>(
    `/teacher/rooms/${roomId}/quiz-summary`,
  )
  return response.data.data
}
