<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getRoomStage } from '../../features/room/model/stages'
import { advanceStudentFlow } from '../../features/flow/api/flow'
import { sendPresenceHeartbeat } from '../../features/monitoring/api/presence'
import { getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { formatCurrency, SERVICE_NAME } from '../../shared/config/brand'
import { useAuthStore } from '../../stores/auth'
import { useRoomSessionStore } from '../../stores/roomSession'
import { getStudentStageComponent, isPlaceholderStage } from './stages/stageComponents'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const errorMessage = ref('')
const flowError = ref('')
const advancingFlow = ref(false)
const quizActivityCompleted = ref(false)
const currentPage = ref('CONNECTING')
const student = computed(() => authStore.student)
const state = computed(() => roomSessionStore.studentState)
const displayStage = computed(() => state.value?.effectiveStage ?? state.value?.currentStage)
const stageDefinition = computed(() => getRoomStage(displayStage.value))
const stageComponent = computed(() => getStudentStageComponent(displayStage.value))
const stageComponentProps = computed(() =>
  isPlaceholderStage(displayStage.value)
    ? { stageLabel: stageDefinition.value?.label ?? displayStage.value ?? '대기 중' }
    : {},
)
const isCatchUpQuiz = computed(() =>
  state.value?.flowMode === 'CATCH_UP'
    && (displayStage.value === 'DAY1_QUIZ' || displayStage.value === 'DAY2_QUIZ'),
)
const canAdvanceCatchUp = computed(() => !isCatchUpQuiz.value || quizActivityCompleted.value)
const currentModule = computed(
  () => state.value?.modules.find((module) => module.state === 'ACTIVE' || module.state === 'GUIDED')?.type ?? null,
)
const connectionLabel = computed(() =>
  roomSessionStore.connectionStatus === 'CONNECTED' ? '연결됨' : '다시 연결 중',
)
const connectionTone = computed(() =>
  roomSessionStore.connectionStatus === 'CONNECTED' ? 'success' : 'warning',
)

let presenceTimer: ReturnType<typeof setInterval> | undefined
let presenceRequestInFlight = false

onMounted(async () => {
  try {
    const loadedStudent = await authStore.loadStudent()
    if (route.params.roomId !== loadedStudent.roomId) {
      await router.replace({ name: 'student-room', params: { roomId: loadedStudent.roomId } })
    }
    await roomSessionStore.refreshStudent(loadedStudent.syncVersion)
    roomSessionStore.startStudentPolling()
    currentPage.value = roomSessionStore.studentState?.effectiveStage
      ?? roomSessionStore.studentState?.currentStage
      ?? 'WAITING'
    startPresenceReporting()
  } catch (error) {
    if (isUnauthorized(error)) {
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
})

watch(
  () => roomSessionStore.pollError,
  async (error) => {
    if (error && isUnauthorized(error)) {
      roomSessionStore.stopPolling()
      await router.replace({ name: 'student-join' })
    }
  },
)

watch(
  () => displayStage.value,
  (stage) => {
    if (!stage) return
    quizActivityCompleted.value = false
    currentPage.value = stage
    void reportPresence()
  },
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

async function advanceCatchUp() {
  if (advancingFlow.value) return
  advancingFlow.value = true
  flowError.value = ''
  try {
    await advanceStudentFlow()
    await roomSessionStore.refreshStudent()
    currentPage.value = displayStage.value ?? 'WAITING'
  } catch (error) {
    flowError.value = getApiErrorMessage(error, '현재 활동을 완료한 뒤 다음 단계로 이동해 주세요.')
  } finally {
    advancingFlow.value = false
  }
}
</script>

<template>
  <main class="student-room-page">
    <p v-if="loading" class="state-message">수업에 연결하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="student && state">
      <header class="student-room-header">
        <RouterLink class="brand-link" to="/">{{ SERVICE_NAME }}</RouterLink>
        <div class="student-room-header__info">
          <span>{{ state.currentDay }}일차</span>
          <strong>{{ student.nickname }}</strong>
          <span>{{ formatCurrency(state.walletBalance) }}</span>
          <StatusBadge :label="connectionLabel" :tone="connectionTone" />
        </div>
      </header>

      <section class="student-stage-card" :aria-label="stageDefinition?.label ?? displayStage">
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
          v-else-if="state.flowMode === 'AWAITING_JOIN'"
          class="catch-up-banner catch-up-banner--waiting"
          role="status"
        >
          <div>
            <strong>필수 활동을 모두 마쳤어요</strong>
            <span>선생님 합류 승인 대기</span>
          </div>
          <p>저장된 결과는 그대로 유지됩니다. 선생님이 승인하면 현재 수업 단계로 이동해요.</p>
        </aside>
        <p class="student-stage-card__mode">
          {{ state.mode === 'FREE' ? '지금 자유롭게 이용할 수 있어요' : state.mode === 'CLOSED' ? '이 활동은 종료되었어요' : '선생님과 함께 보는 중' }}
        </p>
        <component
          :is="stageComponent"
          v-bind="stageComponentProps"
          @page-change="handlePageChange"
          @completion-change="handleActivityCompletion"
        />
        <p v-if="flowError" class="form-error" role="alert">{{ flowError }}</p>
        <p v-if="isCatchUpQuiz && !canAdvanceCatchUp" class="state-message">
          퀴즈 제출을 완료하면 다음 활동으로 이동할 수 있어요.
        </p>
        <button
          v-if="state.flowMode === 'CATCH_UP' && canAdvanceCatchUp"
          class="settlement-action catch-up-next"
          type="button"
          :disabled="advancingFlow"
          @click="advanceCatchUp"
        >
          {{ advancingFlow ? '확인 중…' : '이 활동을 완료하고 다음으로' }}
        </button>
        <div class="waiting-room-name">
          {{ student.roomName }} · {{ student.participantNumber }}번 · sync {{ state.syncVersion }}
        </div>
      </section>
    </template>
  </main>
</template>
