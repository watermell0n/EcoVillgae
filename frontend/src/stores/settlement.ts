import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  getDay1IncomePreview,
  settleDay1Income,
  type Day1IncomePreview,
} from '../features/settlement/api/settlement'

export const useSettlementStore = defineStore('settlement', () => {
  const day1IncomePreview = ref<Day1IncomePreview | null>(null)
  const error = shallowRef<unknown>(null)
  const executing = ref(false)
  let pendingIdempotencyKey: string | undefined

  async function refreshDay1IncomePreview(roomId: string) {
    const preview = await getDay1IncomePreview(roomId)
    day1IncomePreview.value = preview
    error.value = null
    return preview
  }

  async function executeDay1IncomeSettlement(roomId: string) {
    if (executing.value) return undefined
    executing.value = true
    pendingIdempotencyKey ??=
      globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    try {
      const result = await settleDay1Income(roomId, pendingIdempotencyKey)
      day1IncomePreview.value = result.settlement
      pendingIdempotencyKey = undefined
      error.value = null
      return result
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      executing.value = false
    }
  }

  function clearDay1Income() {
    day1IncomePreview.value = null
    error.value = null
    pendingIdempotencyKey = undefined
  }

  return {
    day1IncomePreview,
    error,
    executing,
    refreshDay1IncomePreview,
    executeDay1IncomeSettlement,
    clearDay1Income,
  }
})
