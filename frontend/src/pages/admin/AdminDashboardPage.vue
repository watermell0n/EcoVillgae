<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  getAdminRooms,
  getAdminRoomStudents,
  getAdminStudentActivities,
  terminateAdminStudentSession,
  type AdminActivity,
  type AdminRoomMonitoring,
  type AdminRoomStudents,
  type AdminStudentMonitoring,
  type ConnectionStatus,
} from '../../features/admin/api/admin'
import { getRoomStage } from '../../features/room/model/stages'
import { getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const rooms = ref<AdminRoomMonitoring[]>([])
const roomStudents = ref<AdminRoomStudents | null>(null)
const activities = ref<AdminActivity[]>([])
const selectedRoomId = ref('')
const selectedStudentId = ref('')
const loading = ref(true)
const refreshing = ref(false)
const terminating = ref(false)
const terminationReason = ref('')
const errorMessage = ref('')
const noticeMessage = ref('')
let pollingTimer: ReturnType<typeof setInterval> | undefined

const admin = computed(() => authStore.admin)
const selectedRoom = computed(
  () => rooms.value.find((room) => room.roomId === selectedRoomId.value) ?? null,
)
const selectedStudent = computed(
  () =>
    roomStudents.value?.students.find((student) => student.studentId === selectedStudentId.value) ??
    null,
)
const problemStudentCount = computed(
  () =>
    roomStudents.value?.students.filter(
      (student) =>
        student.connectionStatus === 'OFFLINE' ||
        student.connectionStatus === 'UNSTABLE' ||
        student.syncStatus === 'DELAYED',
    ).length ?? 0,
)

const connectionLabels: Record<ConnectionStatus, string> = {
  ONLINE: '접속 중',
  UNSTABLE: '연결 불안정',
  AWAY: '자리 비움',
  OFFLINE: '연결 끊김',
  NEVER_CONNECTED: '미접속',
}

const eventLabels: Record<string, string> = {
  STUDENT_SETUP_COMPLETED: '최초 계정 설정 완료',
  STUDENT_LOGIN: '로그인',
  STUDENT_CONNECTED: '상태 보고 시작',
  STUDENT_RECONNECTED: '재접속',
  PAGE_ENTERED: '페이지 이동',
  QUIZ_ANSWER_SAVED: '퀴즈 답안 저장',
  QUIZ_SUBMITTED: '퀴즈 제출',
  DAY1_INCOME_SETTLED: '1일차 소득 정산',
  PAYSLIP_VIEWED: '월급명세서 확인',
  STUDENT_SESSION_TERMINATED: '관리자가 세션 종료',
}

onMounted(async () => {
  try {
    if (!authStore.admin) {
      await authStore.loadAdmin()
    }
    await refreshDashboard()
    pollingTimer = setInterval(() => {
      void refreshDashboard(true)
    }, 5_000)
  } catch (error) {
    await handleError(error, '관리자 대시보드를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (pollingTimer !== undefined) clearInterval(pollingTimer)
})

watch(selectedRoomId, async (roomId, previousRoomId) => {
  if (!roomId || roomId === previousRoomId) return
  selectedStudentId.value = ''
  activities.value = []
  await refreshRoomStudents()
})

watch(selectedStudentId, async (studentId, previousStudentId) => {
  if (!studentId || studentId === previousStudentId) {
    if (!studentId) activities.value = []
    return
  }
  await refreshActivities()
})

async function refreshDashboard(silent = false) {
  if (refreshing.value) return
  refreshing.value = true
  if (!silent) errorMessage.value = ''
  try {
    rooms.value = await getAdminRooms()
    if (!selectedRoomId.value || !rooms.value.some((room) => room.roomId === selectedRoomId.value)) {
      selectedRoomId.value = rooms.value[0]?.roomId ?? ''
    }
    if (selectedRoomId.value) {
      await refreshRoomStudents()
      if (selectedStudentId.value) await refreshActivities()
    }
  } catch (error) {
    await handleError(error, '운영 현황을 새로고침하지 못했습니다.', silent)
  } finally {
    refreshing.value = false
  }
}

async function refreshRoomStudents() {
  if (!selectedRoomId.value) {
    roomStudents.value = null
    return
  }
  roomStudents.value = await getAdminRoomStudents(selectedRoomId.value)
  if (
    selectedStudentId.value &&
    !roomStudents.value.students.some((student) => student.studentId === selectedStudentId.value)
  ) {
    selectedStudentId.value = ''
    activities.value = []
  }
}

async function refreshActivities() {
  if (!selectedRoomId.value || !selectedStudentId.value) return
  activities.value = await getAdminStudentActivities(
    selectedRoomId.value,
    selectedStudentId.value,
  )
}

async function terminateSession() {
  if (!selectedStudent.value || !selectedRoomId.value || terminating.value) return
  const reason = terminationReason.value.trim()
  if (!reason) {
    errorMessage.value = '세션을 종료하는 이유를 입력해 주세요.'
    return
  }
  if (!window.confirm(`${selectedStudent.value.nickname ?? `${selectedStudent.value.participantNumber}번`} 학생의 세션을 종료할까요?`)) {
    return
  }
  terminating.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await terminateAdminStudentSession(
      selectedRoomId.value,
      selectedStudent.value.studentId,
      reason,
    )
    noticeMessage.value = `학생 세션을 종료했습니다. 활성 세션 ${result.revokedSessionCount}개가 해제되었습니다.`
    terminationReason.value = ''
    await refreshRoomStudents()
    await refreshActivities()
  } catch (error) {
    await handleError(error, '학생 세션을 종료하지 못했습니다.')
  } finally {
    terminating.value = false
  }
}

async function handleError(error: unknown, fallback: string, silent = false) {
  if (isUnauthorized(error)) {
    await router.replace({ name: 'admin-login' })
    return
  }
  if (!silent) errorMessage.value = getApiErrorMessage(error, fallback)
}

function connectionTone(status: ConnectionStatus) {
  if (status === 'ONLINE') return 'success'
  if (status === 'UNSTABLE' || status === 'AWAY') return 'warning'
  if (status === 'OFFLINE') return 'danger'
  return 'neutral'
}

function syncLabel(student: AdminStudentMonitoring) {
  if (student.syncStatus === 'SYNCED') return '정상'
  if (student.syncStatus === 'DELAYED') {
    return `${student.roomSyncVersion - student.clientSyncVersion}단계 지연`
  }
  return '확인 전'
}

function pageLabel(page: string | null) {
  if (!page) return '아직 보고되지 않음'
  if (page.startsWith('QUIZ_QUESTION_')) return `퀴즈 ${page.replace('QUIZ_QUESTION_', '')}번 문제`
  if (page === 'QUIZ_SUBMITTED') return '퀴즈 제출 완료'
  if (page === 'SESSION_TERMINATED') return '세션 종료됨'
  return getRoomStage(page)?.label ?? page
}

function formatTime(value: string | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <main class="admin-dashboard-page">
    <header class="admin-header">
      <div>
        <RouterLink class="brand-link" to="/">비빔</RouterLink>
        <p v-if="admin">{{ admin.displayName }} 관리자 콘솔</p>
      </div>
      <div class="admin-header__actions">
        <StatusBadge label="5초 간격 모니터링" tone="success" />
        <button class="button-secondary" type="button" :disabled="refreshing" @click="refreshDashboard()">
          {{ refreshing ? '새로고침 중…' : '지금 새로고침' }}
        </button>
      </div>
    </header>

    <p v-if="loading" class="state-message">관리자 운영 정보를 불러오고 있습니다.</p>

    <template v-else>
      <section class="admin-title-row">
        <div>
          <p class="eyebrow">실시간 운영 모니터링</p>
          <h1 class="page-title">학생 연결과 활동 현황</h1>
        </div>
        <label class="admin-room-selector">
          <span>확인할 수업</span>
          <select v-model="selectedRoomId">
            <option v-for="room in rooms" :key="room.roomId" :value="room.roomId">
              {{ room.roomName }} · {{ room.currentDay }}일차
            </option>
          </select>
        </label>
      </section>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

      <template v-if="selectedRoom">
        <section class="admin-summary-grid" aria-label="방 운영 요약">
          <article>
            <span>현재 수업 단계</span>
            <strong>{{ getRoomStage(selectedRoom.currentStage)?.label ?? selectedRoom.currentStage }}</strong>
            <small>sync {{ selectedRoom.syncVersion }}</small>
          </article>
          <article>
            <span>정상 접속</span>
            <strong>{{ selectedRoom.onlineCount }}명</strong>
            <small>/ {{ selectedRoom.expectedStudentCount }}명</small>
          </article>
          <article :class="{ 'has-warning': problemStudentCount > 0 }">
            <span>확인 필요</span>
            <strong>{{ problemStudentCount }}명</strong>
            <small>연결 또는 동기화 문제</small>
          </article>
          <article>
            <span>미접속</span>
            <strong>{{ selectedRoom.neverConnectedCount }}명</strong>
            <small>아직 상태 보고 없음</small>
          </article>
        </section>

        <section class="admin-monitoring-layout">
          <div class="admin-student-panel">
            <div class="panel-heading">
              <p class="eyebrow">학생별 실시간 상태</p>
              <h2>{{ selectedRoom.roomName }}</h2>
            </div>

            <div class="admin-student-table" role="table" aria-label="학생별 접속과 페이지 현황">
              <div class="admin-student-table__head" role="row">
                <span>학생</span>
                <span>연결</span>
                <span>현재 페이지</span>
                <span>동기화</span>
                <span>최근 통신</span>
                <span>잔액</span>
              </div>
              <button
                v-for="student in roomStudents?.students"
                :key="student.studentId"
                type="button"
                class="admin-student-row"
                :class="{ 'is-selected': student.studentId === selectedStudentId }"
                role="row"
                @click="selectedStudentId = student.studentId"
              >
                <strong>{{ student.participantNumber }}번 {{ student.nickname ?? '미설정' }}</strong>
                <StatusBadge
                  :label="connectionLabels[student.connectionStatus]"
                  :tone="connectionTone(student.connectionStatus)"
                />
                <span>{{ pageLabel(student.currentPage) }}</span>
                <span :class="{ 'admin-sync-warning': student.syncStatus === 'DELAYED' }">
                  {{ syncLabel(student) }}
                </span>
                <span>{{ formatTime(student.lastSeenAt) }}</span>
                <span>{{ formatBibim(student.walletBalance) }}</span>
              </button>
            </div>
            <p v-if="!roomStudents?.students.length" class="state-message">이 방에는 학생이 없습니다.</p>
          </div>

          <aside class="admin-activity-panel">
            <template v-if="selectedStudent">
              <div class="panel-heading">
                <p class="eyebrow">학생 상세</p>
                <h2>{{ selectedStudent.participantNumber }}번 {{ selectedStudent.nickname ?? '미설정' }}</h2>
              </div>

              <dl class="admin-student-detail">
                <div><dt>현재 페이지</dt><dd>{{ pageLabel(selectedStudent.currentPage) }}</dd></div>
                <div><dt>현재 모듈</dt><dd>{{ selectedStudent.currentModule ?? '-' }}</dd></div>
                <div><dt>재접속 시각</dt><dd>{{ formatTime(selectedStudent.reconnectedAt) }}</dd></div>
                <div><dt>동기화</dt><dd>{{ syncLabel(selectedStudent) }}</dd></div>
              </dl>

              <section class="admin-recovery-box" aria-labelledby="admin-recovery-title">
                <h3 id="admin-recovery-title">문제 세션 종료</h3>
                <p>자동 재연결이 계속 실패할 때만 사용합니다. 학생은 참여 코드와 비밀번호로 다시 로그인해야 합니다.</p>
                <input
                  v-model.trim="terminationReason"
                  maxlength="200"
                  placeholder="예: 중복 로그인으로 화면 동기화 실패"
                />
                <button type="button" :disabled="terminating" @click="terminateSession">
                  {{ terminating ? '종료 중…' : '학생 세션 종료' }}
                </button>
              </section>

              <section class="admin-activity-list" aria-labelledby="admin-activity-title">
                <div class="panel-heading">
                  <p class="eyebrow">최근 100건</p>
                  <h3 id="admin-activity-title">활동 타임라인</h3>
                </div>
                <ol>
                  <li v-for="activity in activities" :key="activity.eventId">
                    <time>{{ formatTime(activity.occurredAt) }}</time>
                    <div>
                      <strong>{{ eventLabels[activity.eventType] ?? activity.eventType }}</strong>
                      <span v-if="activity.page">{{ pageLabel(activity.page) }}</span>
                    </div>
                  </li>
                </ol>
                <p v-if="!activities.length" class="state-message">아직 기록된 활동이 없습니다.</p>
              </section>
            </template>
            <p v-else class="state-message">학생을 선택하면 현재 상태와 활동 기록을 확인할 수 있습니다.</p>
          </aside>
        </section>
      </template>

      <p v-else class="state-message">모니터링할 수업 방이 없습니다.</p>
    </template>
  </main>
</template>
