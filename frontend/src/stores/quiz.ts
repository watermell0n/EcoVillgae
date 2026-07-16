import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  getTeacherQuizSummary,
  type TeacherQuizSummary,
} from '../features/quiz/api/quiz'

const POLLING_INTERVAL_MS = 2_000

export const useQuizStore = defineStore('quiz', () => {
  const teacherSummary = ref<TeacherQuizSummary | null>(null)
  const pollError = shallowRef<unknown>(null)
  let pollingTimer: ReturnType<typeof setInterval> | undefined
  let requestInFlight = false

  async function refreshTeacherSummary(roomId: string) {
    const summary = await getTeacherQuizSummary(roomId)
    teacherSummary.value = summary
    pollError.value = null
    return summary
  }

  async function pollTeacherSummary(roomId: string) {
    if (requestInFlight) return
    requestInFlight = true
    try {
      await refreshTeacherSummary(roomId)
    } catch (error) {
      pollError.value = error
    } finally {
      requestInFlight = false
    }
  }

  function startTeacherSummaryPolling(roomId: string) {
    stopTeacherSummaryPolling()
    pollingTimer = setInterval(() => {
      void pollTeacherSummary(roomId)
    }, POLLING_INTERVAL_MS)
  }

  function stopTeacherSummaryPolling() {
    if (pollingTimer !== undefined) {
      clearInterval(pollingTimer)
      pollingTimer = undefined
    }
    requestInFlight = false
  }

  function clearTeacherSummary() {
    teacherSummary.value = null
    pollError.value = null
  }

  return {
    teacherSummary,
    pollError,
    refreshTeacherSummary,
    startTeacherSummaryPolling,
    stopTeacherSummaryPolling,
    clearTeacherSummary,
  }
})
