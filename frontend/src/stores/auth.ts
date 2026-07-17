import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  enterStudent as enterStudentApi,
  getAdminMe,
  getStudentMe,
  getTeacherMe,
  loginStudent as loginStudentApi,
  loginAdmin as loginAdminApi,
  loginTeacher as loginTeacherApi,
  setupStudent as setupStudentApi,
  type StudentEntry,
  type AdminSession,
  type StudentSession,
  type TeacherSession,
} from '../features/auth/api/auth'
import { useRoomSessionStore } from './roomSession'

const PENDING_STUDENT_ENTRY_KEY = 'ecovillage.pendingStudentEntry'
const LEGACY_PENDING_STUDENT_ENTRY_KEY = 'bibim.pendingStudentEntry'

function readPendingStudentEntry(): StudentEntry | null {
  const stored = sessionStorage.getItem(PENDING_STUDENT_ENTRY_KEY)
    ?? sessionStorage.getItem(LEGACY_PENDING_STUDENT_ENTRY_KEY)
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
  const admin = ref<AdminSession | null>(null)
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

  async function loginAdmin(loginId: string, password: string) {
    admin.value = await loginAdminApi(loginId, password)
    return admin.value
  }

  async function loadAdmin() {
    admin.value = await getAdminMe()
    return admin.value
  }

  async function enterStudent(participationCode: string) {
    const entry = await enterStudentApi(participationCode)
    student.value = null
    useRoomSessionStore().resetStudentContext()
    pendingStudentEntry.value = entry
    sessionStorage.setItem(PENDING_STUDENT_ENTRY_KEY, JSON.stringify(entry))
    return entry
  }

  async function setupStudent(nickname: string, password: string) {
    if (!pendingStudentEntry.value) {
      throw new Error('참여 코드 정보가 없습니다.')
    }

    const nextStudent = await setupStudentApi(
      pendingStudentEntry.value.participationCode,
      nickname,
      password,
    )
    useRoomSessionStore().resetStudentContext()
    student.value = nextStudent
    clearPendingStudentEntry()
    return student.value
  }

  async function loginStudent(password: string) {
    if (!pendingStudentEntry.value) {
      throw new Error('참여 코드 정보가 없습니다.')
    }

    const nextStudent = await loginStudentApi(pendingStudentEntry.value.participationCode, password)
    useRoomSessionStore().resetStudentContext()
    student.value = nextStudent
    clearPendingStudentEntry()
    return student.value
  }

  async function loadStudent() {
    const nextStudent = await getStudentMe()
    if (student.value && student.value.studentId !== nextStudent.studentId) {
      useRoomSessionStore().resetStudentContext()
    }
    student.value = nextStudent
    return student.value
  }

  function clearPendingStudentEntry() {
    pendingStudentEntry.value = null
    sessionStorage.removeItem(PENDING_STUDENT_ENTRY_KEY)
    sessionStorage.removeItem(LEGACY_PENDING_STUDENT_ENTRY_KEY)
  }

  return {
    teacher,
    admin,
    student,
    pendingStudentEntry,
    loginTeacher,
    loadTeacher,
    loginAdmin,
    loadAdmin,
    enterStudent,
    setupStudent,
    loginStudent,
    loadStudent,
  }
})
