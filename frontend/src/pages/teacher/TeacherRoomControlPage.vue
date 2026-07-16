<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getAdjacentStages, getRoomStage, ROOM_STAGES } from '../../features/room/model/stages'
import { getApiErrorCode, getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth'
import { useQuizStore } from '../../stores/quiz'
import { useRoomSessionStore } from '../../stores/roomSession'

const moduleLabels: Record<string, string> = {
  QUIZ: '퀴즈',
  PAYSLIP: '월급명세서',
  KNOWLEDGE: '금융지식',
  SHOP: '상점',
  DEPOSIT: '예금',
  INVESTMENT: '투자',
  WALLET: '지갑',
  INVESTMENT_RESULT: '투자 결과',
  FUND_VOTE: '공동기금 투표',
  FINAL_RESULT: '최종 결과',
  SURVEY: '설문',
}

const moduleStateLabels: Record<string, string> = {
  LOCKED: '잠김',
  GUIDED: '교육 중',
  ACTIVE: '이용 가능',
  CLOSED: '종료',
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const quizStore = useQuizStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const changingStage = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const pendingStage = ref<string | null>(null)
const showStageWarning = ref(false)
const roomId = computed(() => String(route.params.roomId))
const state = computed(() => roomSessionStore.teacherState)
const teacher = computed(() => authStore.teacher)
const quizSummary = computed(() => quizStore.teacherSummary)
const currentStage = computed(() => getRoomStage(state.value?.currentStage))
const currentStageIndex = computed(() =>
  ROOM_STAGES.findIndex((stage) => stage.id === state.value?.currentStage),
)
const adjacentStages = computed(() => getAdjacentStages(state.value?.currentStage))
const connectionLabel = computed(() =>
  roomSessionStore.connectionStatus === 'CONNECTED' ? '2초 간격 동기화 중' : '다시 연결하는 중',
)
const connectionTone = computed(() =>
  roomSessionStore.connectionStatus === 'CONNECTED' ? 'success' : 'warning',
)

onMounted(async () => {
  try {
    if (!authStore.teacher) {
      await authStore.loadTeacher()
    }
    await roomSessionStore.refreshTeacher(roomId.value)
    roomSessionStore.startTeacherPolling(roomId.value)
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '수업 상태를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  roomSessionStore.stopPolling()
  quizStore.stopTeacherSummaryPolling()
})

watch(
  () => roomSessionStore.pollError,
  async (error) => {
    if (error && isUnauthorized(error)) {
      roomSessionStore.stopPolling()
      await router.replace({ name: 'teacher-login' })
    }
  },
  { immediate: true },
)

watch(
  () => state.value?.currentStage,
  async (stage) => {
    quizStore.stopTeacherSummaryPolling()
    quizStore.clearTeacherSummary()
    if (stage !== 'DAY1_QUIZ' && stage !== 'DAY2_QUIZ') return

    try {
      await quizStore.refreshTeacherSummary(roomId.value)
      quizStore.startTeacherSummaryPolling(roomId.value)
    } catch (error) {
      if (isUnauthorized(error)) {
        await router.replace({ name: 'teacher-login' })
        return
      }
      errorMessage.value = getApiErrorMessage(error, '퀴즈 제출 현황을 불러오지 못했습니다.')
    }
  },
  { immediate: true },
)

async function moveToStage(targetStage?: string, skipIncompleteWarning = false) {
  if (!targetStage || changingStage.value) return

  if (
    !skipIncompleteWarning &&
    state.value?.currentStage.includes('QUIZ') &&
    targetStage.includes('SETTLEMENT')
  ) {
    try {
      const latestSummary = await quizStore.refreshTeacherSummary(roomId.value)
      if (latestSummary.notSubmittedCount > 0) {
        pendingStage.value = targetStage
        showStageWarning.value = true
        return
      }
    } catch (error) {
      errorMessage.value = getApiErrorMessage(error, '퀴즈 제출 현황을 확인하지 못했습니다.')
      return
    }
  }

  changingStage.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.setStage(roomId.value, targetStage)
    noticeMessage.value = `${getRoomStage(targetStage)?.label ?? targetStage} 단계로 이동했습니다.`
  } catch (error) {
    if (getApiErrorCode(error) === 'STATE_CONFLICT') {
      await roomSessionStore.refreshTeacher(roomId.value)
      noticeMessage.value = '다른 화면의 변경을 반영했습니다. 최신 단계에서 다시 진행해 주세요.'
      return
    }
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '단계를 변경하지 못했습니다.')
  } finally {
    changingStage.value = false
  }
}

async function confirmStageMove() {
  const targetStage = pendingStage.value
  showStageWarning.value = false
  pendingStage.value = null
  await moveToStage(targetStage ?? undefined, true)
}

function cancelStageMove() {
  showStageWarning.value = false
  pendingStage.value = null
}

function moduleTone(moduleState: string) {
  if (moduleState === 'ACTIVE') return 'success'
  if (moduleState === 'GUIDED') return 'warning'
  return 'neutral'
}
</script>

<template>
  <main class="control-page">
    <header class="control-header">
      <div>
        <RouterLink class="brand-link" to="/teacher/rooms">비빔</RouterLink>
        <p v-if="teacher">{{ teacher.displayName }}의 수업 진행 대시보드</p>
      </div>
      <StatusBadge :label="connectionLabel" :tone="connectionTone" />
    </header>

    <p v-if="loading" class="state-message">수업 상태를 불러오고 있습니다.</p>
    <p v-else-if="errorMessage && !state" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="state">
      <section class="control-summary" aria-label="수업 상태 요약">
        <div>
          <p class="eyebrow">{{ state.currentDay }}일차 수업</p>
          <h1>{{ state.roomName }}</h1>
        </div>
        <dl>
          <div>
            <dt>접속 학생</dt>
            <dd>{{ state.connectedStudentCount }} / {{ state.expectedStudentCount }}</dd>
          </div>
          <div>
            <dt>현재 모드</dt>
            <dd>{{ state.mode === 'FREE' ? '자유 활동' : state.mode === 'CLOSED' ? '종료' : '교사 안내' }}</dd>
          </div>
          <div>
            <dt>상태 버전</dt>
            <dd>v{{ state.version }} · sync {{ state.syncVersion }}</dd>
          </div>
        </dl>
      </section>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

      <section class="control-grid">
        <aside class="stage-timeline" aria-label="전체 수업 단계">
          <div class="panel-heading">
            <p class="eyebrow">전체 흐름</p>
            <h2>단계 목록</h2>
          </div>
          <ol>
            <li
              v-for="stage in ROOM_STAGES"
              :key="stage.id"
              :class="{
                'is-current': stage.id === state.currentStage,
                'is-complete': ROOM_STAGES.findIndex((item) => item.id === stage.id) < currentStageIndex,
              }"
            >
              <span>{{ stage.day }}일차</span>
              <strong>{{ stage.label }}</strong>
            </li>
          </ol>
        </aside>

        <section class="stage-control-panel" aria-labelledby="current-stage-title">
          <div class="panel-heading">
            <p class="eyebrow">현재 단계</p>
            <h2 id="current-stage-title">{{ currentStage?.label ?? state.currentStage }}</h2>
          </div>
          <p class="stage-control-panel__description">
            {{ currentStage?.description ?? '현재 수업 활동을 진행합니다.' }}
          </p>
          <div class="stage-control-panel__preview">
            <span>{{ state.currentDay }}일차</span>
            <strong>{{ currentStage?.label ?? state.currentStage }}</strong>
            <small>학생 화면도 늦어도 2초 안에 이 단계로 전환됩니다.</small>
          </div>
          <div class="stage-navigation">
            <button
              class="button-secondary"
              type="button"
              :disabled="!adjacentStages.previous || changingStage"
              @click="moveToStage(adjacentStages.previous?.id)"
            >
              ← {{ adjacentStages.previous?.label ?? '이전 단계 없음' }}
            </button>
            <button
              type="button"
              :disabled="!adjacentStages.next || changingStage"
              @click="moveToStage(adjacentStages.next?.id)"
            >
              {{ changingStage ? '변경 중…' : `${adjacentStages.next?.label ?? '다음 단계 없음'} →` }}
            </button>
          </div>
          <p class="control-caution">
            {{ state.currentStage.includes('QUIZ')
              ? '미제출 학생이 있으면 다음 단계로 이동하기 전에 한 번 더 확인합니다.'
              : '정산이 필요한 단계의 완료 여부 확인은 다음 구현에서 연결됩니다.' }}
          </p>
        </section>

        <aside class="progress-panel" aria-label="학생 진행 현황">
          <div class="panel-heading">
            <p class="eyebrow">실시간 현황</p>
            <h2>{{ state.currentStage.includes('QUIZ') ? '퀴즈 제출' : '학생 연결' }}</h2>
          </div>
          <template v-if="state.currentStage.includes('QUIZ') && quizSummary">
            <div class="student-count-ring student-count-ring--quiz">
              <strong>{{ quizSummary.submittedCount }}</strong>
              <span>/ {{ quizSummary.expectedStudentCount }}명 제출</span>
            </div>
            <dl class="quiz-summary-counts">
              <div>
                <dt>풀이 중</dt>
                <dd>{{ quizSummary.inProgressCount }}명</dd>
              </div>
              <div>
                <dt>미시작</dt>
                <dd>{{ quizSummary.notStartedCount }}명</dd>
              </div>
            </dl>
            <ul class="quiz-student-list" aria-label="학생별 퀴즈 현황">
              <li v-for="studentItem in quizSummary.students" :key="studentItem.studentId">
                <span>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</span>
                <strong v-if="studentItem.submissionStatus === 'SUBMITTED'">
                  제출 · {{ studentItem.correctCount }} / {{ quizSummary.questionCount }}
                </strong>
                <strong v-else-if="studentItem.submissionStatus === 'IN_PROGRESS'">풀이 중</strong>
                <strong v-else>미시작</strong>
              </li>
            </ul>
          </template>
          <template v-else>
            <div class="student-count-ring">
              <strong>{{ state.connectedStudentCount }}</strong>
              <span>/ {{ state.expectedStudentCount }}명</span>
            </div>
            <p>로그인한 학생이 현재 수업 단계와 동기화됩니다.</p>
          </template>
        </aside>
      </section>

      <section class="module-status-panel" aria-labelledby="module-status-title">
        <div class="panel-heading">
          <p class="eyebrow">현재 접근 권한</p>
          <h2 id="module-status-title">모듈별 상태</h2>
        </div>
        <div class="module-status-grid">
          <article v-for="module in state.modules" :key="module.type">
            <strong>{{ moduleLabels[module.type] ?? module.type }}</strong>
            <StatusBadge
              :label="moduleStateLabels[module.state] ?? module.state"
              :tone="moduleTone(module.state)"
            />
          </article>
        </div>
      </section>

      <div v-if="showStageWarning && quizSummary" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="stage-warning-title">
          <p class="eyebrow">미제출 학생 확인</p>
          <h2 id="stage-warning-title">그래도 다음 단계로 이동할까요?</h2>
          <p class="confirm-modal__warning">
            아직 퀴즈를 제출하지 않은 학생이 {{ quizSummary.notSubmittedCount }}명 있습니다.
          </p>
          <p>단계를 이동하면 학생은 더 이상 답안을 저장하거나 제출할 수 없습니다.</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="cancelStageMove">돌아가기</button>
            <button type="button" @click="confirmStageMove">그래도 이동하기</button>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>
