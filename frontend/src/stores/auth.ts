import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  enterStudent as enterStudentApi,
  getStudentMe,
  getTeacherMe,
  loginStudent as loginStudentApi,
  loginTeacher as loginTeacherApi,
  setupStudent as setupStudentApi,
  type StudentEntry,
  type StudentSession,
  type TeacherSession,
} from '../features/auth/api/auth'

const PENDING_STUDENT_ENTRY_KEY = 'bibim.pendingStudentEntry'

function readPendingStudentEntry(): StudentEntry | null {
  const stored = sessionStorage.getItem(PENDING_STUDENT_ENTRY_KEY)
  if (!stored) {
    return null
  }

  try {
    return JSON.parse(stored) as StudentEntry
  } catch {
    sessionStorage.removeItem(PENDING_STUDENT_ENTRY_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const teacher = ref<TeacherSession | null>(null)
  const student = ref<StudentSession | null>(null)
  const pendingStudentEntry = ref<StudentEntry | null>(readPendingStudentEntry())

  async function loginTeacher(loginId: string, password: string) {
    teacher.value = await loginTeacherApi(loginId, password)
    return teacher.value
  }

  async function loadTeacher() {
    teacher.value = await getTeacherMe()
    return teacher.value
  }

  async function enterStudent(participationCode: string) {
    const entry = await enterStudentApi(participationCode)
    pendingStudentEntry.value = entry
    sessionStorage.setItem(PENDING_STUDENT_ENTRY_KEY, JSON.stringify(entry))
    return entry
  }

  async function setupStudent(nickname: string, password: string) {
    if (!pendingStudentEntry.value) {
      throw new Error('참여 코드 정보가 없습니다.')
    }

    student.value = await setupStudentApi(
      pendingStudentEntry.value.participationCode,
      nickname,
      password,
    )
    clearPendingStudentEntry()
    return student.value
  }

  async function loginStudent(password: string) {
    if (!pendingStudentEntry.value) {
      throw new Error('참여 코드 정보가 없습니다.')
    }

    student.value = await loginStudentApi(pendingStudentEntry.value.participationCode, password)
    clearPendingStudentEntry()
    return student.value
  }

  async function loadStudent() {
    student.value = await getStudentMe()
    return student.value
  }

  function clearPendingStudentEntry() {
    pendingStudentEntry.value = null
    sessionStorage.removeItem(PENDING_STUDENT_ENTRY_KEY)
  }

  return {
    teacher,
    student,
    pendingStudentEntry,
    loginTeacher,
    loadTeacher,
    enterStudent,
    setupStudent,
    loginStudent,
    loadStudent,
  }
})
