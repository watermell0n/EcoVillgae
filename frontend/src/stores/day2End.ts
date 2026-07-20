import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  addTeacherFund,
  applyTeacherFundExpense,
  closeTeacherVote,
  createTeacherVote,
  decideTeacherVote,
  getTeacherCurrentVote,
  getTeacherFund,
  getTeacherFundCostOptions,
  openTeacherVote,
  type FundCostOption,
  type FundOverview,
  type VoteSession,
} from '../features/day2/api/fundVote'
import {
  getTeacherFinalSummary,
  getTeacherProductDistribution,
  type ProductDistribution,
  type TeacherFinalSummary,
} from '../features/day2/api/finalResult'

const DEFAULT_VOTE = {
  question: '남은 공동기금은 어떤 학급 활동에 사용할까요?',
  options: [
    { title: '학급 간식 시간', description: '함께 간식을 나누며 수업을 마무리해요.', requiredAmount: 40 },
    { title: '협동 놀이 활동', description: '모두가 참여하는 학급 놀이를 진행해요.', requiredAmount: 60 },
    { title: '다음 활동을 위해 저축', description: '남은 공동기금을 다음 공동 활동을 위해 남겨요.', requiredAmount: 0 },
  ],
}

export const useDay2EndStore = defineStore('day2End', () => {
  const fund = ref<FundOverview | null>(null)
  const costOptions = ref<FundCostOption[]>([])
  const vote = ref<VoteSession | null>(null)
  const finalSummary = ref<TeacherFinalSummary | null>(null)
  const distribution = ref<ProductDistribution | null>(null)
  const mutating = ref(false)
  const voteDraft = ref(structuredClone(DEFAULT_VOTE))
  const error = shallowRef<unknown>(null)
  let expenseKey: string | undefined
  let votePollingTimer: ReturnType<typeof setInterval> | undefined

  async function refreshFund(roomId: string) {
    const [nextFund, nextOptions] = await Promise.all([
      getTeacherFund(roomId),
      getTeacherFundCostOptions(roomId),
    ])
    fund.value = nextFund
    costOptions.value = nextOptions
    error.value = null
    return nextFund
  }

  async function applyExpense(roomId: string, optionId: string) {
    if (mutating.value) return undefined
    expenseKey ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    mutating.value = true
    try {
      const result = await applyTeacherFundExpense(roomId, optionId, expenseKey)
      fund.value = result.fund
      expenseKey = undefined
      await refreshFund(roomId)
      return result
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      mutating.value = false
    }
  }

  async function addFund(roomId: string, amount: number, reason: string) {
    if (mutating.value) return undefined
    mutating.value = true
    try {
      const key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
      fund.value = await addTeacherFund(roomId, amount, reason, key)
      await refreshFund(roomId)
      return fund.value
    } finally {
      mutating.value = false
    }
  }

  function addVoteOption() {
    if (voteDraft.value.options.length >= 5) return
    voteDraft.value.options.push({ title: '', description: '', requiredAmount: 0 })
  }

  function removeVoteOption(index: number) {
    if (voteDraft.value.options.length <= 2) return
    voteDraft.value.options.splice(index, 1)
  }

  async function prepareVote(roomId: string) {
    if (mutating.value) return undefined
    mutating.value = true
    try {
      vote.value = await createTeacherVote(roomId, voteDraft.value)
      return vote.value
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      mutating.value = false
    }
  }

  async function refreshVote(roomId: string) {
    vote.value = await getTeacherCurrentVote(roomId)
    error.value = null
    return vote.value
  }

  function startVotePolling(roomId: string) {
    stopVotePolling()
    votePollingTimer = setInterval(() => {
      void refreshVote(roomId).catch((caughtError) => {
        error.value = caughtError
      })
    }, 2_000)
  }

  function stopVotePolling() {
    if (votePollingTimer) clearInterval(votePollingTimer)
    votePollingTimer = undefined
  }

  async function updateVote(action: 'OPEN' | 'CLOSE', roomId: string, force = false) {
    if (!vote.value || mutating.value) return undefined
    mutating.value = true
    try {
      vote.value = action === 'OPEN'
        ? await openTeacherVote(roomId, vote.value.voteSessionId)
        : await closeTeacherVote(roomId, vote.value.voteSessionId, force)
      return vote.value
    } finally {
      mutating.value = false
    }
  }

  async function decideVote(roomId: string, optionId: string) {
    if (!vote.value || mutating.value) return undefined
    mutating.value = true
    try {
      vote.value = await decideTeacherVote(roomId, vote.value.voteSessionId, optionId)
      return vote.value
    } finally {
      mutating.value = false
    }
  }

  async function refreshFinal(roomId: string) {
    const [summary, products] = await Promise.all([
      getTeacherFinalSummary(roomId),
      getTeacherProductDistribution(roomId),
    ])
    finalSummary.value = summary
    distribution.value = products
    error.value = null
    return summary
  }

  function clear() {
    stopVotePolling()
    fund.value = null
    costOptions.value = []
    vote.value = null
    finalSummary.value = null
    distribution.value = null
    expenseKey = undefined
    error.value = null
  }

  return {
    fund,
    costOptions,
    vote,
    finalSummary,
    distribution,
    mutating,
    voteDraft,
    addFund,
    addVoteOption,
    removeVoteOption,
    error,
    refreshFund,
    applyExpense,
    prepareVote,
    refreshVote,
    startVotePolling,
    stopVotePolling,
    updateVote,
    decideVote,
    refreshFinal,
    clear,
  }
})
