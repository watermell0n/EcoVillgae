import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface LearningPage {
  pageId: string
  topicCode: string
  topicTitle: string
  pageOrder: number
  title: string
  content: string
  keyPoints: string[]
}

export interface GuidedLearning {
  stage: string
  mode: string
  syncVersion: number
  currentIndex: number
  pages: LearningPage[]
  currentPage: LearningPage
}

export interface LearningTopic {
  topicId: string
  code: string
  title: string
  description: string
  pageCount: number
}

export interface LearningTopicDetail extends LearningTopic {
  pages: LearningPage[]
}

export async function getTeacherLearning(roomId: string) {
  const response = await http.get<ApiResponse<GuidedLearning>>(
    `/teacher/rooms/${roomId}/learning`,
  )
  return response.data.data
}

export async function changeTeacherLearningPage(
  roomId: string,
  pageId: string,
  expectedVersion: number,
) {
  const response = await http.put<ApiResponse<GuidedLearning>>(
    `/teacher/rooms/${roomId}/learning/page`,
    { pageId, expectedVersion },
  )
  return response.data.data
}

export async function getStudentLearning() {
  const response = await http.get<ApiResponse<GuidedLearning>>('/student/learning/current')
  return response.data.data
}

export async function getStudentLearningTopics() {
  const response = await http.get<ApiResponse<LearningTopic[]>>('/student/learning/topics')
  return response.data.data
}

export async function getStudentLearningTopic(topicId: string) {
  const response = await http.get<ApiResponse<LearningTopicDetail>>(
    `/student/learning/topics/${topicId}`,
  )
  return response.data.data
}
