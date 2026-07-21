<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getTeacherRoomStudents,
  type GuidedActivityView,
  type RoomPreparation,
  type TeacherRoomStudent,
} from '../../features/room/api/room'
import {
  getAdjacentStages,
  getRoomStage,
  getStageNavigationBlockReason,
  isNextStageDisabled,
  ROOM_STAGES,
} from '../../features/room/model/stages'
import { groupProductDistribution } from '../../features/day2/model/productDistribution'
import { getLobbyAttendance } from '../../features/room/model/attendance'
import { getApiErrorCode, getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import ParticipationCodeDialog from './ParticipationCodeDialog.vue'
import StudentFlowPanel from './StudentFlowPanel.vue'
import TeacherLearningMirror from './TeacherLearningMirror.vue'
import TeacherQuizResultPanel from './TeacherQuizResultPanel.vue'
import TeacherQuizMirror from './TeacherQuizMirror.vue'
import TeacherManagementWorkspace from './TeacherManagementWorkspace.vue'
import { getConnectedDay1Counts, getConnectedQuizCounts } from './liveParticipation'
import FreeActivityIntroStageView from '../student/stages/FreeActivityIntroStageView.vue'
import { SERVICE_NAME } from '../../shared/config/brand'
import { copyText } from '../../shared/utils/clipboard'
import { useAuthStore } from '../../stores/auth'
import { useDay1Store } from '../../stores/day1'
import { useDay2FinanceStore } from '../../stores/day2Finance'
import { useDay2FlowStore } from '../../stores/day2Flow'
import { useDay2EndStore } from '../../stores/day2End'
import { useLearningStore } from '../../stores/learning'
import { useQuizStore } from '../../stores/quiz'
import { useRoomSessionStore } from '../../stores/roomSession'
import { useSettlementStore } from '../../stores/settlement'
import { useShopStore } from '../../stores/shop'

const moduleLabels: Record<string, string> = {
  QUIZ: '퀴즈',
  PAYSLIP: '월급명세서',
  KNOWLEDGE: '학습 자료',
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
const day1Store = useDay1Store()
const day2FinanceStore = useDay2FinanceStore()
const day2FlowStore = useDay2FlowStore()
const day2EndStore = useDay2EndStore()
const learningStore = useLearningStore()
const quizStore = useQuizStore()
const settlementStore = useSettlementStore()
const shopStore = useShopStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const changingStage = ref(false)
const changingGuidedActivity = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const copiedParticipationCode = ref<string | null>(null)
const preparation = ref<RoomPreparation | null>(null)
const preparationLoading = ref(false)
const preparationError = ref('')
const showQuizCloseWarning = ref(false)
const showVoteCloseWarning = ref(false)
const showSettlementConfirm = ref(false)
const showDay1CloseConfirm = ref(false)
const showDay2SettlementConfirm = ref(false)
const showFundVotePrepareConfirm = ref(false)
const showRoomCompleteConfirm = ref(false)
const showParticipationCodes = ref(false)
const fundAdditionAmount = ref(0)
const fundAdditionReason = ref('학급 활동 지원금')
const managementView = ref<'PROGRESS' | 'STUDENTS' | 'SETTINGS'>('PROGRESS')
const pendingStageTarget = ref<string | null>(null)
const pendingLobbyExitTarget = ref<string | null>(null)
let preparationRequest: { roomId: string; promise: Promise<RoomPreparation | null> } | undefined
const roomId = computed(() => String(route.params.roomId))
const state = computed(() => roomSessionStore.teacherState)
const teacher = computed(() => authStore.teacher)
const quizSummary = computed(() => quizStore.teacherSummary)
const connectedQuizCounts = computed(() => getConnectedQuizCounts(quizSummary.value?.students ?? []))
const incompleteQuizStudents = computed(() => (
  quizSummary.value?.students.filter((student) => student.submissionStatus !== 'SUBMITTED') ?? []
))
const settlementPreview = computed(() => settlementStore.day1IncomePreview)
const shopSummary = computed(() => shopStore.teacherSummary)
const day1Summary = computed(() => day1Store.teacherSummary)
const connectedDay1Counts = computed(() => getConnectedDay1Counts(day1Summary.value?.students ?? []))
const day1ClosePreview = computed(() => day1Store.closePreview)
const day2FinancePreview = computed(() => day2FinanceStore.teacherPreview)
const day2QuizRewardPreview = computed(() => day2FlowStore.quizRewardPreview)
const inflationPreview = computed(() => day2FlowStore.inflationPreview)
const fundOverview = computed(() => day2EndStore.fund)
const fundCostOptions = computed(() => day2EndStore.costOptions)
const fundVote = computed(() => day2EndStore.vote)
const decidedFundOption = computed(() => fundVote.value?.options.find((option) => option.decided) ?? null)
const finalSummary = computed(() => day2EndStore.finalSummary)
const productDistribution = computed(() => day2EndStore.distribution)
const productDistributionByDay = computed(() => groupProductDistribution(
  productDistribution.value?.items ?? [],
))
const canPrepareFundVote = computed(() => Boolean(fundOverview.value))
const guidedLearning = computed(() => learningStore.guided)
const learningStages = new Set([
  'DAY1_KNOWLEDGE_GUIDED',
  'DAY1_SHOP_GUIDED',
  'DAY1_FINANCE_GUIDED',
  'DAY2_TAX_INFLATION_GUIDED',
])
const currentStage = computed(() => getRoomStage(state.value?.currentStage))
const currentStageIndex = computed(() =>
  ROOM_STAGES.findIndex((stage) => stage.id === state.value?.currentStage),
)
const adjacentStages = computed(() => getAdjacentStages(state.value?.currentStage))
const hasMoreLearningPages = computed(() => Boolean(
  state.value?.currentStage
  && learningStages.has(state.value.currentStage)
  && guidedLearning.value
  && guidedLearning.value.currentIndex < guidedLearning.value.pages.length - 1,
))
const shouldHighlightStageAdvance = computed(() => !hasMoreLearningPages.value)
const pendingStageDefinition = computed(() => getRoomStage(pendingStageTarget.value ?? undefined))
const pendingLobbyExitDefinition = computed(() => getRoomStage(pendingLobbyExitTarget.value ?? undefined))
const lobbyAttendance = computed(() => getLobbyAttendance(preparation.value?.students ?? []))
const lobbyWarningCount = computed(() => (
  lobbyAttendance.value.notJoined.length + lobbyAttendance.value.disconnected.length
))
const pendingStageMovesForward = computed(() => {
  const targetIndex = ROOM_STAGES.findIndex((stage) => stage.id === pendingStageTarget.value)
  return targetIndex > currentStageIndex.value
})
const pendingStageDistance = computed(() => {
  const targetIndex = ROOM_STAGES.findIndex((stage) => stage.id === pendingStageTarget.value)
  return Math.max(0, Math.abs(targetIndex - currentStageIndex.value) - 1)
})
const nextStageDisabled = computed(() =>
  isNextStageDisabled({
    currentStage: state.value?.currentStage,
    hasNextStage: Boolean(adjacentStages.value.next),
    changingStage: changingStage.value,
    quizStatus: quizSummary.value?.status,
    day2FinanceSettlementStatus: day2FinancePreview.value?.settlementStatus,
    day2QuizRewardSettlementStatus: day2QuizRewardPreview.value?.settlementStatus,
    fundExpenseApplied: Boolean(fundOverview.value?.appliedExpense),
    votePrepared: Boolean(fundVote.value),
    voteStatus: fundVote.value?.status,
  }),
)
function stageNavigationBlockReason(targetStage?: string) {
  return getStageNavigationBlockReason({
    currentStage: state.value?.currentStage,
    targetStage,
    changingStage: changingStage.value,
    quizStatus: quizSummary.value?.status,
    day1IncomeSettlementStatus: settlementPreview.value?.settlementStatus,
    day2FinanceSettlementStatus: day2FinancePreview.value?.settlementStatus,
    day2QuizRewardSettlementStatus: day2QuizRewardPreview.value?.settlementStatus,
    inflationApplied: Boolean(inflationPreview.value?.applied),
    fundExpenseApplied: Boolean(fundOverview.value?.appliedExpense),
    votePrepared: Boolean(fundVote.value),
    voteStatus: fundVote.value?.status,
  })
}

async function requestStageMove(targetStage?: string) {
  if (!targetStage) return
  const blockReason = stageNavigationBlockReason(targetStage)
  if (blockReason) {
    errorMessage.value = blockReason
    return
  }
  const targetIndex = ROOM_STAGES.findIndex((stage) => stage.id === targetStage)
  if (state.value?.currentStage === 'DAY1_LOBBY' && targetIndex > currentStageIndex.value) {
    const latestPreparation = await refreshPreparation()
    if (!latestPreparation) {
      errorMessage.value = preparationError.value || '학생 접속 상태를 확인하지 못했습니다.'
      return
    }
    if (lobbyWarningCount.value > 0) {
      pendingLobbyExitTarget.value = targetStage
      return
    }
  }
  continueStageMove(targetStage)
}

function continueStageMove(targetStage: string) {
  const targetIndex = ROOM_STAGES.findIndex((stage) => stage.id === targetStage)
  if (Math.abs(targetIndex - currentStageIndex.value) > 1) {
    pendingStageTarget.value = targetStage
    return
  }
  void moveToStage(targetStage)
}

async function confirmLobbyExit() {
  const targetStage = pendingLobbyExitTarget.value
  pendingLobbyExitTarget.value = null
  if (targetStage) await moveToStage(targetStage)
}

async function confirmStageMove() {
  const targetStage = pendingStageTarget.value
  pendingStageTarget.value = null
  if (targetStage) await moveToStage(targetStage)
}

async function loadTeacherRoom(targetRoomId: string) {
  roomSessionStore.resetTeacherContext()
  loading.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    if (!authStore.teacher) {
      await authStore.loadTeacher()
    }
    await roomSessionStore.refreshTeacher(targetRoomId)
    if (roomId.value !== targetRoomId) return
    roomSessionStore.startTeacherPolling(targetRoomId)
  } catch (error) {
    if (roomId.value !== targetRoomId) return
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '수업 상태를 불러오지 못했습니다.')
  } finally {
    if (roomId.value === targetRoomId) loading.value = false
  }
}

watch(
  roomId,
  (nextRoomId) => {
    void loadTeacherRoom(nextRoomId)
  },
  { immediate: true },
)

onUnmounted(() => {
  roomSessionStore.resetTeacherContext()
  quizStore.stopTeacherSummaryPolling()
  shopStore.stopTeacherSummaryPolling()
  settlementStore.clearDay1Income()
  shopStore.clearTeacherSummary()
  learningStore.clearGuided()
  day1Store.clear()
  day2FinanceStore.clear()
  day2FlowStore.clear()
  day2EndStore.clear()
})

watch(
  () => [state.value?.currentStage, state.value?.connectedStudentCount] as const,
  async ([stage]) => {
    if (stage !== 'DAY1_LOBBY') {
      if (!showParticipationCodes.value) preparation.value = null
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
    day1Store.clear()
    day2FinanceStore.clear()
    day2FlowStore.clear()
    day2EndStore.clear()
    if (stage === 'DAY1_SUMMARY' || stage === 'DAY1_CLOSED') {
      try {
        await day1Store.refreshTeacherSummary(roomId.value)
        if (stage === 'DAY1_SUMMARY') {
          await day1Store.refreshClosePreview(roomId.value)
        }
      } catch (error) {
        if (isUnauthorized(error)) {
          await router.replace({ name: 'teacher-login' })
          return
        }
        errorMessage.value = getApiErrorMessage(error, '1일차 결과를 불러오지 못했습니다.')
      }
      return
    }
    if (stage && [
      'DAY2_LOGIN',
      'DAY2_SETTLEMENT_WAIT',
      'DAY2_FINANCE_SETTLEMENT',
      'DAY2_RESULT_GUIDED',
    ].includes(stage)) {
      try {
        await day2FinanceStore.refreshTeacher(roomId.value)
      } catch (error) {
        if (isUnauthorized(error)) {
          await router.replace({ name: 'teacher-login' })
          return
        }
        errorMessage.value = getApiErrorMessage(error, '2일차 금융 정산 정보를 불러오지 못했습니다.')
      }
      return
    }
    if (stage === 'DAY2_FUND_COST') {
      try {
        await day2EndStore.refreshFund(roomId.value)
        try {
          await day2EndStore.refreshVote(roomId.value)
        } catch (error) {
          if (getApiErrorCode(error) !== 'VOTE_NOT_FOUND') throw error
        }
      } catch (error) {
        errorMessage.value = getApiErrorMessage(error, '공동기금 정보를 불러오지 못했습니다.')
      }
      return
    }
    if (stage && ['DAY2_FUND_VOTE', 'DAY2_FUND_RESULT'].includes(stage)) {
      try {
        await Promise.all([
          day2EndStore.refreshFund(roomId.value),
          day2EndStore.refreshVote(roomId.value),
        ])
        if (stage === 'DAY2_FUND_VOTE') {
          day2EndStore.startVotePolling(roomId.value)
        }
      } catch (error) {
        errorMessage.value = getApiErrorMessage(error, '공동기금 투표를 불러오지 못했습니다.')
      }
      return
    }
    if (stage && ['DAY2_FINAL_RESULT', 'DAY2_SURVEY', 'DAY2_COMPLETED'].includes(stage)) {
      try {
        await day2EndStore.refreshFinal(roomId.value)
      } catch (error) {
        errorMessage.value = getApiErrorMessage(error, '최종 결과를 불러오지 못했습니다.')
      }
      return
    }
    if (stage === 'DAY2_QUIZ_SETTLEMENT') {
      try {
        await day2FlowStore.refreshQuizReward(roomId.value)
      } catch (error) {
        errorMessage.value = getApiErrorMessage(error, '경제 퀴즈 보상 정보를 불러오지 못했습니다.')
      }
      return
    }
    if (stage && [
      'DAY2_TAX_INFLATION_GUIDED',
      'DAY2_INFLATION_APPLY',
      'DAY2_FINAL_SHOP',
    ].includes(stage)) {
      try {
        await day2FlowStore.refreshInflation(roomId.value)
        if (stage === 'DAY2_TAX_INFLATION_GUIDED') {
          await learningStore.refreshTeacher(roomId.value)
        }
        if (stage === 'DAY2_FINAL_SHOP') {
          await shopStore.refreshTeacherSummary(roomId.value)
          shopStore.startTeacherSummaryPolling(roomId.value)
        }
      } catch (error) {
        errorMessage.value = getApiErrorMessage(error, '물가상승 정보를 불러오지 못했습니다.')
      }
      return
    }
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
        await Promise.all([
          shopStore.refreshTeacherSummary(roomId.value),
          day1Store.refreshTeacherSummary(roomId.value),
        ])
        shopStore.startTeacherSummaryPolling(roomId.value)
        day1Store.startTeacherSummaryPolling(roomId.value)
      } catch (error) {
        if (isUnauthorized(error)) {
          await router.replace({ name: 'teacher-login' })
          return
        }
        errorMessage.value = getApiErrorMessage(error, '학생 경제활동 현황을 불러오지 못했습니다.')
      }
      return
    }
    if (!stage || ![
      'DAY1_QUIZ',
      'DAY1_QUIZ_RESULT',
      'DAY2_QUIZ',
      'DAY2_QUIZ_RESULT',
    ].includes(stage)) return

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

async function moveToStage(targetStage?: string) {
  if (!targetStage || changingStage.value) return

  const navigationBlockReason = stageNavigationBlockReason(targetStage)
  if (navigationBlockReason) {
    errorMessage.value = navigationBlockReason
    return
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

  if (
    state.value?.currentStage === 'DAY2_QUIZ_SETTLEMENT' &&
    targetStage === 'DAY2_TAX_INFLATION_GUIDED'
  ) {
    try {
      const latest = await day2FlowStore.refreshQuizReward(roomId.value)
      if (latest.settlementStatus !== 'COMPLETED') {
        errorMessage.value = '경제 퀴즈 보상 정산을 먼저 완료해 주세요.'
        return
      }
    } catch (error) {
      errorMessage.value = getApiErrorMessage(error, '퀴즈 보상 정산 상태를 확인하지 못했습니다.')
      return
    }
  }

  if (
    state.value?.currentStage === 'DAY2_INFLATION_APPLY' &&
    targetStage === 'DAY2_FINAL_SHOP'
  ) {
    try {
      const latest = await day2FlowStore.refreshInflation(roomId.value)
      if (!latest.applied) {
        errorMessage.value = '물가상승을 먼저 적용해 주세요.'
        return
      }
    } catch (error) {
      errorMessage.value = getApiErrorMessage(error, '물가상승 적용 상태를 확인하지 못했습니다.')
      return
    }
  }

  if (state.value?.currentStage === 'DAY2_FUND_COST' && targetStage === 'DAY2_FUND_VOTE') {
    try {
      await day2EndStore.refreshFund(roomId.value)
      if (!day2EndStore.vote) {
        errorMessage.value = '공동비용을 검토하고 투표 선택지를 준비해 주세요.'
        return
      }
    } catch (error) {
      errorMessage.value = getApiErrorMessage(error, '공동기금 준비 상태를 확인하지 못했습니다.')
      return
    }
  }

  if (state.value?.currentStage === 'DAY2_FUND_VOTE' && targetStage === 'DAY2_FUND_RESULT') {
    try {
      const latestVote = await day2EndStore.refreshVote(roomId.value)
      if (latestVote.status !== 'DECIDED') {
        errorMessage.value = '투표를 마감하고 최종 결과를 확정해 주세요.'
        return
      }
    } catch (error) {
      errorMessage.value = getApiErrorMessage(error, '투표 결과를 확인하지 못했습니다.')
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
      const conflictMessage = getApiErrorMessage(error, '단계를 변경하지 못했습니다.')
      if (conflictMessage.includes('다른 화면에서 수업 상태가 변경')) {
        noticeMessage.value = '다른 화면의 변경을 반영했습니다. 최신 단계에서 다시 진행해 주세요.'
      } else {
        errorMessage.value = conflictMessage
      }
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

async function selectGuidedActivity(view: GuidedActivityView) {
  if (changingGuidedActivity.value || state.value?.guidedActivityView === view) return
  changingGuidedActivity.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.setGuidedActivityView(roomId.value, view)
    noticeMessage.value = '학생 설명 화면을 변경했습니다.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '학생 설명 화면을 변경하지 못했습니다.')
  } finally {
    changingGuidedActivity.value = false
  }
}

async function controlQuiz(command: 'OPEN_QUIZ' | 'CLOSE_QUIZ' | 'FORCE_CLOSE_QUIZ') {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.controlQuiz(roomId.value, command)
    await quizStore.refreshTeacherSummary(roomId.value)
    noticeMessage.value = command === 'OPEN_QUIZ'
      ? '퀴즈를 시작했습니다. 학생 화면에 문제가 열립니다.'
      : command === 'FORCE_CLOSE_QUIZ'
        ? '퀴즈를 마감했습니다. 미제출 학생은 개인 보충 퀴즈를 계속 진행합니다.'
        : '퀴즈를 마감했습니다. 제출 결과가 저장되었습니다.'
  } catch (error) {
    if (getApiErrorCode(error) === 'QUIZ_INCOMPLETE') {
      await quizStore.refreshTeacherSummary(roomId.value)
      showQuizCloseWarning.value = true
      return
    }
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
  const targetRoomId = roomId.value
  if (preparationRequest?.roomId === targetRoomId) return preparationRequest.promise
  preparationLoading.value = true
  preparationError.value = ''
  const request = {
    roomId: targetRoomId,
    promise: Promise.resolve(null) as Promise<RoomPreparation | null>,
  }
  request.promise = (async () => {
    try {
      const latest = await getTeacherRoomStudents(targetRoomId)
      if (roomId.value !== targetRoomId) return null
      preparation.value = latest
      return latest
    } catch (error) {
      if (isUnauthorized(error)) {
        await router.replace({ name: 'teacher-login' })
        return null
      }
      preparationError.value = getApiErrorMessage(error, '참여 코드와 학생 현황을 불러오지 못했습니다.')
      return null
    } finally {
      if (preparationRequest === request) {
        preparationLoading.value = false
        preparationRequest = undefined
      }
    }
  })()
  preparationRequest = request
  return request.promise
}

async function copyParticipationCode(code: string) {
  const copied = await copyText(code)
  copiedParticipationCode.value = copied ? code : null
  if (copied) {
    noticeMessage.value = `${code} 코드를 복사했습니다.`
    errorMessage.value = ''
  } else {
    noticeMessage.value = ''
    errorMessage.value = `${code} 코드를 자동으로 복사하지 못했습니다. 코드를 직접 선택해 복사해 주세요.`
  }
}

async function openParticipationCodes() {
  showParticipationCodes.value = true
  await refreshPreparation()
}

function closeParticipationCodes() {
  showParticipationCodes.value = false
  copiedParticipationCode.value = null
  if (state.value?.currentStage !== 'DAY1_LOBBY') preparation.value = null
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

async function applyFundExpense(optionId: string) {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await day2EndStore.applyExpense(roomId.value, optionId)
    if (result) noticeMessage.value = result.replayed ? '기존 공동비용 결과를 불러왔습니다.' : '공동비용을 적용했습니다.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '공동비용을 적용하지 못했습니다.')
  }
}

async function addClassFund() {
  if (fundAdditionAmount.value < 1 || !fundAdditionReason.value.trim()) return
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await day2EndStore.addFund(roomId.value, fundAdditionAmount.value, fundAdditionReason.value.trim())
    noticeMessage.value = `공동기금에 ${formatBibim(fundAdditionAmount.value)}을 추가했습니다.`
    fundAdditionAmount.value = 0
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '공동기금을 추가하지 못했습니다.')
  }
}

async function prepareFundVote() {
  errorMessage.value = ''
  noticeMessage.value = ''
  const withoutExpense = !fundOverview.value?.appliedExpense
  try {
    const result = await day2EndStore.prepareVote(roomId.value)
    if (result) {
      noticeMessage.value = withoutExpense
        ? '공동비용을 사용하지 않고 학생 투표 선택지를 준비했습니다.'
        : '학생 투표 선택지를 준비했습니다.'
    }
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '투표를 준비하지 못했습니다.')
  }
}

function requestFundVotePreparation() {
  if (fundVote.value || day2EndStore.mutating) return
  if (!fundOverview.value?.appliedExpense) {
    showFundVotePrepareConfirm.value = true
    return
  }
  void prepareFundVote()
}

async function confirmFundVoteWithoutExpense() {
  showFundVotePrepareConfirm.value = false
  await prepareFundVote()
}

async function controlFundVote(action: 'OPEN' | 'CLOSE', force = false) {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await day2EndStore.updateVote(action, roomId.value, force)
    if (result) {
      noticeMessage.value = action === 'OPEN'
        ? '공동기금 투표를 시작했습니다.'
        : result.status === 'DECIDED'
          ? '투표를 마감하고 단독 1위 결과를 확정했습니다.'
          : '투표를 마감했습니다. 동률 1위 중 최종 선택을 골라 주세요.'
    }
  } catch (error) {
    if (getApiErrorCode(error) === 'VOTE_INCOMPLETE') {
      await day2EndStore.refreshVote(roomId.value)
      showVoteCloseWarning.value = true
      return
    }
    errorMessage.value = getApiErrorMessage(error, '투표 상태를 변경하지 못했습니다.')
  }
}

async function requestFundVoteClose() {
  errorMessage.value = ''
  try {
    const latest = await day2EndStore.refreshVote(roomId.value)
    if (latest.pendingStudents.length > 0) {
      showVoteCloseWarning.value = true
      return
    }
    await controlFundVote('CLOSE')
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '투표 참여 현황을 확인하지 못했습니다.')
  }
}

async function confirmFundVoteClose() {
  showVoteCloseWarning.value = false
  await controlFundVote('CLOSE', true)
}

async function decideFundVote(optionId: string) {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await day2EndStore.decideVote(roomId.value, optionId)
    if (result) noticeMessage.value = '동점 투표의 최종 선택을 확정했습니다.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '최종 선택을 확정하지 못했습니다.')
  }
}

async function completeRoom() {
  showRoomCompleteConfirm.value = false
  changingStage.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.completeRoom(roomId.value)
    await day2EndStore.refreshFinal(roomId.value)
    noticeMessage.value = '수업을 종료했습니다. 최종 결과 조회는 계속 사용할 수 있습니다.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '수업을 종료하지 못했습니다.')
  } finally {
    changingStage.value = false
  }
}

async function confirmDay1Close() {
  if (changingStage.value) return
  showDay1CloseConfirm.value = false
  changingStage.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.closeDayOne(roomId.value)
    await day1Store.refreshTeacherSummary(roomId.value)
    noticeMessage.value = '1일차를 마감했습니다. 확정된 금융 활동과 투자 시나리오가 고정되었습니다.'
  } catch (error) {
    if (getApiErrorCode(error) === 'STATE_CONFLICT') {
      await roomSessionStore.refreshTeacher(roomId.value)
    }
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '1일차를 마감하지 못했습니다.')
  } finally {
    changingStage.value = false
  }
}

async function requestQuizClose() {
  errorMessage.value = ''
  try {
    const latest = await quizStore.refreshTeacherSummary(roomId.value)
    if (latest.notSubmittedCount > 0) {
      showQuizCloseWarning.value = true
      return
    }
    await controlQuiz('CLOSE_QUIZ')
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '퀴즈 제출 현황을 확인하지 못했습니다.')
  }
}

async function confirmQuizClose() {
  showQuizCloseWarning.value = false
  await controlQuiz('FORCE_CLOSE_QUIZ')
}

async function startDayTwo() {
  if (changingStage.value) return
  changingStage.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await roomSessionStore.startDayTwo(roomId.value)
    noticeMessage.value = '2일차를 시작했습니다. 학생은 기존 참여 코드와 비밀번호로 다시 로그인할 수 있습니다.'
  } catch (error) {
    if (getApiErrorCode(error) === 'STATE_CONFLICT') {
      await roomSessionStore.refreshTeacher(roomId.value)
    }
    errorMessage.value = getApiErrorMessage(error, '2일차를 시작하지 못했습니다.')
  } finally {
    changingStage.value = false
  }
}

async function confirmDay2FinanceSettlement() {
  showDay2SettlementConfirm.value = false
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await day2FinanceStore.settle(roomId.value)
    await roomSessionStore.refreshTeacher(roomId.value)
    noticeMessage.value = result?.replayed
      ? '이미 완료된 금융 정산 결과를 불러왔습니다.'
      : `${result?.settlement.targetCount ?? 0}명의 예금·투자 정산을 완료했습니다.`
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '2일차 금융 정산을 완료하지 못했습니다.')
  }
}

async function settleDay2QuizReward() {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await day2FlowStore.settleQuizReward(roomId.value)
    await roomSessionStore.refreshTeacher(roomId.value)
    noticeMessage.value = result?.replayed
      ? '이미 완료된 경제 퀴즈 보상 결과를 불러왔습니다.'
      : `${result?.settlement.submittedCount ?? 0}명의 퀴즈 보상을 지급했습니다.`
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '경제 퀴즈 보상을 정산하지 못했습니다.')
  }
}

async function applyInflation() {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const result = await day2FlowStore.applyInflation(roomId.value)
    await roomSessionStore.refreshTeacher(roomId.value)
    noticeMessage.value = result?.replayed
      ? '이미 적용된 물가상승 결과를 불러왔습니다.'
      : `상품 ${result?.inflation.products.length ?? 0}개의 가격에 물가상승을 적용했습니다.`
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '물가상승을 적용하지 못했습니다.')
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
  <main class="control-page student-theme">
    <header class="control-header">
      <div>
        <RouterLink class="brand-link" to="/teacher/rooms">{{ SERVICE_NAME }}</RouterLink>
        <p v-if="teacher">{{ teacher.displayName }}의 수업 진행 대시보드</p>
      </div>
    </header>

    <div class="teacher-operations-shell">
      <aside class="teacher-primary-nav" aria-label="교사 메뉴">
        <div class="teacher-primary-nav__room">
          <span>{{ state?.currentDay ?? 1 }}일차</span>
          <strong>{{ state?.roomName ?? '우리 반 경제교실' }}</strong>
        </div>
        <nav>
          <RouterLink to="/teacher/rooms"><span aria-hidden="true">⌂</span>학급 목록</RouterLink>
          <button type="button" :class="{ 'is-active': managementView === 'PROGRESS' }" @click="managementView = 'PROGRESS'"><span aria-hidden="true">▶</span>수업 진행</button>
          <button type="button" :class="{ 'is-active': managementView === 'STUDENTS' }" @click="managementView = 'STUDENTS'"><span aria-hidden="true">♙</span>학생 계정</button>
          <button type="button" :class="{ 'is-active': managementView === 'SETTINGS' }" @click="managementView = 'SETTINGS'"><span aria-hidden="true">⚙</span>수업 설정</button>
        </nav>
        <div class="teacher-primary-nav__tip">
          <strong>교사 팁</strong>
          <p>다음 활동을 열면 학생들의 마을 화면이 자동으로 바뀝니다.</p>
        </div>
      </aside>

      <div class="teacher-operations-content">
        <TeacherManagementWorkspace
          v-if="managementView !== 'PROGRESS'"
          :room-id="roomId"
          :view="managementView"
          @refreshed="roomSessionStore.refreshTeacher(roomId)"
        />

        <template v-else>

    <p v-if="loading" class="state-message">수업 상태를 불러오고 있습니다.</p>
    <p v-else-if="errorMessage && !state" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="state">
      <StudentFlowPanel :room-id="roomId" />

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
          <p class="stage-timeline__guide">같은 일차의 선택 가능한 단계를 눌러 바로 이동할 수 있습니다.</p>
          <ol>
            <li
              v-for="stage in ROOM_STAGES"
              :key="stage.id"
              :class="{
                'is-current': stage.id === state.currentStage,
                'is-complete': ROOM_STAGES.findIndex((item) => item.id === stage.id) < currentStageIndex,
              }"
            >
              <button
                type="button"
                :disabled="Boolean(stageNavigationBlockReason(stage.id))"
                :title="stageNavigationBlockReason(stage.id)"
                :aria-current="stage.id === state.currentStage ? 'step' : undefined"
                @click="requestStageMove(stage.id)"
              >
                <span>{{ stage.day }}일차</span>
                <strong>{{ stage.label }}</strong>
              </button>
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
                <button
                  type="button"
                  class="participation-code-button"
                  :class="{ 'is-copied': copiedParticipationCode === studentItem.participationCode }"
                  :aria-label="`${studentItem.participationCode} 참여 코드 복사`"
                  @click="copyParticipationCode(studentItem.participationCode)"
                >
                  <span>{{ studentItem.participationCode }}</span>
                  <small>{{ copiedParticipationCode === studentItem.participationCode ? '복사됨' : '복사' }}</small>
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
          <TeacherQuizResultPanel
            v-else-if="['DAY1_QUIZ_RESULT', 'DAY2_QUIZ_RESULT'].includes(state.currentStage) && quizSummary"
            :summary="quizSummary"
            :day-number="state.currentStage === 'DAY1_QUIZ_RESULT' ? 1 : 2"
            :next-stage-label="state.currentStage === 'DAY1_QUIZ_RESULT' ? '소득·세금 정산' : '퀴즈 보상 정산'"
          />
          <TeacherQuizMirror
            v-else-if="['DAY1_QUIZ', 'DAY2_QUIZ'].includes(state.currentStage) && quizSummary"
            :summary="quizSummary"
            @open="controlQuiz('OPEN_QUIZ')"
            @close="requestQuizClose"
          />
          <div v-else-if="state.currentStage === 'DAY1_FREE_ACTIVITY' && day1Summary" class="teacher-live-economy">
            <header class="teacher-live-economy__header">
              <div><p class="eyebrow">학생 화면과 같은 경제활동 현황</p><h2>학생별 선택 현황</h2></div>
            </header>
            <div class="teacher-live-economy__summary">
              <span>🏦 예금 <strong>{{ day1Summary.depositConfirmedCount }}명</strong></span>
              <span>📈 투자 <strong>{{ day1Summary.investmentConfirmedCount }}명</strong></span>
              <span>🛒 구매 <strong>{{ day1Summary.purchaseParticipantCount }}명</strong></span>
              <span>💰 상점 <strong>{{ shopSummary?.totalPurchaseCount ?? 0 }}건</strong></span>
            </div>
            <div class="teacher-live-student-grid" aria-label="학생별 실시간 경제활동">
              <article v-for="studentItem in day1Summary.students" :key="studentItem.studentId">
                <header><span>{{ studentItem.participantNumber }}번</span><strong>{{ studentItem.nickname ?? '이름 미설정' }}</strong><b>{{ formatBibim(studentItem.walletBalance) }}</b></header>
                <div class="teacher-live-module" :class="`is-${studentItem.depositStatus.toLowerCase()}`">
                  <span aria-hidden="true">🏦</span><div><strong>예금</strong><small>{{ studentItem.depositStatus === 'DRAFT' ? '잠시 저장' : studentItem.depositConfirmed ? '확정 완료' : '선택 전' }}</small></div>
                  <b>{{ formatBibim(studentItem.depositStatus === 'DRAFT' ? studentItem.depositDraftPrincipal : studentItem.depositPrincipal) }}</b>
                </div>
                <div class="teacher-live-module" :class="`is-${studentItem.investmentStatus.toLowerCase()}`">
                  <span aria-hidden="true">📈</span><div><strong>투자</strong><small>{{ studentItem.investmentStatus === 'DRAFT' ? '잠시 저장' : studentItem.investmentConfirmed ? '확정 완료' : '선택 전' }}</small></div>
                  <b>{{ formatBibim(studentItem.investmentStatus === 'DRAFT' ? studentItem.investmentDraftTotal : studentItem.investmentPrincipal) }}</b>
                </div>
                <p v-if="studentItem.investments.length" class="teacher-live-investments">
                  {{ studentItem.investments.map((item) => `${item.assetName} ${formatBibim(item.amount)}`).join(' · ') }}
                </p>
                <div class="teacher-live-module" :class="{ 'is-confirmed': studentItem.purchased }">
                  <span aria-hidden="true">🛒</span><div><strong>상점</strong><small>{{ studentItem.purchased ? '구매 완료' : '구매 전' }}</small></div><b>{{ formatBibim(studentItem.purchaseTotal) }}</b>
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
              class="settlement-action teacher-stage-action"
              type="button"
              :disabled="settlementPreview.settlementStatus === 'COMPLETED' || settlementStore.executing"
              @click="showSettlementConfirm = true"
            >
              {{ settlementPreview.settlementStatus === 'COMPLETED'
                ? '정산 완료'
                : settlementStore.executing ? '정산 중…' : `${settlementPreview.targetCount}명 정산하기` }}
            </button>
          </div>
          <div
            v-else-if="(state.currentStage === 'DAY1_SUMMARY' || state.currentStage === 'DAY1_CLOSED') && day1Summary"
            class="day1-close-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>1일차 활동 결과</span>
                <strong>{{ day1Summary.studentCount }}명 집계 완료</strong>
              </div>
              <StatusBadge
                :label="state.currentStage === 'DAY1_CLOSED' ? '마감 완료' : '마감 전 확인'"
                :tone="state.currentStage === 'DAY1_CLOSED' ? 'success' : 'warning'"
              />
            </div>
            <dl class="day1-close-totals">
              <div><dt>학생 지갑</dt><dd>{{ formatBibim(day1Summary.totalWalletBalance) }}</dd></div>
              <div><dt>예금 원금</dt><dd>{{ formatBibim(day1Summary.totalDepositPrincipal) }}</dd></div>
              <div><dt>투자 원금</dt><dd>{{ formatBibim(day1Summary.totalInvestmentPrincipal) }}</dd></div>
              <div><dt>총소비</dt><dd>{{ formatBibim(day1Summary.totalPurchaseAmount) }}</dd></div>
            </dl>
            <div class="day1-student-table" role="table" aria-label="학생별 1일차 활동 결과">
              <div class="day1-student-table__head" role="row">
                <span>학생</span><span>잔액</span><span>예금</span><span>투자</span><span>소비</span>
              </div>
              <div v-for="studentItem in day1Summary.students" :key="studentItem.studentId" role="row">
                <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
                <span>{{ formatBibim(studentItem.walletBalance) }}</span>
                <span>{{ formatBibim(studentItem.depositPrincipal) }}</span>
                <span>{{ formatBibim(studentItem.investmentPrincipal) }}</span>
                <span>{{ formatBibim(studentItem.purchaseTotal) }}</span>
              </div>
            </div>
            <template v-if="state.currentStage === 'DAY1_SUMMARY' && day1ClosePreview">
              <ul v-if="day1ClosePreview.warnings.length > 0" class="day1-close-warnings">
                <li v-for="warning in day1ClosePreview.warnings" :key="warning">{{ warning }}</li>
              </ul>
              <button
                class="day1-close-action teacher-stage-action"
                type="button"
                :disabled="!day1ClosePreview.canClose || changingStage"
                @click="showDay1CloseConfirm = true"
              >
                1일차 마감하기
              </button>
            </template>
            <p v-else-if="state.currentStage === 'DAY1_CLOSED'" class="control-caution">
              1일차 금융 활동은 마감되어 더 이상 수정할 수 없습니다.
            </p>
            <button
              v-if="state.currentStage === 'DAY1_CLOSED'"
              class="day1-close-action teacher-stage-action"
              type="button"
              :disabled="changingStage"
              @click="startDayTwo"
            >
              {{ changingStage ? '2일차 준비 중…' : '2일차 시작하기' }}
            </button>
          </div>
          <div
            v-else-if="[
              'DAY2_LOGIN',
              'DAY2_SETTLEMENT_WAIT',
              'DAY2_FINANCE_SETTLEMENT',
              'DAY2_RESULT_GUIDED',
            ].includes(state.currentStage) && day2FinancePreview"
            class="day2-finance-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>{{ state.currentStage === 'DAY2_RESULT_GUIDED' ? '2일차 금융 결과 교육' : '2일차 예금·투자 정산' }}</span>
                <strong>{{ state.currentStage === 'DAY2_RESULT_GUIDED' ? '예금·투자·금융세 핵심 정리' : `${day2FinancePreview.targetCount}명 금융 결과` }}</strong>
              </div>
              <StatusBadge
                :label="day2FinancePreview.settlementStatus === 'COMPLETED' ? '정산 완료' : '정산 전'"
                :tone="day2FinancePreview.settlementStatus === 'COMPLETED' ? 'success' : 'warning'"
              />
            </div>
            <template v-if="state.currentStage === 'DAY2_RESULT_GUIDED'">
              <div class="teacher-finance-lesson">
                <p>학생 화면에 각자의 실제 결과와 함께 아래 설명이 표시됩니다. 순서대로 읽으며 진행해 주세요.</p>
                <ol>
                  <li>
                    <span>1</span>
                    <div><strong>예금은 약속한 이자를 받습니다.</strong><small>맡긴 원금과 받은 이자를 확인합니다.</small></div>
                  </li>
                  <li>
                    <span>2</span>
                    <div><strong>투자는 가격에 따라 손익이 달라집니다.</strong><small>위험과 기대 수익의 관계를 비교합니다.</small></div>
                  </li>
                  <li>
                    <span>3</span>
                    <div><strong>금융 수익에는 세금이 적용됩니다.</strong><small>과세 대상 수익과 실제로 남은 돈을 확인합니다.</small></div>
                  </li>
                </ol>
              </div>
              <dl class="day1-close-totals teacher-finance-lesson__totals">
                <div><dt>전체 예금 이자</dt><dd>+{{ formatBibim(day2FinancePreview.totalDepositInterest) }}</dd></div>
                <div><dt>전체 투자 손익</dt><dd>{{ day2FinancePreview.totalInvestmentProfit > 0 ? '+' : '' }}{{ formatBibim(day2FinancePreview.totalInvestmentProfit) }}</dd></div>
                <div><dt>금융세율</dt><dd>{{ day2FinancePreview.financialTaxBps / 100 }}%</dd></div>
                <div><dt>정산 후 지갑 합계</dt><dd>{{ formatBibim(day2FinancePreview.totalWalletAfter) }}</dd></div>
              </dl>
            </template>
            <template v-else>
              <dl class="day1-close-totals">
                <div><dt>예금 원금</dt><dd>{{ formatBibim(day2FinancePreview.totalDepositPrincipal) }}</dd></div>
                <div><dt>예금 이자</dt><dd>+{{ formatBibim(day2FinancePreview.totalDepositInterest) }}</dd></div>
                <div><dt>투자 손익</dt><dd>{{ day2FinancePreview.totalInvestmentProfit > 0 ? '+' : '' }}{{ formatBibim(day2FinancePreview.totalInvestmentProfit) }}</dd></div>
                <div><dt>금융세</dt><dd>−{{ formatBibim(day2FinancePreview.totalFinancialTax) }}</dd></div>
              </dl>
              <div class="day2-finance-summary-strip">
                <span>금융세율 {{ day2FinancePreview.financialTaxBps / 100 }}%</span>
                <strong>정산 후 학생 지갑 합계 {{ formatBibim(day2FinancePreview.totalWalletAfter) }}</strong>
              </div>
              <div class="day1-student-table" role="table" aria-label="학생별 2일차 금융 정산">
                <div class="day1-student-table__head" role="row">
                  <span>학생</span><span>정산 전</span><span>예금 이자</span><span>투자 손익</span><span>정산 후</span>
                </div>
                <div v-for="studentItem in day2FinancePreview.students" :key="studentItem.studentId" role="row">
                  <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
                  <span>{{ formatBibim(studentItem.walletBefore) }}</span>
                  <span>+{{ formatBibim(studentItem.depositInterest) }}</span>
                  <span>{{ studentItem.investmentProfit > 0 ? '+' : '' }}{{ formatBibim(studentItem.investmentProfit) }}</span>
                  <strong>{{ formatBibim(studentItem.walletAfter) }}</strong>
                </div>
              </div>
            </template>
            <p v-if="state.currentStage === 'DAY2_LOGIN'" class="control-caution">
              학생이 기존 참여 코드와 비밀번호로 다시 로그인하면 1일차 자산이 복구됩니다.
            </p>
            <p v-else-if="state.currentStage === 'DAY2_SETTLEMENT_WAIT'" class="control-caution">
              예상 결과를 확인했습니다. 다음 단계에서 금융 정산을 실행해 주세요.
            </p>
            <button
              v-else-if="state.currentStage === 'DAY2_FINANCE_SETTLEMENT'"
              class="settlement-action"
              type="button"
              :disabled="day2FinancePreview.settlementStatus === 'COMPLETED' || day2FinanceStore.settling"
              @click="showDay2SettlementConfirm = true"
            >
              {{ day2FinancePreview.settlementStatus === 'COMPLETED'
                ? '금융 정산 완료'
                : day2FinanceStore.settling ? '정산 중…' : `${day2FinancePreview.targetCount}명 금융 정산하기` }}
            </button>
            <p v-else class="control-caution">
              학생별 화면에 실제 결과를 바탕으로 예금, 투자 위험, 금융세 설명이 표시됩니다.
            </p>
          </div>
          <div
            v-else-if="state.currentStage === 'DAY2_QUIZ_SETTLEMENT' && day2QuizRewardPreview"
            class="day2-reward-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>경제 퀴즈</span>
                <strong>{{ day2QuizRewardPreview.submittedCount }} / {{ day2QuizRewardPreview.targetCount }}명 제출</strong>
              </div>
              <StatusBadge
                :label="day2QuizRewardPreview.settlementStatus === 'COMPLETED' ? '보상 완료' : '정산 대기'"
                :tone="day2QuizRewardPreview.settlementStatus === 'COMPLETED' ? 'success' : 'warning'"
              />
            </div>
            <div class="day2-reward-rule">
              <span>정답 1문제당 {{ formatBibim(day2QuizRewardPreview.correctRewardPerQuestion) }}</span>
              <span>정답수당 소득세 10%</span>
              <strong>예상 실제 지급액 {{ formatBibim(day2QuizRewardPreview.totalReward) }}</strong>
            </div>
            <p v-if="day2QuizRewardPreview.notSubmittedCount > 0" class="control-caution">
              미제출 학생 {{ day2QuizRewardPreview.notSubmittedCount }}명은 보상을 받지 않습니다.
            </p>
            <div class="day1-student-table day2-reward-table" role="table" aria-label="경제 퀴즈 보상 미리보기">
              <div class="day1-student-table__head" role="row">
                <span>학생</span><span>제출</span><span>정답</span><span>정답수당</span><span>소득세</span><span>실제 지급</span>
              </div>
              <div v-for="studentItem in day2QuizRewardPreview.students" :key="studentItem.studentId" role="row">
                <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
                <span>{{ studentItem.submitted ? '제출' : '미제출' }}</span>
                <span>{{ studentItem.correctCount }}개</span>
                <span>{{ formatBibim(studentItem.correctReward) }}</span>
                <span>−{{ formatBibim(studentItem.incomeTax) }}</span>
                <strong>{{ formatBibim(studentItem.totalReward) }}</strong>
              </div>
            </div>
            <button
              class="settlement-action"
              type="button"
              :disabled="day2QuizRewardPreview.settlementStatus === 'COMPLETED' || day2FlowStore.settlingQuizReward"
              @click="settleDay2QuizReward"
            >
              {{ day2QuizRewardPreview.settlementStatus === 'COMPLETED'
                ? '퀴즈 보상 정산 완료'
                : day2FlowStore.settlingQuizReward ? '정산 중…' : '퀴즈 보상 지급하기' }}
            </button>
          </div>
          <div
            v-else-if="[
              'DAY2_INFLATION_APPLY',
              'DAY2_FINAL_SHOP',
            ].includes(state.currentStage) && inflationPreview"
            class="inflation-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>인플레이션 체험</span>
                <strong>상품 가격 +{{ inflationPreview.inflationBps / 100 }}%</strong>
              </div>
              <StatusBadge
                :label="inflationPreview.applied ? '적용 완료' : '적용 전'"
                :tone="inflationPreview.applied ? 'success' : 'warning'"
              />
            </div>
            <div v-if="state.currentStage === 'DAY2_INFLATION_APPLY'" class="inflation-product-table" role="table" aria-label="상품 가격 상승 미리보기">
              <div role="row"><strong>상품</strong><strong>기존 가격</strong><strong>상승 후</strong><strong>증가액</strong></div>
              <div v-for="product in inflationPreview.products" :key="product.productId" role="row">
                <span>{{ product.name }}</span>
                <span>{{ formatBibim(product.originalPrice) }}</span>
                <strong>{{ formatBibim(product.priceAfterInflation) }}</strong>
                <span>+{{ formatBibim(product.increaseAmount) }}</span>
              </div>
            </div>
            <button
              v-if="state.currentStage === 'DAY2_INFLATION_APPLY'"
              class="settlement-action"
              type="button"
              :disabled="inflationPreview.applied || day2FlowStore.applyingInflation"
              @click="applyInflation"
            >
              {{ inflationPreview.applied
                ? '물가상승 적용 완료'
                : day2FlowStore.applyingInflation ? '가격 변경 중…' : '모든 상품에 물가상승 적용' }}
            </button>
            <div v-if="state.currentStage === 'DAY2_FINAL_SHOP' && shopSummary" class="teacher-product-list">
              <article v-for="product in shopSummary.products" :key="product.productId">
                <div><strong>{{ product.name }}</strong><span>{{ formatBibim(product.price) }}</span></div>
                <div><span>기존 {{ formatBibim(product.originalPrice) }}</span><strong>재고 {{ product.currentStock }}개</strong></div>
              </article>
            </div>
          </div>
          <div
            v-else-if="state.currentStage === 'DAY2_FUND_COST' && fundOverview"
            class="fund-control-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>우리 반 공동기금</span>
                <strong>{{ formatBibim(fundOverview.balance) }}</strong>
              </div>
              <StatusBadge
                :label="fundOverview.appliedExpense ? '공동비용 적용 완료' : fundVote ? '공동비용 미사용' : '비용 선택 전'"
                :tone="fundOverview.appliedExpense || fundVote ? 'success' : 'warning'"
              />
            </div>
            <dl class="fund-source-grid">
              <div><dt>소득세 적립</dt><dd>+{{ formatBibim(fundOverview.totalIncomeTax) }}</dd></div>
              <div><dt>금융세 적립</dt><dd>+{{ formatBibim(fundOverview.totalFinancialTax) }}</dd></div>
              <div><dt>공동비용</dt><dd>−{{ formatBibim(fundOverview.totalExpenses) }}</dd></div>
            </dl>
            <form v-if="!fundVote" class="fund-live-add" @submit.prevent="addClassFund">
              <div><strong>공동기금 실시간 추가</strong><small>추가 즉시 교사·학생 잔액에 반영됩니다.</small></div>
              <label>금액<input v-model.number="fundAdditionAmount" type="number" min="1" placeholder="예: 50"></label>
              <label>사유<input v-model.trim="fundAdditionReason" maxlength="100" placeholder="예: 학급 활동 지원금"></label>
              <button type="submit" :disabled="fundAdditionAmount < 1 || !fundAdditionReason.trim() || day2EndStore.mutating">추가</button>
            </form>
            <div class="fund-cost-option-grid">
              <article v-for="option in fundCostOptions" :key="option.optionId">
                <div>
                  <strong>{{ option.name }}</strong>
                  <span>{{ formatBibim(option.amount) }}</span>
                </div>
                <button
                  type="button"
                  :disabled="Boolean(fundOverview.appliedExpense) || Boolean(fundVote) || !option.affordable || day2EndStore.mutating"
                  @click="applyFundExpense(option.optionId)"
                >
                  {{ option.applied ? '적용 완료' : option.affordable ? '이 비용 적용' : '잔액 부족' }}
                </button>
              </article>
            </div>
            <p v-if="!fundOverview.appliedExpense && !fundVote" class="control-caution">
              공동비용을 선택하지 않고 기금을 그대로 보존한 채 학생 투표를 준비할 수도 있습니다.
            </p>
            <section v-if="!fundVote" class="fund-vote-builder" aria-labelledby="fund-vote-builder-title">
              <div class="fund-vote-builder__head">
                <div><strong id="fund-vote-builder-title">투표 항목 실시간 구성</strong><small>2~5개 항목을 직접 입력할 수 있습니다.</small></div>
                <button class="button-secondary" type="button" :disabled="day2EndStore.voteDraft.options.length >= 5" @click="day2EndStore.addVoteOption">+ 항목 추가</button>
              </div>
              <label>투표 질문<input v-model.trim="day2EndStore.voteDraft.question" maxlength="200"></label>
              <div class="fund-vote-builder__options">
                <article v-for="(option, index) in day2EndStore.voteDraft.options" :key="index">
                  <label>항목명<input v-model.trim="option.title" maxlength="100" placeholder="예: 학급 간식 시간"></label>
                  <label>설명<input v-model.trim="option.description" maxlength="255" placeholder="어떻게 사용할지 설명해 주세요"></label>
                  <label>필요 금액<input v-model.number="option.requiredAmount" type="number" min="0"></label>
                  <button class="button-secondary" type="button" :disabled="day2EndStore.voteDraft.options.length <= 2" @click="day2EndStore.removeVoteOption(index)">삭제</button>
                </article>
              </div>
            </section>
            <p v-else-if="!fundOverview.appliedExpense && fundVote" class="control-notice">
              공동비용을 사용하지 않기로 확정했습니다. 현재 공동기금 전액을 기준으로 투표가 준비되었습니다.
            </p>
            <button
              v-if="canPrepareFundVote"
              class="settlement-action teacher-stage-action"
              type="button"
              :disabled="Boolean(fundVote) || day2EndStore.mutating"
              @click="requestFundVotePreparation"
            >
              {{ fundVote
                ? '학생 투표 준비 완료'
                : fundOverview.appliedExpense ? '학생 투표 선택지 준비하기' : '공동비용 없이 투표 준비하기' }}
            </button>
          </div>
          <div
            v-else-if="state.currentStage === 'DAY2_FUND_VOTE' && fundOverview && fundVote"
            class="fund-control-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>익명 공동기금 투표</span>
                <strong>{{ fundVote.submittedCount }} / {{ fundVote.eligibleStudentCount }}명 참여</strong>
              </div>
              <StatusBadge
                :label="fundVote.status === 'READY' ? '시작 전' : fundVote.status === 'OPEN' ? '투표 중' : fundVote.status === 'CLOSED' ? '동점 결정 필요' : '결과 확정'"
                :tone="fundVote.status === 'DECIDED' ? 'success' : fundVote.status === 'OPEN' ? 'warning' : 'neutral'"
              />
            </div>
            <h3>{{ fundVote.question }}</h3>
            <p v-if="fundVote.status === 'OPEN'" class="control-caution">
              투표 중에는 선택지별 득표수를 공개하지 않습니다.
            </p>
            <div class="teacher-vote-options">
              <article
                v-for="option in fundVote.options"
                :key="option.optionId"
                :class="{ 'is-decided': option.decided, 'is-tied': fundVote.status === 'CLOSED' && option.tiedForFirst }"
              >
                <div>
                  <strong>{{ option.title }}</strong>
                  <span>{{ option.description }}</span>
                  <small>필요 {{ formatBibim(option.requiredAmount) }}</small>
                </div>
                <b v-if="option.voteCount !== null">{{ option.voteCount }}표</b>
                <button
                  v-if="fundVote.status === 'CLOSED' && option.tiedForFirst"
                  type="button"
                  :disabled="day2EndStore.mutating"
                  @click="decideFundVote(option.optionId)"
                >동점 최종 선택</button>
              </article>
            </div>
            <button
              v-if="fundVote.status === 'READY'"
              class="settlement-action teacher-stage-action"
              type="button"
              :disabled="day2EndStore.mutating"
              @click="controlFundVote('OPEN')"
            >투표 시작하기</button>
            <button
              v-else-if="fundVote.status === 'OPEN'"
              class="settlement-action teacher-stage-action"
              type="button"
              :disabled="day2EndStore.mutating"
              @click="requestFundVoteClose"
            >투표 마감하기</button>
          </div>
          <div
            v-else-if="state.currentStage === 'DAY2_FUND_RESULT' && fundVote"
            class="fund-control-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>공동기금 투표 결과</span>
                <strong>{{ decidedFundOption?.title ?? '최종 선택 결과' }}</strong>
              </div>
              <StatusBadge label="결과 공개" tone="success" />
            </div>
            <h3>{{ fundVote.question }}</h3>
            <p v-if="decidedFundOption" class="control-notice">
              {{ decidedFundOption.voteCount }}표를 받은 ‘{{ decidedFundOption.title }}’이 우리 반의 선택으로 확정되었습니다.
            </p>
            <div class="teacher-vote-options" aria-label="공동기금 투표 득표 결과">
              <article
                v-for="option in fundVote.options"
                :key="option.optionId"
                :class="{ 'is-decided': option.decided }"
              >
                <div>
                  <strong>{{ option.title }}</strong>
                  <span>{{ option.description }}</span>
                  <small>필요 {{ formatBibim(option.requiredAmount) }}</small>
                </div>
                <b>{{ option.voteCount ?? 0 }}표</b>
              </article>
            </div>
            <p class="control-caution">학생들과 결과를 함께 확인한 뒤 최종 결과 단계로 이동해 주세요.</p>
          </div>
          <div
            v-else-if="['DAY2_FINAL_RESULT', 'DAY2_SURVEY', 'DAY2_COMPLETED'].includes(state.currentStage) && finalSummary && productDistribution"
            class="final-control-workspace"
          >
            <div class="day1-close-workspace__headline">
              <div>
                <span>2일 경제활동 최종 결과</span>
                <strong>{{ finalSummary.studentCount }}명 · 지갑 합계 {{ formatBibim(finalSummary.totalFinalWalletBalance) }}</strong>
              </div>
              <StatusBadge
                :label="state.currentStage === 'DAY2_COMPLETED' ? '수업 종료' : '결과 공개'"
                :tone="state.currentStage === 'DAY2_COMPLETED' ? 'neutral' : 'success'"
              />
            </div>
            <dl class="final-class-summary">
              <div><dt>총소득</dt><dd>{{ formatBibim(finalSummary.totalGrossIncome) }}</dd></div>
              <div><dt>총세금</dt><dd>{{ formatBibim(finalSummary.totalIncomeTax + finalSummary.totalFinancialTax) }}</dd></div>
              <div><dt>총소비</dt><dd>{{ formatBibim(finalSummary.totalPurchaseAmount) }}</dd></div>
              <div><dt>공동기금 잔액</dt><dd>{{ formatBibim(finalSummary.classFundBalance) }}</dd></div>
            </dl>
            <div class="day1-student-table final-student-table" role="table" aria-label="학생별 최종 결과">
              <div class="day1-student-table__head" role="row">
                <span>학생</span><span>소득</span><span>세금</span><span>소비</span><span>최종 지갑</span>
              </div>
              <div v-for="studentItem in finalSummary.students" :key="studentItem.studentId" role="row">
                <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
                <span>{{ formatBibim(studentItem.grossIncome) }}</span>
                <span>{{ formatBibim(studentItem.totalTax) }}</span>
                <span>{{ formatBibim(studentItem.purchaseAmount) }}</span>
                <strong>{{ formatBibim(studentItem.finalWalletBalance) }}</strong>
              </div>
            </div>
            <section class="distribution-list">
              <h3>상품 배부 목록 · 총 {{ productDistribution.totalQuantity }}개</h3>
              <div v-if="productDistribution.items.length" class="distribution-days">
                <section
                  v-for="day in productDistributionByDay"
                  :key="day.dayNumber"
                  class="distribution-day"
                >
                  <header>
                    <h4>{{ day.dayNumber }}일차 구매</h4>
                    <strong>총 {{ day.totalQuantity }}개</strong>
                  </header>
                  <div v-if="day.students.length" class="distribution-students">
                    <article
                      v-for="studentItem in day.students"
                      :key="studentItem.studentId"
                      class="distribution-student"
                    >
                      <div class="distribution-student__header">
                        <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
                        <span>{{ studentItem.totalQuantity }}개</span>
                      </div>
                      <ul>
                        <li v-for="item in studentItem.items" :key="item.productId">
                          <strong>{{ item.productName }}</strong>
                          <span>× {{ item.quantity }}</span>
                        </li>
                      </ul>
                    </article>
                  </div>
                  <p v-else>이 날 배부할 상품이 없습니다.</p>
                </section>
              </div>
              <p v-else>배부할 상품이 없습니다.</p>
            </section>
            <section v-if="finalSummary.voteResult" class="final-vote-result">
              <p class="eyebrow">공동기금 투표 결과</p>
              <h3>{{ finalSummary.voteResult.title }}</h3>
              <p>{{ finalSummary.voteResult.voteCount }} / {{ finalSummary.voteResult.submittedCount }}표</p>
            </section>
            <a
              v-if="state.currentStage !== 'DAY2_FINAL_RESULT'"
              class="survey-link-button"
              :href="finalSummary.surveyUrl"
              target="_blank"
              rel="noopener noreferrer"
            >외부 설문 새 탭에서 열기 ↗</a>
            <button
              v-if="state.currentStage === 'DAY2_SURVEY'"
              class="settlement-action room-complete-button"
              type="button"
              :disabled="changingStage"
              @click="showRoomCompleteConfirm = true"
            >수업 종료하기</button>
          </div>
          <TeacherLearningMirror
            v-else-if="learningStages.has(state.currentStage) && guidedLearning"
            :learning="guidedLearning"
            @previous="moveLearningPage(-1)"
            @next="moveLearningPage(1)"
          />
          <FreeActivityIntroStageView
            v-else-if="state.currentStage === 'DAY1_FREE_ACTIVITY_GUIDED'"
            class="free-activity-intro--teacher"
            :active-view="state.guidedActivityView ?? 'HOME'"
            teacher-control
            @select="selectGuidedActivity"
          />
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
              ) || state.currentStage === 'DAY1_CLOSED' || state.currentStage === 'DAY2_LOGIN' || (
                state.currentStage === 'DAY2_FUND_COST' && (Boolean(fundOverview?.appliedExpense) || Boolean(fundVote))
              ) || (
                state.currentStage === 'DAY2_FUND_VOTE' && fundVote?.status !== 'READY'
              ) || state.currentStage === 'DAY2_FUND_RESULT' || state.currentStage === 'DAY2_COMPLETED'"
              @click="requestStageMove(adjacentStages.previous?.id)"
            >
              ← {{ adjacentStages.previous?.label ?? '이전 단계 없음' }}
            </button>
            <button
              type="button"
              :class="{ 'is-recommended': shouldHighlightStageAdvance }"
              :disabled="nextStageDisabled"
              @click="requestStageMove(adjacentStages.next?.id)"
            >
              {{ changingStage
                ? '변경 중…'
                : state.currentStage === 'DAY1_QUIZ_RESULT'
                  ? '퀴즈 결과 확인 완료 · 소득·세금 정산 →'
                  : state.currentStage === 'DAY2_QUIZ_RESULT'
                    ? '퀴즈 결과 확인 완료 · 보상 정산 →'
                  : state.currentStage === 'DAY1_FREE_ACTIVITY_GUIDED'
                    ? '활동 시작 · 학생 화면 열기 →'
                    : `${adjacentStages.next?.label ?? '다음 단계 없음'} →` }}
            </button>
          </div>
          <p class="control-caution">
            {{ ['DAY1_QUIZ', 'DAY2_QUIZ'].includes(state.currentStage)
              ? quizSummary?.status === 'CLOSED'
                ? '퀴즈가 마감되었습니다. 미제출 학생을 확인한 뒤 다음 단계로 이동합니다.'
                : '퀴즈를 시작하고 마감해야 다음 단계로 이동할 수 있습니다.'
              : state.currentStage === 'DAY1_QUIZ_RESULT'
                ? '학생들과 문항별 정답을 확인한 뒤 소득·세금 정산으로 이동해 주세요.'
              : state.currentStage === 'DAY2_QUIZ_RESULT'
                ? '학생들과 문항별 정답과 해설을 확인한 뒤 퀴즈 보상 정산으로 이동해 주세요.'
              : state.currentStage === 'DAY2_QUIZ_SETTLEMENT'
                ? '퀴즈 보상 정산을 완료하면 금융지식 교육 단계로 이동할 수 있습니다.'
              : state.currentStage === 'DAY1_INCOME_SETTLEMENT'
                ? '정산을 완료해야 월급명세서 단계로 이동할 수 있습니다.'
                : state.currentStage === 'DAY1_SUMMARY'
                  ? '집계 결과와 경고를 확인한 뒤 1일차 마감 버튼을 눌러 주세요.'
                  : state.currentStage === 'DAY1_CLOSED'
                    ? '2일차 시작 버튼을 누르면 새 세션이 열리고 기존 학생 계정과 자산이 복구됩니다.'
                    : state.currentStage === 'DAY2_FINANCE_SETTLEMENT'
                      ? '금융 정산을 완료해야 개인 결과 단계로 이동할 수 있습니다.'
                      : state.currentStage === 'DAY2_RESULT_GUIDED'
                        ? '학생과 함께 예금 이자, 투자 손익, 금융세를 확인해 주세요.'
                        : state.currentStage === 'DAY2_FUND_COST'
                          ? '공동비용을 검토하고 투표 선택지를 준비하면 다음 단계로 이동할 수 있습니다.'
                          : state.currentStage === 'DAY2_FUND_VOTE'
                            ? '투표를 마감하고 결과를 확정하면 공동기금 투표 결과 화면으로 이동할 수 있습니다.'
                            : state.currentStage === 'DAY2_FUND_RESULT'
                              ? '학생들과 공동기금 투표 결과를 확인한 뒤 최종 결과로 이동해 주세요.'
                            : state.currentStage === 'DAY2_SURVEY'
                              ? '설문 링크를 안내한 뒤 수업 종료 버튼으로 명시적으로 종료해 주세요.'
                              : state.currentStage === 'DAY2_COMPLETED'
                                ? '수업이 종료되었습니다. 결과 조회만 계속 사용할 수 있습니다.'
                : '현재 단계의 활동이 끝나면 다음 단계로 이동합니다.' }}
          </p>
        </section>

        <aside class="progress-panel" aria-label="학생 진행 현황">
          <div class="panel-heading">
            <p class="eyebrow">실시간 현황</p>
            <h2>{{ ['DAY1_QUIZ', 'DAY2_QUIZ'].includes(state.currentStage)
              ? '퀴즈 제출'
              : ['DAY1_QUIZ_RESULT', 'DAY2_QUIZ_RESULT'].includes(state.currentStage)
                ? '퀴즈 결과'
              : state.currentStage === 'DAY1_INCOME_SETTLEMENT'
                ? '정산 결과'
                : state.currentStage === 'DAY1_FREE_ACTIVITY'
                  ? '최근 구매'
                  : state.currentStage === 'DAY1_SUMMARY' || state.currentStage === 'DAY1_CLOSED'
                    ? '1일차 참여'
                    : [
                      'DAY2_LOGIN',
                      'DAY2_SETTLEMENT_WAIT',
                      'DAY2_FINANCE_SETTLEMENT',
                      'DAY2_RESULT_GUIDED',
                    ].includes(state.currentStage)
                      ? '금융 정산'
                      : ['DAY2_FUND_VOTE', 'DAY2_FUND_RESULT'].includes(state.currentStage)
                        ? state.currentStage === 'DAY2_FUND_RESULT' ? '투표 결과' : '익명 투표 참여'
                        : ['DAY2_FINAL_RESULT', 'DAY2_SURVEY', 'DAY2_COMPLETED'].includes(state.currentStage)
                          ? '최종 결과'
                          : '학생 연결' }}</h2>
          </div>
          <template v-if="[
            'DAY1_QUIZ',
            'DAY1_QUIZ_RESULT',
            'DAY2_QUIZ',
            'DAY2_QUIZ_RESULT',
          ].includes(state.currentStage) && quizSummary">
            <div class="student-count-ring student-count-ring--quiz">
              <strong>{{ connectedQuizCounts.submitted }}</strong>
              <span>/ {{ connectedQuizCounts.total }}명 제출</span>
            </div>
            <dl class="quiz-summary-counts">
              <div>
                <dt>풀이 중</dt>
                <dd>{{ connectedQuizCounts.inProgress }}명</dd>
              </div>
              <div>
                <dt>미시작</dt>
                <dd>{{ connectedQuizCounts.notStarted }}명</dd>
              </div>
            </dl>
            <p class="live-count-basis">현재 접속 중인 학생만 집계합니다.</p>
            <ul class="quiz-student-list" aria-label="학생별 퀴즈 현황">
              <li v-for="studentItem in connectedQuizCounts.students" :key="studentItem.studentId">
                <span>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</span>
                <strong v-if="studentItem.submissionStatus === 'SUBMITTED'">
                  제출 · {{ studentItem.correctCount }} / {{ quizSummary.questionCount }}
                </strong>
                <strong v-else-if="studentItem.submissionStatus === 'IN_PROGRESS'">풀이 중</strong>
                <strong v-else>미시작</strong>
              </li>
            </ul>
            <p v-if="connectedQuizCounts.total === 0" class="state-message">현재 접속 중인 학생이 없습니다.</p>
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
          <template v-else-if="state.currentStage === 'DAY1_FREE_ACTIVITY' && day1Summary">
            <div class="student-count-ring student-count-ring--shop">
              <strong>{{ connectedDay1Counts.active }}</strong>
              <span>/ {{ connectedDay1Counts.total }}명 활동 중</span>
            </div>
            <dl class="quiz-summary-counts">
              <div><dt>예금 확정</dt><dd>{{ connectedDay1Counts.depositConfirmed }}명</dd></div>
              <div><dt>투자 확정</dt><dd>{{ connectedDay1Counts.investmentConfirmed }}명</dd></div>
              <div><dt>구매 참여</dt><dd>{{ connectedDay1Counts.purchaseParticipants }}명</dd></div>
              <div><dt>선택 전</dt><dd>{{ connectedDay1Counts.inactive }}명</dd></div>
            </dl>
            <p class="live-count-basis">현재 접속 중인 학생만 집계합니다.</p>
          </template>
          <template
            v-else-if="(state.currentStage === 'DAY1_SUMMARY' || state.currentStage === 'DAY1_CLOSED') && day1Summary"
          >
            <div class="student-count-ring student-count-ring--day1">
              <strong>{{ day1Summary.studentCount - day1Summary.inactiveStudentCount }}</strong>
              <span>/ {{ day1Summary.studentCount }}명 활동</span>
            </div>
            <dl class="quiz-summary-counts">
              <div><dt>예금 확정</dt><dd>{{ day1Summary.depositConfirmedCount }}명</dd></div>
              <div><dt>투자 확정</dt><dd>{{ day1Summary.investmentConfirmedCount }}명</dd></div>
              <div><dt>구매 참여</dt><dd>{{ day1Summary.purchaseParticipantCount }}명</dd></div>
              <div><dt>미참여</dt><dd>{{ day1Summary.inactiveStudentCount }}명</dd></div>
            </dl>
          </template>
          <template
            v-else-if="[
              'DAY2_LOGIN',
              'DAY2_SETTLEMENT_WAIT',
              'DAY2_FINANCE_SETTLEMENT',
              'DAY2_RESULT_GUIDED',
            ].includes(state.currentStage) && day2FinancePreview"
          >
            <div class="student-count-ring student-count-ring--day2">
              <strong>{{ day2FinancePreview.targetCount }}</strong>
              <span>명 금융 결과</span>
            </div>
            <dl class="quiz-summary-counts">
              <div><dt>예금 이자</dt><dd>+{{ formatBibim(day2FinancePreview.totalDepositInterest) }}</dd></div>
              <div><dt>투자 손익</dt><dd>{{ day2FinancePreview.totalInvestmentProfit > 0 ? '+' : '' }}{{ formatBibim(day2FinancePreview.totalInvestmentProfit) }}</dd></div>
              <div><dt>금융세</dt><dd>{{ formatBibim(day2FinancePreview.totalFinancialTax) }}</dd></div>
              <div><dt>공동기금</dt><dd>{{ formatBibim(day2FinancePreview.classFundBalance) }}</dd></div>
            </dl>
          </template>
          <template v-else-if="['DAY2_FUND_VOTE', 'DAY2_FUND_RESULT'].includes(state.currentStage) && fundVote">
            <div class="student-count-ring student-count-ring--day2">
              <strong>{{ fundVote.submittedCount }}</strong>
              <span>/ {{ fundVote.eligibleStudentCount }}명 투표</span>
            </div>
            <dl class="quiz-summary-counts">
              <div><dt>상태</dt><dd>{{ state.currentStage === 'DAY2_FUND_RESULT' ? '결과 공개' : fundVote.status === 'READY' ? '시작 전' : fundVote.status === 'OPEN' ? '진행 중' : '마감' }}</dd></div>
              <div><dt>결과</dt><dd>{{ fundVote.status === 'DECIDED' ? '확정' : '대기' }}</dd></div>
            </dl>
            <p>개별 학생의 선택은 공개하지 않고 참여 인원만 표시합니다.</p>
          </template>
          <template v-else-if="['DAY2_FINAL_RESULT', 'DAY2_SURVEY', 'DAY2_COMPLETED'].includes(state.currentStage) && finalSummary">
            <div class="student-count-ring student-count-ring--day2">
              <strong>{{ finalSummary.studentCount }}</strong>
              <span>명 결과 집계</span>
            </div>
            <dl class="quiz-summary-counts">
              <div><dt>상품 배부</dt><dd>{{ productDistribution?.totalQuantity ?? 0 }}개</dd></div>
              <div><dt>공동기금</dt><dd>{{ formatBibim(finalSummary.classFundBalance) }}</dd></div>
            </dl>
          </template>
          <template v-else>
            <div class="student-count-ring">
              <strong>{{ state.connectedStudentCount }}</strong>
              <span>/ {{ state.expectedStudentCount }}명</span>
            </div>
            <p>로그인한 학생이 현재 수업 단계와 동기화됩니다.</p>
          </template>
          <button
            class="button-secondary participation-code-directory-trigger"
            type="button"
            @click="openParticipationCodes"
          >학생별 입장 코드 보기</button>
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

      <ParticipationCodeDialog
        v-if="showParticipationCodes"
        :students="preparation?.students ?? []"
        :loading="preparationLoading"
        :error-message="preparationError"
        :copied-code="copiedParticipationCode"
        @close="closeParticipationCodes"
        @refresh="refreshPreparation"
        @copy="copyParticipationCode"
      />

      <div v-if="showQuizCloseWarning && quizSummary" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="quiz-close-warning-title">
          <p class="eyebrow">미제출 학생 확인</p>
          <h2 id="quiz-close-warning-title">그래도 퀴즈를 마감할까요?</h2>
          <p class="confirm-modal__warning">
            아직 제출하지 않은 학생이 {{ incompleteQuizStudents.length }}명 있습니다.
          </p>
          <ul class="close-warning-list" aria-label="퀴즈 미제출 학생">
            <li v-for="studentItem in incompleteQuizStudents" :key="studentItem.studentId">
              <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
              <span>{{ studentItem.submissionStatus === 'IN_PROGRESS' ? '풀이 중' : '미시작' }}</span>
            </li>
          </ul>
          <p>
            강제로 마감하면 미제출 학생은 다음 화면으로 자동 이동하지 않습니다.
            개인 보충 퀴즈를 계속 풀 수 있으며, 지연 정답수당은 80%가 적용됩니다.
          </p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="showQuizCloseWarning = false">돌아가기</button>
            <button type="button" @click="confirmQuizClose">그래도 마감하기</button>
          </div>
        </section>
      </div>

      <div v-if="showVoteCloseWarning && fundVote" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="vote-close-warning-title">
          <p class="eyebrow">투표 미참여 학생 확인</p>
          <h2 id="vote-close-warning-title">그래도 공동기금 투표를 마감할까요?</h2>
          <p class="confirm-modal__warning">
            ACTIVE 학생 {{ fundVote.eligibleStudentCount }}명 중
            {{ fundVote.pendingStudents.length }}명이 아직 참여하지 않았습니다.
          </p>
          <ul class="close-warning-list" aria-label="공동기금 투표 미참여 학생">
            <li v-for="studentItem in fundVote.pendingStudents" :key="studentItem.studentId">
              <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname ?? '미설정' }}</strong>
              <span>미참여</span>
            </li>
          </ul>
          <p>강제로 마감하면 현재까지 제출된 표만으로 결과를 계산합니다.</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="showVoteCloseWarning = false">돌아가기</button>
            <button type="button" @click="confirmFundVoteClose">그래도 마감하기</button>
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

      <div v-if="showDay1CloseConfirm && day1ClosePreview" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="day1-close-confirm-title">
          <p class="eyebrow">1일차 최종 마감</p>
          <h2 id="day1-close-confirm-title">1일차 활동을 마감할까요?</h2>
          <p class="confirm-modal__warning">
            마감하면 확정된 예금·투자·구매 결과가 고정되고 새 1일차 거래를 만들 수 없습니다.
          </p>
          <p v-if="day1ClosePreview.depositDraftCount + day1ClosePreview.investmentDraftCount > 0">
            예금 초안 {{ day1ClosePreview.depositDraftCount }}건과 투자 초안
            {{ day1ClosePreview.investmentDraftCount }}건은 자동 취소됩니다.
          </p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="showDay1CloseConfirm = false">취소</button>
            <button type="button" :disabled="changingStage" @click="confirmDay1Close">마감 확정</button>
          </div>
        </section>
      </div>

      <div v-if="showDay2SettlementConfirm && day2FinancePreview" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="day2-finance-confirm-title">
          <p class="eyebrow">2일차 금융 정산</p>
          <h2 id="day2-finance-confirm-title">{{ day2FinancePreview.targetCount }}명의 예금과 투자를 정산할까요?</h2>
          <p class="confirm-modal__warning">
            예금 원금 {{ formatBibim(day2FinancePreview.totalDepositPrincipal) }},
            이자 {{ formatBibim(day2FinancePreview.totalDepositInterest) }},
            투자 손익 {{ formatBibim(day2FinancePreview.totalInvestmentProfit) }}을 반영합니다.
          </p>
          <p>
            이자와 양수인 투자 순이익에 금융세 {{ day2FinancePreview.financialTaxBps / 100 }}%가 적용되며,
            완료된 정산은 다시 실행되지 않습니다.
          </p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="showDay2SettlementConfirm = false">취소</button>
            <button type="button" :disabled="day2FinanceStore.settling" @click="confirmDay2FinanceSettlement">
              금융 정산 실행
            </button>
          </div>
        </section>
      </div>

      <div v-if="pendingStageTarget && pendingStageDefinition" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="direct-stage-move-title">
          <p class="eyebrow">단계 목록 이동</p>
          <h2 id="direct-stage-move-title">{{ pendingStageDefinition.label }} 단계로 바로 이동할까요?</h2>
          <p class="confirm-modal__warning">
            {{ pendingStageMovesForward
              ? `${pendingStageDistance}개의 중간 단계를 건너뛰며 학생 화면도 즉시 이동합니다.`
              : `${pendingStageDistance}개의 중간 단계를 건너 이전 화면으로 돌아갑니다.` }}
          </p>
          <p v-if="pendingStageMovesForward">건너뛴 활동은 자동 실행되지 않습니다. 필요한 정산과 확정 작업은 이동 전에 완료해야 합니다.</p>
          <p v-else>이미 완료한 거래·정산·투표 결과는 이전 단계로 이동해도 취소되지 않습니다.</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="pendingStageTarget = null">취소</button>
            <button type="button" :disabled="changingStage" @click="confirmStageMove">
              {{ changingStage ? '이동 중…' : '단계 이동' }}
            </button>
          </div>
        </section>
      </div>

      <div v-if="pendingLobbyExitTarget && pendingLobbyExitDefinition" class="modal-backdrop">
        <section class="confirm-modal lobby-start-warning" role="dialog" aria-modal="true" aria-labelledby="lobby-start-warning-title">
          <p class="eyebrow">학생 접속 확인</p>
          <h2 id="lobby-start-warning-title">아직 준비되지 않은 학생이 {{ lobbyWarningCount }}명 있습니다.</h2>
          <p class="confirm-modal__warning">
            지금 {{ pendingLobbyExitDefinition.label }} 단계로 이동하면 준비되지 않은 학생은 이후 보충 흐름으로 참여합니다.
          </p>
          <section v-if="lobbyAttendance.notJoined.length" class="lobby-start-warning__group">
            <h3>아직 입장하지 않음 · {{ lobbyAttendance.notJoined.length }}명</h3>
            <ul class="close-warning-list">
              <li v-for="studentItem in lobbyAttendance.notJoined" :key="studentItem.studentId">
                <strong>{{ studentItem.participantNumber }}번 학생</strong>
                <span>입장 전</span>
              </li>
            </ul>
          </section>
          <section v-if="lobbyAttendance.disconnected.length" class="lobby-start-warning__group">
            <h3>현재 연결 끊김 · {{ lobbyAttendance.disconnected.length }}명</h3>
            <ul class="close-warning-list">
              <li v-for="studentItem in lobbyAttendance.disconnected" :key="studentItem.studentId">
                <strong>{{ studentItem.participantNumber }}번 {{ studentItem.nickname || '학생' }}</strong>
                <span>오프라인</span>
              </li>
            </ul>
          </section>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="pendingLobbyExitTarget = null">입장 대기 계속하기</button>
            <button type="button" :disabled="changingStage" @click="confirmLobbyExit">
              {{ changingStage ? '이동 중…' : '그래도 시작하기' }}
            </button>
          </div>
        </section>
      </div>

      <div v-if="showFundVotePrepareConfirm && fundOverview" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="fund-vote-prepare-title">
          <p class="eyebrow">공동비용 미사용</p>
          <h2 id="fund-vote-prepare-title">공동비용 없이 투표를 준비할까요?</h2>
          <p class="confirm-modal__warning">
            공동기금 {{ formatBibim(fundOverview.balance) }}을 차감하지 않고 그대로 투표 기준 금액으로 사용합니다.
          </p>
          <p>투표 선택지를 준비한 뒤에는 공동비용을 다시 적용할 수 없습니다.</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" :disabled="day2EndStore.mutating" @click="showFundVotePrepareConfirm = false">
              돌아가기
            </button>
            <button type="button" :disabled="day2EndStore.mutating" @click="confirmFundVoteWithoutExpense">
              {{ day2EndStore.mutating ? '준비 중…' : '비용 없이 투표 준비' }}
            </button>
          </div>
        </section>
      </div>

      <div v-if="showRoomCompleteConfirm" class="modal-backdrop">
        <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="room-complete-title">
          <p class="eyebrow">2일 수업 최종 종료</p>
          <h2 id="room-complete-title">수업을 종료할까요?</h2>
          <p class="confirm-modal__warning">
            종료 후에는 구매·정산·투표·단계 변경이 모두 차단됩니다.
          </p>
          <p>학생과 교사는 새로고침하거나 다시 로그인해도 최종 결과를 계속 확인할 수 있습니다.</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" @click="showRoomCompleteConfirm = false">취소</button>
            <button type="button" :disabled="changingStage" @click="completeRoom">수업 종료 확정</button>
          </div>
        </section>
      </div>
    </template>
        </template>
      </div>
    </div>
  </main>
</template>
