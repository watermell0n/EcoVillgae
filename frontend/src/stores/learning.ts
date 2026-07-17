import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  changeTeacherLearningPage,
  getStudentLearning,
  getStudentLearningTopic,
  getStudentLearningTopics,
  getTeacherLearning,
  type GuidedLearning,
  type LearningTopic,
  type LearningTopicDetail,
} from '../features/learning/api/learning'

export const useLearningStore = defineStore('learning', () => {
  const guided = ref<GuidedLearning | null>(null)
  const topics = ref<LearningTopic[]>([])
  const selectedTopic = ref<LearningTopicDetail | null>(null)

  async function refreshTeacher(roomId: string) {
    guided.value = await getTeacherLearning(roomId)
    return guided.value
  }

  async function changeTeacherPage(roomId: string, pageId: string, expectedVersion: number) {
    guided.value = await changeTeacherLearningPage(roomId, pageId, expectedVersion)
    return guided.value
  }

  async function refreshStudent() {
    guided.value = await getStudentLearning()
    return guided.value
  }

  async function refreshTopics() {
    topics.value = await getStudentLearningTopics()
    return topics.value
  }

  async function openTopic(topicId: string) {
    selectedTopic.value = await getStudentLearningTopic(topicId)
    return selectedTopic.value
  }

  function clearGuided() {
    guided.value = null
  }

  function clearReview() {
    topics.value = []
    selectedTopic.value = null
  }

  return {
    guided,
    topics,
    selectedTopic,
    refreshTeacher,
    changeTeacherPage,
    refreshStudent,
    refreshTopics,
    openTopic,
    clearGuided,
    clearReview,
  }
})
