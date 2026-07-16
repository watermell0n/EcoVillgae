import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  cancelDepositDraft,
  cancelInvestmentDraft,
  confirmDeposit,
  confirmInvestment,
  getCurrentDeposit,
  getCurrentInvestment,
  getEconomyOverview,
  getEconomyTransactions,
  saveDepositDraft,
  saveInvestmentDraft,
  type DepositState,
  type EconomyOverview,
  type EconomyTransaction,
  type InvestmentState,
} from '../features/economy/api/economy'

export const useEconomyStore = defineStore('economy', () => {
  const overview = ref<EconomyOverview | null>(null)
  const transactions = ref<EconomyTransaction[]>([])
  const deposit = ref<DepositState | null>(null)
  const investment = ref<InvestmentState | null>(null)
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

  async function refreshInvestment() {
    investment.value = await getCurrentInvestment()
    return investment.value
  }

  async function refreshAll() {
    loading.value = true
    try {
      await Promise.all([
        refreshOverview(),
        refreshTransactions(),
        refreshDeposit(),
        refreshInvestment(),
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
      await Promise.all([refreshOverview(), refreshTransactions()])
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
      await Promise.all([refreshOverview(), refreshTransactions()])
      return investment.value
    } finally {
      saving.value = false
    }
  }

  function clear() {
    overview.value = null
    transactions.value = []
    deposit.value = null
    investment.value = null
    depositKey = undefined
    investmentKey = undefined
    error.value = null
  }

  return {
    overview,
    transactions,
    deposit,
    investment,
    loading,
    saving,
    error,
    refreshOverview,
    refreshTransactions,
    refreshDeposit,
    refreshInvestment,
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
