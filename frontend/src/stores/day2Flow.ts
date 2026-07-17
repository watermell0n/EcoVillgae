import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  getTeacherDay2QuizRewardPreview,
  settleTeacherDay2QuizReward,
  type Day2QuizRewardPreview,
} from '../features/day2/api/quizReward'
import {
  applyTeacherInflation,
  getTeacherInflationPreview,
  type InflationPreview,
} from '../features/day2/api/inflation'

export const useDay2FlowStore = defineStore('day2Flow', () => {
  const quizRewardPreview = ref<Day2QuizRewardPreview | null>(null)
  const inflationPreview = ref<InflationPreview | null>(null)
  const settlingQuizReward = ref(false)
  const applyingInflation = ref(false)
  const error = shallowRef<unknown>(null)
  let quizRewardKey: string | undefined

  async function refreshQuizReward(roomId: string) {
    quizRewardPreview.value = await getTeacherDay2QuizRewardPreview(roomId)
    error.value = null
    return quizRewardPreview.value
  }

  async function settleQuizReward(roomId: string) {
    if (settlingQuizReward.value) return undefined
    quizRewardKey ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    settlingQuizReward.value = true
    try {
      const result = await settleTeacherDay2QuizReward(roomId, quizRewardKey)
      quizRewardPreview.value = result.settlement
      quizRewardKey = undefined
      error.value = null
      return result
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      settlingQuizReward.value = false
    }
  }

  async function refreshInflation(roomId: string) {
    inflationPreview.value = await getTeacherInflationPreview(roomId)
    error.value = null
    return inflationPreview.value
  }

  async function applyInflation(roomId: string) {
    if (applyingInflation.value) return undefined
    applyingInflation.value = true
    try {
      const result = await applyTeacherInflation(roomId)
      inflationPreview.value = result.inflation
      error.value = null
      return result
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      applyingInflation.value = false
    }
  }

  function clear() {
    quizRewardPreview.value = null
    inflationPreview.value = null
    quizRewardKey = undefined
    error.value = null
  }

  return {
    quizRewardPreview,
    inflationPreview,
    settlingQuizReward,
    applyingInflation,
    error,
    refreshQuizReward,
    settleQuizReward,
    refreshInflation,
    applyInflation,
    clear,
  }
})
