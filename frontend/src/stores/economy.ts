import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  cancelDepositDraft,
  cancelInvestmentDraft,
  confirmDeposit,
  confirmInvestment,
  getCurrentDeposit,
  getCurrentInvestment,
  getDepositHistory,
  getEconomyOverview,
  getEconomyTransactions,
  getInvestmentHistory,
  saveDepositDraft,
  saveInvestmentDraft,
  type DepositState,
  type DepositHistoryItem,
  type EconomyOverview,
  type EconomyTransaction,
  type InvestmentState,
  type InvestmentHistoryItem,
} from '../features/economy/api/economy'

export const useEconomyStore = defineStore('economy', () => {
  const overview = ref<EconomyOverview | null>(null)
  const transactions = ref<EconomyTransaction[]>([])
  const deposit = ref<DepositState | null>(null)
  const depositHistory = ref<DepositHistoryItem[]>([])
  const investment = ref<InvestmentState | null>(null)
  const investmentHistory = ref<InvestmentHistoryItem[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const error = shallowRef<unknown>(null)
  let depositKey: string | undefined
  let investmentKey: string | undefined

  async function refreshOverview() {
    overview.value = await getEconomyOverview()
    return overview.value
  }

  async function refreshTransactions() {
    transactions.value = await getEconomyTransactions()
    return transactions.value
  }

  async function refreshDeposit() {
    deposit.value = await getCurrentDeposit()
    return deposit.value
  }

  async function refreshDepositHistory() {
    depositHistory.value = await getDepositHistory()
    return depositHistory.value
  }

  async function refreshInvestment() {
    investment.value = await getCurrentInvestment()
    return investment.value
  }

  async function refreshInvestmentHistory() {
    investmentHistory.value = await getInvestmentHistory()
    return investmentHistory.value
  }

  async function refreshAll() {
    loading.value = true
    try {
      await Promise.all([
        refreshOverview(),
        refreshTransactions(),
        refreshDeposit(),
        refreshDepositHistory(),
        refreshInvestment(),
        refreshInvestmentHistory(),
      ])
      error.value = null
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      loading.value = false
    }
  }

  async function updateDepositDraft(principal: number) {
    saving.value = true
    try {
      deposit.value = await saveDepositDraft(principal)
      depositKey = undefined
      await refreshOverview()
      return deposit.value
    } finally {
      saving.value = false
    }
  }

  async function removeDepositDraft() {
    saving.value = true
    try {
      deposit.value = await cancelDepositDraft()
      depositKey = undefined
      await refreshOverview()
      return deposit.value
    } finally {
      saving.value = false
    }
  }

  async function finalizeDeposit() {
    saving.value = true
    depositKey ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    try {
      deposit.value = await confirmDeposit(depositKey)
      depositKey = undefined
      await Promise.all([refreshOverview(), refreshTransactions(), refreshDepositHistory()])
      return deposit.value
    } finally {
      saving.value = false
    }
  }

  async function updateInvestmentDraft(allocations: Array<{ assetId: string; amount: number }>) {
    saving.value = true
    try {
      investment.value = await saveInvestmentDraft(allocations)
      investmentKey = undefined
      await refreshOverview()
      return investment.value
    } finally {
      saving.value = false
    }
  }

  async function removeInvestmentDraft() {
    saving.value = true
    try {
      investment.value = await cancelInvestmentDraft()
      investmentKey = undefined
      await refreshOverview()
      return investment.value
    } finally {
      saving.value = false
    }
  }

  async function finalizeInvestment() {
    saving.value = true
    investmentKey ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    try {
      investment.value = await confirmInvestment(investmentKey)
      investmentKey = undefined
      await Promise.all([refreshOverview(), refreshTransactions(), refreshInvestmentHistory()])
      return investment.value
    } finally {
      saving.value = false
    }
  }

  function clear() {
    overview.value = null
    transactions.value = []
    deposit.value = null
    depositHistory.value = []
    investment.value = null
    investmentHistory.value = []
    depositKey = undefined
    investmentKey = undefined
    error.value = null
  }

  return {
    overview,
    transactions,
    deposit,
    depositHistory,
    investment,
    investmentHistory,
    loading,
    saving,
    error,
    refreshOverview,
    refreshTransactions,
    refreshDeposit,
    refreshDepositHistory,
    refreshInvestment,
    refreshInvestmentHistory,
    refreshAll,
    updateDepositDraft,
    removeDepositDraft,
    finalizeDeposit,
    updateInvestmentDraft,
    removeInvestmentDraft,
    finalizeInvestment,
    clear,
  }
})
