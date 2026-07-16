<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getTeacherRoomStudents,
  type RoomPreparation,
  type TeacherRoomStudent,
} from '../../features/room/api/room'
import { getAdjacentStages, getRoomStage, ROOM_STAGES } from '../../features/room/model/stages'
import { getApiErrorCode, getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth'
import { useLearningStore } from '../../stores/learning'
import { useQuizStore } from '../../stores/quiz'
import { useRoomSessionStore } from '../../stores/roomSession'
import { useSettlementStore } from '../../stores/settlement'
import { useShopStore } from '../../stores/shop'

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
const learningStore = useLearningStore()
const quizStore = useQuizStore()
const settlementStore = useSettlementStore()
const shopStore = useShopStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const changingStage = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const preparation = ref<RoomPreparation | null>(null)
const preparationLoading = ref(false)
const preparationError = ref('')
const pendingStage = ref<string | null>(null)
const showStageWarning = ref(false)
const showSettlementConfirm = ref(false)
const roomId = computed(() => String(route.params.roomId))
const state = computed(() => roomSessionStore.teacherState)
const teacher = computed(() => authStore.teacher)
const quizSummary = computed(() => quizStore.teacherSummary)
const settlementPreview = computed(() => settlementStore.day1IncomePreview)
const shopSummary = computed(() => shopStore.teacherSummary)
const guidedLearning = computed(() => learningStore.guided)
const learningStages = new Set([
  'DAY1_KNOWLEDGE_GUIDED',
  'DAY1_SHOP_GUIDED',
  'DAY1_FINANCE_GUIDED',
])
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
  shopStore.stopTeacherSummaryPolling()
  settlementStore.clearDay1Income()
  shopStore.clearTeacherSummary()
  learningStore.clearGuided()
})

watch(
  () => [state.value?.currentStage, state.value?.connectedStudentCount] as const,
  async ([stage]) => {
    if (stage !== 'DAY1_LOBBY') {
      preparation.value = null
      preparationError.value = ''
      return
    }
    await refreshPreparation()
  },
  { immediate: true },
)

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
    shopStore.stopTeacherSummaryPolling()
    shopStore.clearTeacherSummary()
    settlementStore.clearDay1Income()
    learningStore.clearGuided()
    if (stage && learningStages.has(stage)) {
      try {
        await learningStore.refreshTeacher(roomId.value)
      } catch (error) {
        if (isUnauthorized(error)) {
          await router.replace({ name: 'teacher-login' })
          return
        }
        errorMessage.value = getApiErrorMessage(error, '교육 페이지를 불러오지 못했습니다.')
      }
      return
    }
    if (stage === 'DAY1_INCOME_SETTLEMENT') {
      try {
        await settlementStore.refreshDay1IncomePreview(roomId.value)
      } catch (error) {
        if (isUnauthorized(error)) {
          await router.replace({ name: 'teacher-login' })
          return
        }
        errorMessage.value = getApiErrorMessage(error, '소득 정산 미리보기를 불러오지 못했습니다.')
      }
      return
    }
    if (stage === 'DAY1_FREE_ACTIVITY') {
      try {
        await shopStore.refreshTeacherSummary(roomId.value)
        shopStore.startTeacherSummaryPolling(roomId.value)
      } catch (error) {
        if (isUnauthorized(error)) {
          await router.replace({ name: 'teacher-login' })
          return
        }
        errorMessage.value = getApiErrorMessage(error, '상점 판매 현황을 불러오지 못했습니다.')
      }
      return
    }
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

  if (
    state.value?.currentStage === 'DAY1_INCOME_SETTLEMENT' &&
    targetStage === 'DAY1_PAYSLIP_GUIDED'
  ) {
    try {
      const latest = await settlementStore.refreshDay1IncomePreview(roomId.value)
      if (latest.settlementStatus !== 'COMPLETED') {
        errorMessage.value = '소득 정산을 먼저 완료한 뒤 월급명세서 단계로 이동해 주세요.'
        return
      }
    } catch (error) {
      errorMessage.value = getApiErrorMessage(error, '정산 완료 여부를 확인하지 못했습니다.')
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

async function controlQuiz(command: 'OPEN_QUIZ' | 'CLOSE_QUIZ') {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.controlQuiz(roomId.value, command)
    await quizStore.refreshTeacherSummary(roomId.value)
    noticeMessage.value = command === 'OPEN_QUIZ'
      ? '퀴즈를 시작했습니다. 학생 화면에 문제가 열립니다.'
      : '퀴즈를 마감했습니다. 더 이상 답안을 저장하거나 제출할 수 없습니다.'
  } catch (error) {
    if (getApiErrorCode(error) === 'STATE_CONFLICT') {
      await roomSessionStore.refreshTeacher(roomId.value)
      await quizStore.refreshTeacherSummary(roomId.value)
    }
    errorMessage.value = getApiErrorMessage(error, '퀴즈 상태를 변경하지 못했습니다.')
  }
}

async function moveLearningPage(offset: number) {
  if (!guidedLearning.value || !state.value) return
  const target = guidedLearning.value.pages[guidedLearning.value.currentIndex + offset]
  if (!target) return

  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await learningStore.changeTeacherPage(
      roomId.value,
      target.pageId,
      state.value.version,
    )
    await roomSessionStore.refreshTeacher(roomId.value)
    noticeMessage.value = `${target.title} 페이지를 학생 화면에 표시했습니다.`
  } catch (error) {
    if (getApiErrorCode(error) === 'STATE_CONFLICT') {
      await roomSessionStore.refreshTeacher(roomId.value)
      await learningStore.refreshTeacher(roomId.value)
    }
    errorMessage.value = getApiErrorMessage(error, '교육 페이지를 변경하지 못했습니다.')
  }
}

async function refreshPreparation() {
  if (preparationLoading.value) return
  preparationLoading.value = true
  preparationError.value = ''
  try {
    preparation.value = await getTeacherRoomStudents(roomId.value)
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    preparationError.value = getApiErrorMessage(error, '참여 코드와 학생 현황을 불러오지 못했습니다.')
  } finally {
    preparationLoading.value = false
  }
}

async function copyParticipationCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    noticeMessage.value = `${code} 코드를 복사했습니다.`
  } catch {
    noticeMessage.value = `참여 코드: ${code}`
  }
}

function studentStatusLabel(student: TeacherRoomStudent) {
  if (student.online) return '접속'
  if (student.status === 'ACTIVE') return '설정 완료'
  if (student.status === 'ABSENT') return '결석'
  return '입장 전'
}

function studentStatusTone(student: TeacherRoomStudent): 'success' | 'warning' | 'neutral' {
  if (student.online) return 'success'
  if (student.status === 'ACTIVE') return 'warning'
  return 'neutral'
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

async function confirmSettlement() {
  showSettlementConfirm.value = false
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await settlementStore.executeDay1IncomeSettlement(roomId.value)
    if (result) {
      noticeMessage.value = result.replayed
        ? '이미 완료된 정산 결과를 불러왔습니다.'
        : `${result.settlement.targetCount}명의 소득 정산이 완료되었습니다.`
      await roomSessionStore.refreshTeacher(roomId.value)
    }
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '소득 정산을 완료하지 못했습니다.')
  }
}

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
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
          <div v-if="state.currentStage === 'DAY1_LOBBY' && preparation" class="room-preparation-workspace">
            <div class="room-preparation-heading">
              <div>
                <span>개인 참여 코드</span>
                <strong>{{ preparation.students.length }}개 생성됨</strong>
              </div>
              <button class="button-secondary" type="button" :disabled="preparationLoading" @click="refreshPreparation">
                {{ preparationLoading ? '새로고침 중…' : '접속 현황 새로고침' }}
              </button>
            </div>
            <p v-if="preparationError" class="form-error" role="alert">{{ preparationError }}</p>
            <div class="participation-code-list" role="table" aria-label="학생 참여 코드와 접속 현황">
              <div class="participation-code-list__head" role="row">
                <span>번호</span>
                <span>개인 코드</span>
                <span>닉네임</span>
                <span>상태</span>
              </div>
              <div v-for="studentItem in preparation.students" :key="studentItem.studentId" role="row">
                <strong>{{ studentItem.participantNumber }}번</strong>
                <button type="button" class="participation-code-button" @click="copyParticipationCode(studentItem.participationCode)">
                  {{ studentItem.participationCode }}
                </button>
                <span>{{ studentItem.nickname ?? '미설정' }}</span>
                <StatusBadge
                  :label="studentStatusLabel(studentItem)"
                  :tone="studentStatusTone(studentItem)"
                />
              </div>
            </div>
            <p class="control-caution">참여 코드를 학생별로 전달하세요. 코드는 한 학생만 사용할 수 있습니다.</p>
          </div>
          <p
            v-else-if="state.currentStage === 'DAY1_LOBBY'"
            :class="preparationError ? 'form-error' : 'state-message'"
            :role="preparationError ? 'alert' : undefined"
          >
            {{ preparationError || '참여 코드와 학생 현황을 불러오고 있습니다.' }}
          </p>
          <div v-else-if="state.currentStage.includes('QUIZ') && quizSummary" class="quiz-control-workspace">
            <div class="quiz-control-workspace__headline">
              <div>
                <span>퀴즈 상태</span>
                <strong>{{ quizSummary.status === 'READY' ? '시작 전' : quizSummary.status === 'OPEN' ? '진행 중' : '마감 완료' }}</strong>
              </div>
              <StatusBadge
                :label="quizSummary.status"
                :tone="quizSummary.status === 'OPEN' ? 'success' : quizSummary.status === 'READY' ? 'warning' : 'neutral'"
              />
            </div>
            <p v-if="quizSummary.status === 'READY'">
              시작 버튼을 누르기 전에는 학생이 문제를 열거나 답안을 제출할 수 없습니다.
            </p>
            <p v-else-if="quizSummary.status === 'OPEN'">
              학생이 답안을 저장하고 있습니다. 모두 제출했거나 시간이 끝나면 마감해 주세요.
            </p>
            <p v-else>답안 저장과 제출이 차단되었습니다. 이제 다음 단계로 이동할 수 있습니다.</p>
            <button
              v-if="quizSummary.status === 'READY'"
              type="button"
              @click="controlQuiz('OPEN_QUIZ')"
            >퀴즈 시작하기</button>
            <button
              v-else-if="quizSummary.status === 'OPEN'"
              class="button-secondary"
              type="button"
              @click="controlQuiz('CLOSE_QUIZ')"
            >퀴즈 마감하기</button>
          </div>
          <div v-else-if="state.currentStage === 'DAY1_FREE_ACTIVITY' && shopSummary" class="shop-summary-workspace">
            <div class="shop-summary-headline">
              <div>
                <span>누적 판매</span>
                <strong>{{ shopSummary.totalPurchaseCount }}건</strong>
              </div>
              <div>
                <span>상점 수입</span>
                <strong>{{ formatBibim(shopSummary.totalRevenue) }}</strong>
              </div>
            </div>
            <div class="teacher-product-list" aria-label="상품별 판매 현황">
              <article v-for="product in shopSummary.products" :key="product.productId">
                <div>
                  <strong>{{ product.name }}</strong>
                  <span>{{ formatBibim(product.price) }}</span>
                </div>
                <div>
                  <span>판매 {{ product.initialStock - product.currentStock }}개</span>
                  <strong>재고 {{ product.currentStock }}개</strong>
                </div>
              </article>
            </div>
          </div>
          <div v-else-if="state.currentStage === 'DAY1_INCOME_SETTLEMENT' && settlementPreview" class="settlement-workspace">
            <div class="settlement-status-row">
              <StatusBadge
                :label="settlementPreview.settlementStatus === 'COMPLETED' ? '정산 완료' : '정산 전'"
                :tone="settlementPreview.settlementStatus === 'COMPLETED' ? 'success' : 'warning'"
              />
              <span>소득세율 {{ settlementPreview.incomeTaxBps / 100 }}%</span>
            </div>
            <dl class="settlement-totals">
              <div>
                <dt>총소득</dt>
                <dd>{{ formatBibim(settlementPreview.totalGrossIncome) }}</dd>
              </div>
              <div>
                <dt>소득세</dt>
                <dd>{{ formatBibim(settlementPreview.totalIncomeTax) }}</dd>
              </div>
              <div>
                <dt>총 실수령액</dt>
                <dd>{{ formatBibim(settlementPreview.totalNetIncome) }}</dd>
              </div>
            </dl>
            <div class="settlement-student-table" role="table" aria-label="학생별 정산 미리보기">
              <div class="settlement-student-table__head" role="row">
                <span>학생</span>
                <span>퀴즈</span>
                <span>총소득</span>
                <span>세금</span>
                <span>실수령액</span>
              </div>
              <div v-for="item in settlementPreview.students" :key="item.studentId" role="row">
                <strong>{{ item.participantNumber }}번 {{ item.nickname ?? '미설정' }}</strong>
                <span>{{ item.quizSubmitted ? `${item.correctCount}개 정답` : '미제출' }}</span>
                <span>{{ formatBibim(item.grossIncome) }}</span>
                <span>−{{ formatBibim(item.incomeTax) }}</span>
                <strong>{{ formatBibim(item.netIncome) }}</strong>
              </div>
            </div>
            <button
              class="settlement-action"
              type="button"
              :disabled="settlementPreview.settlementStatus === 'COMPLETED' || settlementStore.executing"
              @click="showSettlementConfirm = true"
            >
              {{ settlementPreview.settlementStatus === 'COMPLETED'
                ? '정산 완료'
                : settlementStore.executing ? '정산 중…' : `${settlementPreview.targetCount}명 정산하기` }}
            </button>
          </div>
          <div v-else-if="learningStages.has(state.currentStage) && guidedLearning" class="learning-control-workspace">
            <header>
              <div>
                <p class="eyebrow">{{ guidedLearning.currentPage.topicTitle }}</p>
                <h3>{{ guidedLearning.currentPage.title }}</h3>
              </div>
              <strong>{{ guidedLearning.currentIndex + 1 }} / {{ guidedLearning.pages.length }}</strong>
            </header>
            <p>{{ guidedLearning.currentPage.content }}</p>
            <ul>
              <li v-for="point in guidedLearning.currentPage.keyPoints" :key="point">{{ point }}</li>
            </ul>
            <div class="learning-control-workspace__navigation">
              <button
                class="button-secondary"
                type="button"
                :disabled="guidedLearning.currentIndex === 0"
                @click="moveLearningPage(-1)"
              >← 이전 페이지</button>
              <button
                type="button"
                :disabled="guidedLearning.currentIndex === guidedLearning.pages.length - 1"
                @click="moveLearningPage(1)"
              >다음 페이지 →</button>
            </div>
            <small>페이지를 바꾸면 학생 화면에 늦어도 2초 안에 반영됩니다.</small>
          </div>
          <div v-else-if="state.currentStage !== 'DAY1_INCOME_SETTLEMENT' && state.currentStage !== 'DAY1_FREE_ACTIVITY'" class="stage-control-panel__preview">
            <span>{{ state.currentDay }}일차</span>
            <strong>{{ currentStage?.label ?? state.currentStage }}</strong>
            <small>학생 화면도 늦어도 2초 안에 이 단계로 전환됩니다.</small>
          </div>
          <p v-else class="state-message">현재 단계 현황을 불러오고 있습니다.</p>
          <div class="stage-navigation">
            <button
              class="button-secondary"
              type="button"
              :disabled="!adjacentStages.previous || changingStage || (
                state.currentStage === 'DAY1_INCOME_SETTLEMENT' &&
                settlementPreview?.settlementStatus === 'COMPLETED'
              )"
              @click="moveToStage(adjacentStages.previous?.id)"
            >
              ← {{ adjacentStages.previous?.label ?? '이전 단계 없음' }}
            </button>
            <button
              type="button"
              :disabled="!adjacentStages.next || changingStage || (
                state.currentStage.includes('QUIZ') && quizSummary?.status !== 'CLOSED'
              )"
              @click="moveToStage(adjacentStages.next?.id)"
            >
              {{ changingStage ? '변경 중…' : `${adjacentStages.next?.label ?? '다음 단계 없음'} →` }}
            </button>
          </div>
          <p class="control-caution">
            {{ state.currentStage.includes('QUIZ')
              ? quizSummary?.status === 'CLOSED'
                ? '퀴즈가 마감되었습니다. 미제출 학생을 확인한 뒤 다음 단계로 이동합니다.'
                : '퀴즈를 시작하고 마감해야 다음 단계로 이동할 수 있습니다.'
              : state.currentStage === 'DAY1_INCOME_SETTLEMENT'
                ? '정산을 완료해야 월급명세서 단계로 이동할 수 있습니다.'
                : '현재 단계의 활동이 끝나면 다음 단계로 이동합니다.' }}
          </p>
        </section>

        <aside class="progress-panel" aria-label="학생 진행 현황">
          <div class="panel-heading">
            <p class="eyebrow">실시간 현황</p>
            <h2>{{ state.currentStage.includes('QUIZ')
              ? '퀴즈 제출'
              : state.currentStage === 'DAY1_INCOME_SETTLEMENT'
                ? '정산 결과'
                : state.currentStage === 'DAY1_FREE_ACTIVITY' ? '최근 구매' : '학생 연결' }}</h2>
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
          <template v-else-if="state.currentStage === 'DAY1_INCOME_SETTLEMENT' && settlementPreview">
            <div class="student-count-ring student-count-ring--settlement">
              <strong>{{ settlementPreview.targetCount }}</strong>
              <span>명 정산 대상</span>
            </div>
            <dl class="quiz-summary-counts">
              <div>
                <dt>공동기금</dt>
                <dd>{{ formatBibim(settlementPreview.classFundBalance) }}</dd>
              </div>
              <div>
                <dt>상태</dt>
                <dd>{{ settlementPreview.settlementStatus === 'COMPLETED' ? '완료' : '미실행' }}</dd>
              </div>
            </dl>
            <p>걷힌 소득세는 학급 공동기금에 적립됩니다.</p>
          </template>
          <template v-else-if="state.currentStage === 'DAY1_FREE_ACTIVITY' && shopSummary">
            <div class="student-count-ring student-count-ring--shop">
              <strong>{{ shopSummary.totalPurchaseCount }}</strong>
              <span>건 구매 완료</span>
            </div>
            <ul v-if="shopSummary.recentPurchases.length > 0" class="shop-purchase-list" aria-label="최근 구매 목록">
              <li v-for="purchase in shopSummary.recentPurchases.slice(0, 6)" :key="purchase.purchaseId">
                <span>{{ purchase.participantNumber }}번 {{ purchase.nickname ?? '미설정' }}</span>
                <strong>{{ purchase.productName }} · {{ purchase.quantity }}개</strong>
              </li>
            </ul>
            <p v-else>아직 구매가 발생하지 않았습니다.</p>
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

      <div v-if="showSettlementConfirm && settlementPreview" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="settlement-confirm-title">
          <p class="eyebrow">1일차 소득 정산</p>
          <h2 id="settlement-confirm-title">{{ settlementPreview.targetCount }}명의 월급을 지급할까요?</h2>
          <p class="confirm-modal__warning">
            총 {{ formatBibim(settlementPreview.totalNetIncome) }}이 학생 지갑에 지급되고,
            소득세 {{ formatBibim(settlementPreview.totalIncomeTax) }}은 공동기금에 적립됩니다.
          </p>
          <p>완료된 정산은 다시 실행되지 않으며 같은 요청을 반복해도 잔액은 한 번만 변경됩니다.</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="showSettlementConfirm = false">취소</button>
            <button type="button" :disabled="settlementStore.executing" @click="confirmSettlement">
              정산 실행하기
            </button>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>
