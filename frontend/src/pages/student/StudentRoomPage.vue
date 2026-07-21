<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import { getRoomStage } from '../../features/room/model/stages'
import { advanceStudentFlow } from '../../features/flow/api/flow'
import { sendPresenceHeartbeat } from '../../features/monitoring/api/presence'
import { getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import { useAuthStore } from '../../stores/auth'
import { useDay1Store } from '../../stores/day1'
import { useRoomSessionStore } from '../../stores/roomSession'
import StudentSessionClosedView from './StudentSessionClosedView.vue'
import { getStudentStageComponent, isPlaceholderStage } from './stages/stageComponents'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const day1Store = useDay1Store()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const errorMessage = ref('')
const flowError = ref('')
const advancingFlow = ref(false)
const quizActivityCompleted = ref(false)
const currentPage = ref('CONNECTING')
const closingCountdown = ref<number | null>(null)
const closingNoticeKind = ref<'DAY1' | 'DAY2' | null>(null)
const sessionClosed = ref(false)
const closedDay = ref<1 | 2 | null>(null)
const catchUpQuizNoticeVisible = ref(false)
const awaitingJoinNoticeVisible = ref(false)
const catchUpNextVisible = ref(true)
const allowRouteExit = ref(false)
const student = computed(() => authStore.student)
const state = computed(() => roomSessionStore.studentState)
const displayStage = computed(() => state.value?.effectiveStage ?? state.value?.currentStage)
const stageDefinition = computed(() => getRoomStage(displayStage.value))
const stageComponent = computed(() => getStudentStageComponent(displayStage.value))
const stageComponentProps = computed(() => {
  if (isPlaceholderStage(displayStage.value)) {
    return { stageLabel: stageDefinition.value?.label ?? displayStage.value ?? '대기 중' }
  }
  if (displayStage.value === 'DAY1_FREE_ACTIVITY_GUIDED') {
    return { activeView: state.value?.guidedActivityView ?? 'HOME' }
  }
  return {}
})
const isCatchUpQuiz = computed(() =>
  state.value?.flowMode === 'CATCH_UP'
    && (displayStage.value === 'DAY1_QUIZ' || displayStage.value === 'DAY2_QUIZ'),
)
const canAdvanceCatchUp = computed(() => !isCatchUpQuiz.value || quizActivityCompleted.value)
const currentModule = computed(
  () => state.value?.modules.find((module) => module.state === 'ACTIVE' || module.state === 'GUIDED')?.type ?? null,
)
let presenceTimer: ReturnType<typeof setInterval> | undefined
let closingTimer: ReturnType<typeof setInterval> | undefined
let catchUpQuizNoticeTimer: ReturnType<typeof setTimeout> | undefined
let awaitingJoinNoticeTimer: ReturnType<typeof setTimeout> | undefined
let presenceRequestInFlight = false

onBeforeRouteLeave(() => {
  if (allowRouteExit.value || !student.value || !state.value) return true
  return false
})

onMounted(async () => {
  try {
    const loadedStudent = await authStore.loadStudent()
    if (route.params.roomId !== loadedStudent.roomId) {
      await router.replace({ name: 'student-room', params: { roomId: loadedStudent.roomId } })
    }
    await roomSessionStore.refreshStudent(loadedStudent.syncVersion, loadedStudent.roomId)
    roomSessionStore.startStudentPolling(loadedStudent.roomId)
    currentPage.value = roomSessionStore.studentState?.effectiveStage
      ?? roomSessionStore.studentState?.currentStage
      ?? 'WAITING'
    startPresenceReporting()
  } catch (error) {
    if (isUnauthorized(error)) {
      allowRouteExit.value = true
      await router.replace({ name: 'student-join' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '수업 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  roomSessionStore.stopPolling()
  stopPresenceReporting()
  if (closingTimer) clearInterval(closingTimer)
  if (catchUpQuizNoticeTimer) clearTimeout(catchUpQuizNoticeTimer)
  if (awaitingJoinNoticeTimer) clearTimeout(awaitingJoinNoticeTimer)
})

watch(
  () => roomSessionStore.pollError,
  async (error) => {
    if (error && isUnauthorized(error)) {
      roomSessionStore.stopPolling()
      allowRouteExit.value = true
      await router.replace({ name: 'student-join' })
    }
  },
)

watch(
  () => state.value?.syncVersion,
  async (nextVersion, previousVersion) => {
    if (nextVersion !== undefined && previousVersion !== undefined && nextVersion !== previousVersion) {
      await authStore.loadStudent()
    }
  },
)

watch(
  () => displayStage.value,
  (stage) => {
    if (!stage) return
    quizActivityCompleted.value = false
    catchUpNextVisible.value = true
    currentPage.value = stage
    void reportPresence()
    const closingStage = ['DAY1_CLOSED', 'DAY2_COMPLETED'].includes(stage)
    if (!closingStage) {
      if (closingTimer) clearInterval(closingTimer)
      closingTimer = undefined
      closingCountdown.value = null
      closingNoticeKind.value = null
      if (sessionClosed.value && closedDay.value === 1) {
        sessionClosed.value = false
        closedDay.value = null
      }
    }
    if (closingStage && closingCountdown.value === null && !sessionClosed.value) {
      closingNoticeKind.value = stage === 'DAY1_CLOSED' ? 'DAY1' : 'DAY2'
      closingCountdown.value = 5
      closingTimer = setInterval(() => {
        if (closingCountdown.value === null) return
        closingCountdown.value -= 1
        if (closingCountdown.value <= 0) {
          finishSessionClosing()
        }
      }, 1_000)
    }
  },
)

watch(
  () => isCatchUpQuiz.value && !canAdvanceCatchUp.value,
  (shouldShow) => {
    dismissCatchUpQuizNotice()
    if (!shouldShow) return
    catchUpQuizNoticeVisible.value = true
    catchUpQuizNoticeTimer = setTimeout(() => {
      catchUpQuizNoticeVisible.value = false
      catchUpQuizNoticeTimer = undefined
    }, 5_000)
  },
  { immediate: true },
)

watch(
  () => state.value?.flowMode === 'AWAITING_JOIN' ? state.value.joinStatus : null,
  (joinStatus) => {
    if (awaitingJoinNoticeTimer) clearTimeout(awaitingJoinNoticeTimer)
    awaitingJoinNoticeTimer = undefined
    awaitingJoinNoticeVisible.value = Boolean(joinStatus)
    if (!joinStatus) return
    awaitingJoinNoticeTimer = setTimeout(() => {
      awaitingJoinNoticeVisible.value = false
      awaitingJoinNoticeTimer = undefined
    }, 5_000)
  },
  { immediate: true },
)

function handlePageChange(page: string) {
  currentPage.value = page
  void reportPresence()
}

function startPresenceReporting() {
  stopPresenceReporting()
  document.addEventListener('visibilitychange', reportPresence)
  void reportPresence()
  presenceTimer = setInterval(() => {
    void reportPresence()
  }, 5_000)
}

function stopPresenceReporting() {
  if (presenceTimer !== undefined) {
    clearInterval(presenceTimer)
    presenceTimer = undefined
  }
  document.removeEventListener('visibilitychange', reportPresence)
  presenceRequestInFlight = false
}

function finishSessionClosing() {
  const noticeKind = closingNoticeKind.value
  const isDayOneClose = noticeKind === 'DAY1'
  if (closingTimer) clearInterval(closingTimer)
  closingTimer = undefined
  closingCountdown.value = null
  closedDay.value = noticeKind === 'DAY1' ? 1 : 2
  sessionClosed.value = true
  closingNoticeKind.value = null
  if (!isDayOneClose) {
    roomSessionStore.stopPolling()
    stopPresenceReporting()
  }
}

async function reportPresence() {
  if (!state.value || presenceRequestInFlight) return
  presenceRequestInFlight = true
  try {
    await sendPresenceHeartbeat({
      reportedStage: displayStage.value ?? state.value.currentStage,
      currentModule: currentModule.value,
      currentPage: currentPage.value,
      clientSyncVersion: state.value.syncVersion,
      visibility: document.visibilityState === 'visible' ? 'VISIBLE' : 'HIDDEN',
    })
  } catch (error) {
    if (isUnauthorized(error)) {
      stopPresenceReporting()
      allowRouteExit.value = true
      await router.replace({ name: 'student-join' })
    }
  } finally {
    presenceRequestInFlight = false
  }
}

function handleActivityCompletion(completed: boolean) {
  quizActivityCompleted.value = completed
  if (completed) flowError.value = ''
}

function dismissCatchUpQuizNotice() {
  if (catchUpQuizNoticeTimer) clearTimeout(catchUpQuizNoticeTimer)
  catchUpQuizNoticeTimer = undefined
  catchUpQuizNoticeVisible.value = false
}

async function advanceCatchUp() {
  if (advancingFlow.value) return
  advancingFlow.value = true
  catchUpNextVisible.value = false
  flowError.value = ''
  try {
    await advanceStudentFlow()
    await roomSessionStore.refreshStudent()
    currentPage.value = displayStage.value ?? 'WAITING'
  } catch (error) {
    catchUpNextVisible.value = true
    flowError.value = getApiErrorMessage(error, '현재 활동을 완료한 뒤 다음 단계로 이동해 주세요.')
  } finally {
    advancingFlow.value = false
  }
}
</script>

<template>
  <main class="student-room-page">
    <p v-if="loading" class="state-message state-message--plain">수업에 연결하고 있습니다</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <StudentSessionClosedView
      v-else-if="sessionClosed"
      :day-number="closedDay ?? 2"
      :purchases="day1Store.studentSummary?.purchases ?? []"
      :purchase-total="day1Store.studentSummary?.purchaseTotal ?? 0"
    />
    <template v-else-if="student && state">
      <section class="student-stage-card" :aria-label="stageDefinition?.label ?? displayStage">
        <div v-if="closingCountdown !== null" class="student-closing-countdown" role="alert">
          <div>
            <strong>{{ closingCountdown }}초 뒤에 종료됩니다.</strong>
            <span v-if="closingNoticeKind === 'DAY1'">1일차 결과를 저장한 뒤, 2일차가 시작되면 자동으로 연결돼요.</span>
            <span v-else>결과를 저장하고 있어요.</span>
          </div>
        </div>
        <aside v-if="state.flowMode === 'CATCH_UP'" class="catch-up-banner" role="status">
          <div>
            <strong>놓친 활동을 순서대로 진행하고 있어요</strong>
            <span>{{ state.completedActivityCount ?? 0 }} / {{ state.totalActivityCount ?? 0 }} 완료</span>
          </div>
          <p>
            반 수업은 {{ getRoomStage(state.roomStage ?? state.currentStage)?.label ?? state.roomStage ?? state.currentStage }} 단계예요.
            지연 퀴즈 정답수당과 투자 수익은 {{ (state.rewardRateBps ?? 8000) / 100 }}%가 적용됩니다.
          </p>
          <p v-if="isCatchUpQuiz">
            선생님 수업이 다음 단계로 이동해도 이 퀴즈를 제출해야 다음 활동으로 갈 수 있어요.
          </p>
        </aside>
        <aside
          v-else-if="state.flowMode === 'AWAITING_JOIN' && awaitingJoinNoticeVisible"
          class="catch-up-banner catch-up-banner--waiting"
          role="status"
        >
          <strong>{{ state.joinStatus === 'APPROVED_PENDING_BOUNDARY'
            ? '선생님 승인이 완료됐어요'
            : '필수 활동을 모두 마쳤어요' }}</strong>
          <p v-if="state.joinStatus === 'APPROVED_PENDING_BOUNDARY'">
            현재 수업 화면으로 이동했습니다. 선생님이 다음 단계로 진행하면 자동으로 정상 합류해요.
          </p>
          <p v-else>저장된 결과는 그대로 유지됩니다. 선생님이 승인하면 현재 수업 단계로 이동해요.</p>
        </aside>
        <p class="student-stage-card__mode">
          {{ state.mode === 'FREE' ? '지금 자유롭게 이용할 수 있어요' : state.mode === 'CLOSED' ? '이 활동은 종료되었어요' : '선생님과 함께 보는 중' }}
        </p>
        <component
          :is="stageComponent"
          :key="displayStage"
          class="student-stage-transition"
          v-bind="stageComponentProps"
          @page-change="handlePageChange"
          @completion-change="handleActivityCompletion"
        />
        <p v-if="flowError" class="form-error" role="alert">{{ flowError }}</p>
        <div
          v-if="isCatchUpQuiz && !canAdvanceCatchUp && catchUpQuizNoticeVisible"
          class="state-message catch-up-quiz-notice"
          role="status"
        >
          <span>퀴즈 제출을 완료하면 다음 활동으로 이동할 수 있어요.</span>
          <button type="button" aria-label="퀴즈 제출 안내 닫기" @click="dismissCatchUpQuizNotice">×</button>
        </div>
        <button
          v-if="state.flowMode === 'CATCH_UP' && canAdvanceCatchUp && catchUpNextVisible"
          class="settlement-action catch-up-next"
          type="button"
          :disabled="advancingFlow"
          @click="advanceCatchUp"
        >
          {{ advancingFlow ? '확인 중…' : '이 활동을 완료하고 다음으로' }}
        </button>
      </section>
    </template>
  </main>
</template>
