import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  getStudentPurchaseHistory,
  getStudentShopProducts,
  getTeacherShopSummary,
  purchaseShopProduct,
  type PurchaseHistoryItem,
  type StudentShopCatalog,
  type TeacherShopSummary,
} from '../features/shop/api/shop'

const TEACHER_POLLING_INTERVAL_MS = 2_000

export const useShopStore = defineStore('shop', () => {
  const studentCatalog = ref<StudentShopCatalog | null>(null)
  const purchaseHistory = ref<PurchaseHistoryItem[]>([])
  const teacherSummary = ref<TeacherShopSummary | null>(null)
  const loadingStudentShop = ref(false)
  const purchasing = ref(false)
  const error = shallowRef<unknown>(null)

  let pendingIdempotencyKey: string | undefined
  let pendingPurchaseFingerprint: string | undefined
  let teacherPollingTimer: ReturnType<typeof setInterval> | undefined
  let teacherRequestInFlight = false

  async function refreshStudentCatalog() {
    loadingStudentShop.value = true
    try {
      const catalog = await getStudentShopProducts()
      studentCatalog.value = catalog
      error.value = null
      return catalog
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      loadingStudentShop.value = false
    }
  }

  async function refreshPurchaseHistory() {
    const history = await getStudentPurchaseHistory()
    purchaseHistory.value = history.purchases
    return history
  }

  async function buyProduct(productId: string, quantity: number) {
    if (purchasing.value) return undefined
    const fingerprint = `${productId}:${quantity}`
    if (pendingPurchaseFingerprint !== fingerprint) {
      pendingIdempotencyKey = undefined
      pendingPurchaseFingerprint = fingerprint
    }
    pendingIdempotencyKey ??=
      globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    purchasing.value = true
    try {
      const purchase = await purchaseShopProduct(productId, quantity, pendingIdempotencyKey)
      pendingIdempotencyKey = undefined
      pendingPurchaseFingerprint = undefined
      if (studentCatalog.value) {
        studentCatalog.value.walletBalance = purchase.walletBalance
        const product = studentCatalog.value.products.find(
          (item) => item.productId === purchase.productId,
        )
        if (product) {
          product.currentStock = purchase.remainingStock
          product.status = purchase.productStatus
        }
      }
      await refreshPurchaseHistory()
      error.value = null
      return purchase
    } catch (caughtError) {
      error.value = caughtError
      throw caughtError
    } finally {
      purchasing.value = false
    }
  }

  async function refreshTeacherSummary(roomId: string) {
    const summary = await getTeacherShopSummary(roomId)
    teacherSummary.value = summary
    error.value = null
    return summary
  }

  function startTeacherSummaryPolling(roomId: string) {
    stopTeacherSummaryPolling()
    teacherPollingTimer = setInterval(async () => {
      if (teacherRequestInFlight) return
      teacherRequestInFlight = true
      try {
        await refreshTeacherSummary(roomId)
      } catch (caughtError) {
        error.value = caughtError
      } finally {
        teacherRequestInFlight = false
      }
    }, TEACHER_POLLING_INTERVAL_MS)
  }

  function stopTeacherSummaryPolling() {
    if (teacherPollingTimer !== undefined) {
      clearInterval(teacherPollingTimer)
      teacherPollingTimer = undefined
    }
    teacherRequestInFlight = false
  }

  function clearStudentShop() {
    studentCatalog.value = null
    purchaseHistory.value = []
    pendingIdempotencyKey = undefined
    pendingPurchaseFingerprint = undefined
    error.value = null
  }

  function clearTeacherSummary() {
    teacherSummary.value = null
    error.value = null
  }

  return {
    studentCatalog,
    purchaseHistory,
    teacherSummary,
    loadingStudentShop,
    purchasing,
    error,
    refreshStudentCatalog,
    refreshPurchaseHistory,
    buyProduct,
    refreshTeacherSummary,
    startTeacherSummaryPolling,
    stopTeacherSummaryPolling,
    clearStudentShop,
    clearTeacherSummary,
  }
})
