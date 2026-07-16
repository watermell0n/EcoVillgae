import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  getStudentDay2FinanceResult,
  getTeacherDay2FinancePreview,
  settleTeacherDay2Finance,
  type StudentDay2FinanceResult,
  type TeacherDay2FinancePreview,
} from '../features/day2/api/finance'

export const useDay2FinanceStore = defineStore('day2-finance', () => {
  const studentResult = ref<StudentDay2FinanceResult | null>(null)
  const teacherPreview = ref<TeacherDay2FinancePreview | null>(null)
  const loading = ref(false)
  const settling = ref(false)
  let settlementKey: string | undefined

  async function refreshStudent() {
    loading.value = true
    try {
      studentResult.value = await getStudentDay2FinanceResult()
      return studentResult.value
    } finally {
      loading.value = false
    }
  }

  async function refreshTeacher(roomId: string) {
    teacherPreview.value = await getTeacherDay2FinancePreview(roomId)
    return teacherPreview.value
  }

  async function settle(roomId: string) {
    settling.value = true
    settlementKey ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    try {
      const result = await settleTeacherDay2Finance(roomId, settlementKey)
      teacherPreview.value = result.settlement
      settlementKey = undefined
      return result
    } finally {
      settling.value = false
    }
  }

  function clear() {
    studentResult.value = null
    teacherPreview.value = null
    settlementKey = undefined
  }

  return {
    studentResult,
    teacherPreview,
    loading,
    settling,
    refreshStudent,
    refreshTeacher,
    settle,
    clear,
  }
})
