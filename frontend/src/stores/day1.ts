import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  getDay1ClosePreview,
  getStudentDay1Summary,
  getTeacherDay1Summary,
  type Day1ClosePreview,
  type StudentDay1Summary,
  type TeacherDay1Summary,
} from '../features/day1/api/day1'

export const useDay1Store = defineStore('day1', () => {
  const studentSummary = ref<StudentDay1Summary | null>(null)
  const teacherSummary = ref<TeacherDay1Summary | null>(null)
  const closePreview = ref<Day1ClosePreview | null>(null)
  let teacherSummaryTimer: ReturnType<typeof setInterval> | undefined
  let teacherSummaryRequestInFlight = false

  async function refreshStudentSummary() {
    studentSummary.value = await getStudentDay1Summary()
    return studentSummary.value
  }

  async function refreshTeacherSummary(roomId: string) {
    if (teacherSummaryRequestInFlight) return teacherSummary.value
    teacherSummaryRequestInFlight = true
    try {
      teacherSummary.value = await getTeacherDay1Summary(roomId)
      return teacherSummary.value
    } finally {
      teacherSummaryRequestInFlight = false
    }
  }

  function startTeacherSummaryPolling(roomId: string) {
    stopTeacherSummaryPolling()
    teacherSummaryTimer = setInterval(() => void refreshTeacherSummary(roomId), 2_000)
  }

  function stopTeacherSummaryPolling() {
    if (teacherSummaryTimer) clearInterval(teacherSummaryTimer)
    teacherSummaryTimer = undefined
    teacherSummaryRequestInFlight = false
  }

  async function refreshClosePreview(roomId: string) {
    closePreview.value = await getDay1ClosePreview(roomId)
    return closePreview.value
  }

  function clear() {
    stopTeacherSummaryPolling()
    studentSummary.value = null
    teacherSummary.value = null
    closePreview.value = null
  }

  return {
    studentSummary,
    teacherSummary,
    closePreview,
    refreshStudentSummary,
    refreshTeacherSummary,
    startTeacherSummaryPolling,
    stopTeacherSummaryPolling,
    refreshClosePreview,
    clear,
  }
})
