<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'

import type { InvestmentAsset } from '../../../features/economy/api/economy'
import type { ShopProduct } from '../../../features/shop/api/shop'
import { getApiErrorMessage } from '../../../shared/api/errors'
import { useEconomyStore } from '../../../stores/economy'
import { useLearningStore } from '../../../stores/learning'
import { useRoomSessionStore } from '../../../stores/roomSession'
import { useShopStore } from '../../../stores/shop'

type ActivityView = 'HOME' | 'SHOP' | 'DEPOSIT' | 'INVESTMENT' | 'KNOWLEDGE' | 'WALLET'

const props = withDefaults(defineProps<{ initialView?: string }>(), { initialView: 'HOME' })

const activityCards: Array<{ id: ActivityView; icon: string; title: string; description: string }> = [
  { id: 'SHOP', icon: '🛒', title: '상점', description: '가격과 재고를 비교해 상품을 구매해요.' },
  { id: 'DEPOSIT', icon: '🏦', title: '예금', description: '돈을 맡기고 받을 이자를 확인해요.' },
  { id: 'INVESTMENT', icon: '📈', title: '투자', description: '위험을 비교하고 여러 곳에 나누어 투자해요.' },
  { id: 'KNOWLEDGE', icon: '📚', title: '금융지식', description: '배운 내용을 내 속도로 다시 읽어요.' },
  { id: 'WALLET', icon: '👛', title: '지갑', description: '내 잔액과 거래 내역을 확인해요.' },
]

const shopStore = useShopStore()
const economyStore = useEconomyStore()
const learningStore = useLearningStore()
const roomSessionStore = useRoomSessionStore()
const validViews: ActivityView[] = ['HOME', 'SHOP', 'DEPOSIT', 'INVESTMENT', 'KNOWLEDGE', 'WALLET']
const activeView = ref<ActivityView>(validViews.includes(props.initialView as ActivityView) ? props.initialView as ActivityView : 'HOME')
const viewHistory = ref<ActivityView[]>([])
const loading = ref(true)
const errorMessage = ref('')
const noticeMessage = ref('')
const selectedProduct = ref<ShopProduct | null>(null)
const selectedInvestmentAsset = ref<InvestmentAsset | null>(null)
const quantity = ref(1)
const reviewError = ref('')
const reviewLoading = ref(false)
const reviewPageIndex = ref(0)
const depositPrincipal = ref(0)
const allocationAmounts = reactive<Record<string, number>>({})

const catalog = computed(() => shopStore.studentCatalog)
const overview = computed(() => economyStore.overview)
const deposit = computed(() => economyStore.deposit)
const investment = computed(() => economyStore.investment)
const depositMinimum = computed(() => deposit.value?.minimumAmount ?? 20)
const depositUnit = computed(() => deposit.value?.amountUnit ?? 10)
const investmentMinimum = computed(() => investment.value?.minimumAmount ?? 10)
const investmentUnit = computed(() => investment.value?.amountUnit ?? 10)
const totalPrice = computed(() => (selectedProduct.value?.price ?? 0) * quantity.value)
const reviewPage = computed(() => learningStore.selectedTopic?.pages[reviewPageIndex.value])
const expectedDepositInterest = computed(() => Math.round(
  depositPrincipal.value * (deposit.value?.interestRateBps ?? 0) / 10_000,
))
const depositHistorySummary = computed(() => economyStore.depositHistory.reduce(
  (summary, item) => ({
    principal: summary.principal + item.principal,
    expectedInterest: summary.expectedInterest + item.expectedInterest,
    expectedMaturityAmount: summary.expectedMaturityAmount + item.expectedMaturityAmount,
  }),
  { principal: 0, expectedInterest: 0, expectedMaturityAmount: 0 },
))
const investmentHistorySummary = computed(() => {
  const byAsset = new Map<string, {
    assetId: string
    assetName: string
    riskLevel: string
    amount: number
  }>()

  for (const item of economyStore.investmentHistory) {
    for (const allocation of item.allocations) {
      const current = byAsset.get(allocation.assetId)
      byAsset.set(allocation.assetId, {
        assetId: allocation.assetId,
        assetName: allocation.assetName,
        riskLevel: allocation.riskLevel,
        amount: (current?.amount ?? 0) + allocation.amount,
      })
    }
  }

  const displayOrder = new Map(
    (investment.value?.assets ?? []).map((asset, index) => [asset.assetId, index]),
  )
  const assets = [...byAsset.values()].sort((left, right) =>
    (displayOrder.get(left.assetId) ?? Number.MAX_SAFE_INTEGER)
      - (displayOrder.get(right.assetId) ?? Number.MAX_SAFE_INTEGER),
  )

  return {
    assets,
    totalAmount: assets.reduce((sum, asset) => sum + asset.amount, 0),
  }
})
const selectedAllocations = computed(() =>
  Object.entries(allocationAmounts)
    .filter(([, amount]) => Number(amount) > 0)
    .map(([assetId, amount]) => ({ assetId, amount: Number(amount) })),
)
const investmentTotal = computed(() =>
  selectedAllocations.value.reduce((sum, item) => sum + item.amount, 0),
)
const adjustableWalletBalance = computed(() =>
  Math.floor(Math.max(overview.value?.walletBalance ?? 0, 0) / depositUnit.value) * depositUnit.value,
)
const canDecreaseDeposit = computed(() => depositPrincipal.value > depositMinimum.value)
const canIncreaseDeposit = computed(() => {
  const nextAmount = depositPrincipal.value < depositMinimum.value
    ? depositMinimum.value
    : depositPrincipal.value + depositUnit.value
  return nextAmount <= adjustableWalletBalance.value
})
const canSaveDeposit = computed(() =>
  depositPrincipal.value >= depositMinimum.value
    && depositPrincipal.value % depositUnit.value === 0
    && depositPrincipal.value <= (overview.value?.walletBalance ?? 0),
)
const canSaveInvestment = computed(() =>
  selectedAllocations.value.length > 0
    && selectedAllocations.value.length <= 3
    && selectedAllocations.value.every((item) =>
      item.amount >= investmentMinimum.value && item.amount % investmentUnit.value === 0)
    && investmentTotal.value <= (overview.value?.walletBalance ?? 0),
)

onMounted(async () => {
  try {
    await Promise.all([
      economyStore.refreshAll(),
      shopStore.refreshStudentCatalog(),
      shopStore.refreshPurchaseHistory(),
      learningStore.refreshTopics(),
    ])
    resetDepositPrincipal(deposit.value?.status === 'DRAFT' ? deposit.value.principal : undefined)
    resetInvestmentAllocations()
    const requestedActivity = new URLSearchParams(window.location.search).get('activity')?.toUpperCase()
    if (requestedActivity && ['SHOP', 'DEPOSIT', 'INVESTMENT', 'KNOWLEDGE', 'WALLET'].includes(requestedActivity)) {
      openActivity(requestedActivity as ActivityView)
    }
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '자유 경제활동 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  shopStore.clearStudentShop()
  economyStore.clear()
  learningStore.clearReview()
})

function openActivity(view: ActivityView, remember = true) {
  if (remember && view !== activeView.value) viewHistory.value.push(activeView.value)
  activeView.value = view
  selectedInvestmentAsset.value = null
  errorMessage.value = ''
  noticeMessage.value = ''
  if (view === 'DEPOSIT') resetDepositPrincipal(depositPrincipal.value)
  if (view === 'INVESTMENT') clampInvestmentAllocations()
}

function goBack() {
  const previous = viewHistory.value.pop()
  openActivity(previous ?? 'HOME', false)
}

function openInvestmentDetail(asset: InvestmentAsset) {
  selectedInvestmentAsset.value = asset
}

function closeInvestmentDetail() {
  selectedInvestmentAsset.value = null
}

function chooseInvestmentFromDetail() {
  const asset = selectedInvestmentAsset.value
  if (!asset) return
  if (investmentAmount(asset.assetId) === 0 && canIncreaseInvestment()) {
    allocationAmounts[asset.assetId] = investmentMinimum.value
  }
  closeInvestmentDetail()
}

function normalizedAmount(amount: number | undefined, unit = depositUnit.value) {
  if (!Number.isFinite(amount) || !amount || amount < 0) return 0
  return Math.floor(amount / unit) * unit
}

function resetDepositPrincipal(amount = depositMinimum.value) {
  if (adjustableWalletBalance.value < depositMinimum.value) {
    depositPrincipal.value = 0
    return
  }
  depositPrincipal.value = Math.min(
    adjustableWalletBalance.value,
    Math.max(depositMinimum.value, normalizedAmount(amount)),
  )
}

function changeDepositPrincipal(direction: -1 | 1) {
  if (direction < 0) {
    if (!canDecreaseDeposit.value) return
    depositPrincipal.value = Math.max(
      depositMinimum.value,
      depositPrincipal.value - depositUnit.value,
    )
    return
  }
  if (!canIncreaseDeposit.value) return
  depositPrincipal.value = depositPrincipal.value < depositMinimum.value
    ? depositMinimum.value
    : depositPrincipal.value + depositUnit.value
}

function investmentAmount(assetId: string) {
  return normalizedAmount(allocationAmounts[assetId], investmentUnit.value)
}

function canIncreaseInvestment() {
  return investmentTotal.value + investmentUnit.value <= (overview.value?.walletBalance ?? 0)
}

function changeInvestmentAmount(assetId: string, direction: -1 | 1) {
  const currentAmount = investmentAmount(assetId)
  if (direction < 0) {
    allocationAmounts[assetId] = Math.max(0, currentAmount - investmentUnit.value)
    return
  }
  if (!canIncreaseInvestment()) return
  allocationAmounts[assetId] = currentAmount === 0
    ? investmentMinimum.value
    : currentAmount + investmentUnit.value
}

function setInvestmentAmount(assetId: string, rawValue: string | number) {
  const parsed = typeof rawValue === 'number' ? rawValue : Number(rawValue)
  const currentAmount = investmentAmount(assetId)
  const otherInvestments = investmentTotal.value - currentAmount
  const availableForAsset = Math.max((overview.value?.walletBalance ?? 0) - otherInvestments, 0)
  const maximum = Math.floor(availableForAsset / investmentUnit.value) * investmentUnit.value
  const requested = normalizedAmount(parsed, investmentUnit.value)
  allocationAmounts[assetId] = Math.min(requested, maximum)
}

function resetInvestmentAllocations() {
  for (const asset of investment.value?.assets ?? []) {
    allocationAmounts[asset.assetId] = 0
  }
  if (investment.value?.status !== 'DRAFT') return

  let remainingBalance = adjustableWalletBalance.value
  for (const allocation of investment.value.allocations) {
    const amount = Math.min(normalizedAmount(allocation.amount, investmentUnit.value), remainingBalance)
    allocationAmounts[allocation.assetId] = amount
    remainingBalance -= amount
  }
}

function clampInvestmentAllocations() {
  let remainingBalance = adjustableWalletBalance.value
  for (const asset of investment.value?.assets ?? []) {
    const amount = Math.min(investmentAmount(asset.assetId), remainingBalance)
    allocationAmounts[asset.assetId] = amount
    remainingBalance -= amount
  }
}

async function saveDeposit() {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await economyStore.updateDepositDraft(depositPrincipal.value)
    noticeMessage.value = '예금 금액을 잠시 저장했어요. 확정 전에는 금액을 바꿀 수 있어요.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '예금 금액을 잠시 저장하지 못했습니다.')
  }
}

async function cancelDeposit() {
  try {
    await economyStore.removeDepositDraft()
    resetDepositPrincipal()
    noticeMessage.value = '잠시 저장한 예금 금액을 취소했어요.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '잠시 저장한 예금 금액을 취소하지 못했습니다.')
  }
}

async function confirmDeposit() {
  try {
    const result = await economyStore.finalizeDeposit()
    noticeMessage.value = result?.replayed
      ? '이미 완료된 예금 결과를 불러왔어요.'
      : '예금을 확정했어요. 원금이 예금 계정으로 이동했습니다.'
    resetDepositPrincipal()
    await roomSessionStore.refreshStudent()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '예금을 확정하지 못했습니다.')
  }
}

async function saveInvestment() {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await economyStore.updateInvestmentDraft(selectedAllocations.value)
    noticeMessage.value = '투자 금액을 잠시 저장했어요. 확정 전에 배분을 바꿀 수 있어요.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '투자 금액을 잠시 저장하지 못했습니다.')
  }
}

async function cancelInvestment() {
  try {
    await economyStore.removeInvestmentDraft()
    resetInvestmentAllocations()
    noticeMessage.value = '잠시 저장한 투자 금액을 취소했어요.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '잠시 저장한 투자 금액을 취소하지 못했습니다.')
  }
}

async function confirmInvestment() {
  try {
    const result = await economyStore.finalizeInvestment()
    noticeMessage.value = result?.replayed
      ? '이미 완료된 투자 결과를 불러왔어요.'
      : '투자를 확정했어요. 이제 금액과 시장 시나리오는 바뀌지 않아요.'
    resetInvestmentAllocations()
    await roomSessionStore.refreshStudent()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '투자를 확정하지 못했습니다.')
  }
}

async function openReview(topicId: string) {
  reviewLoading.value = true
  reviewError.value = ''
  reviewPageIndex.value = 0
  try {
    await learningStore.openTopic(topicId)
  } catch (error) {
    reviewError.value = getApiErrorMessage(error, '복습 내용을 불러오지 못했습니다.')
  } finally {
    reviewLoading.value = false
  }
}

function closeReview() {
  learningStore.selectedTopic = null
  reviewPageIndex.value = 0
}

function openPurchase(product: ShopProduct) {
  selectedProduct.value = product
  quantity.value = 1
  errorMessage.value = ''
  noticeMessage.value = ''
}

function closePurchase() {
  selectedProduct.value = null
  quantity.value = 1
}

function changeQuantity(amount: number) {
  if (!selectedProduct.value) return
  quantity.value = Math.min(
    selectedProduct.value.currentStock,
    Math.max(1, quantity.value + amount),
  )
}

async function confirmPurchase() {
  if (!selectedProduct.value) return
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const purchase = await shopStore.buyProduct(selectedProduct.value.productId, quantity.value)
    if (!purchase) return
    noticeMessage.value = purchase.replayed
      ? '이미 완료된 구매 결과를 불러왔어요.'
      : `${purchase.productName} ${purchase.quantity}개를 구매했어요!`
    closePurchase()
    await Promise.all([
      economyStore.refreshOverview(),
      economyStore.refreshTransactions(),
      roomSessionStore.refreshStudent(),
    ])
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '상품을 구매하지 못했습니다.')
  }
}

function canBuy(product: ShopProduct) {
  return product.status === 'ON_SALE'
    && product.currentStock > 0
    && product.price <= (catalog.value?.walletBalance ?? 0)
}

function productEmoji(product: ShopProduct) {
  return ['✏️', '🍪', '⭐'][product.displayOrder - 1] ?? '🎁'
}

function riskLabel(risk: string) {
  return { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }[risk] ?? risk
}

function investmentEmoji(asset: InvestmentAsset) {
  if (asset.riskLevel === 'LOW') return '🏭'
  if (asset.riskLevel === 'HIGH') return '🚢'
  return asset.name.includes('배터리') ? '🔋' : '🌿'
}

function transactionIcon(type: string) {
  return { DAY1_INCOME: '💰', PURCHASE: '🛒', DEPOSIT_CONFIRM: '🏦', INVESTMENT_CONFIRM: '📈' }[type] ?? '↔️'
}

function formatRate(bps: number) {
  const value = bps / 100
  return `${value > 0 ? '+' : ''}${value}%`
}

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}

function formatPurchasedAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="free-activity-stage-view">
    <p v-if="loading" class="state-message">자유 경제활동을 준비하고 있습니다.</p>
    <div v-else-if="errorMessage && !overview" class="quiz-load-error" role="alert">
      <strong>자유 경제활동 정보를 불러오지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
    </div>
    <template v-else-if="overview && catalog && investment && deposit">
      <div class="economy-sticky-controls">
      <header class="economy-header">
        <button v-if="activeView !== 'HOME'" class="button-secondary" type="button" @click="goBack">← 이전 화면</button>
        <div>
          <p class="eyebrow">1일차 자유 경제활동</p>
          <h1>{{ activeView === 'HOME' ? '내 비빔을 어떻게 사용할까요?' : activityCards.find((item) => item.id === activeView)?.title }}</h1>
        </div>
        <div class="shop-wallet" aria-label="현재 사용 가능 잔액">
          <span>사용 가능</span>
          <strong>{{ formatBibim(overview.walletBalance) }}</strong>
        </div>
      </header>

      <nav class="economy-module-tabs" aria-label="자유 경제활동 메뉴">
        <button type="button" :class="{ 'is-active': activeView === 'HOME' }" @click="openActivity('HOME')">홈</button>
        <button v-for="card in activityCards" :key="card.id" type="button" :class="{ 'is-active': activeView === card.id }" @click="openActivity(card.id)">{{ card.title }}</button>
      </nav>
      </div>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

      <section v-if="activeView === 'HOME'" class="economy-home">
        <div class="economy-balance-grid">
          <article><span>지갑</span><strong>{{ formatBibim(overview.walletBalance) }}</strong></article>
          <article><span>예금</span><strong>{{ formatBibim(overview.depositBalance) }}</strong></article>
          <article><span>투자</span><strong>{{ formatBibim(overview.investmentBalance) }}</strong></article>
          <article><span>구매</span><strong>{{ overview.purchaseCount }}건</strong></article>
        </div>
        <div class="economy-activity-grid">
          <button v-for="card in activityCards" :key="card.id" type="button" @click="openActivity(card.id)">
            <span aria-hidden="true">{{ card.icon }}</span>
            <strong>{{ card.title }}</strong>
            <small>{{ card.description }}</small>
          </button>
        </div>
      </section>

      <template v-else-if="activeView === 'SHOP'">
        <section class="shop-product-grid" aria-label="판매 상품">
          <article v-for="product in catalog.products" :key="product.productId" class="shop-product-card" :class="{ 'is-sold-out': product.status === 'SOLD_OUT' }">
            <img v-if="product.imageUrl" class="shop-product-card__image" :src="product.imageUrl" :alt="`${product.name} 상품 이미지`">
            <span v-else class="shop-product-card__emoji" aria-hidden="true">{{ productEmoji(product) }}</span>
            <div>
              <span class="shop-product-card__stock">{{ product.status === 'SOLD_OUT' ? '품절' : `재고 ${product.currentStock}개` }}</span>
              <h2>{{ product.name }}</h2>
              <p>{{ product.description }}</p>
            </div>
            <footer>
              <strong>{{ formatBibim(product.price) }}</strong>
              <button type="button" :disabled="!canBuy(product) || shopStore.purchasing" @click="openPurchase(product)">{{ product.status === 'SOLD_OUT' ? '품절' : product.price > catalog.walletBalance ? '잔액 부족' : '구매하기' }}</button>
            </footer>
          </article>
        </section>
        <section class="shop-history" aria-labelledby="shop-history-title">
          <div><p class="eyebrow">구매 내역</p><h2 id="shop-history-title">내가 산 상품</h2></div>
          <p v-if="shopStore.purchaseHistory.length === 0" class="shop-history__empty">아직 구매한 상품이 없어요.</p>
          <ul v-else>
            <li v-for="purchase in shopStore.purchaseHistory" :key="purchase.purchaseId">
              <div><strong>{{ purchase.productName }}</strong><span>{{ formatPurchasedAt(purchase.purchasedAt) }} · {{ purchase.quantity }}개</span></div>
              <strong>−{{ formatBibim(purchase.totalPrice) }}</strong>
            </li>
          </ul>
        </section>
      </template>

      <section v-else-if="activeView === 'DEPOSIT'" class="finance-activity-panel">
        <div class="finance-guide-card">
          <p class="eyebrow">약속 이자율 5%</p>
          <h2>기다린 만큼 이자를 받아요.</h2>
          <p>20비빔 이상을 10비빔 단위로 맡길 수 있어요. 확정한 건은 취소할 수 없지만, 남은 비빔으로 새 예금을 추가할 수 있어요.</p>
        </div>
        <div class="finance-form-card">
          <span id="deposit-principal-label" class="amount-stepper-label">맡길 금액</span>
          <div class="amount-stepper" role="group" aria-labelledby="deposit-principal-label">
            <button
              class="button-secondary"
              type="button"
              aria-label="예금 금액 10비빔 줄이기"
              :disabled="!canDecreaseDeposit || economyStore.saving"
              @click="changeDepositPrincipal(-1)"
            >−10</button>
            <output id="deposit-principal" class="amount-stepper__value" aria-live="polite">{{ formatBibim(depositPrincipal) }}</output>
            <button
              type="button"
              aria-label="예금 금액 10비빔 늘리기"
              :disabled="!canIncreaseDeposit || economyStore.saving"
              @click="changeDepositPrincipal(1)"
            >+10</button>
          </div>
          <dl>
            <div><dt>예상 이자</dt><dd>{{ formatBibim(expectedDepositInterest) }}</dd></div>
            <div><dt>만기 예상 금액</dt><dd>{{ formatBibim(depositPrincipal + expectedDepositInterest) }}</dd></div>
            <div><dt>확정 후 지갑</dt><dd>{{ formatBibim(overview.walletBalance - depositPrincipal) }}</dd></div>
          </dl>
          <div class="finance-actions">
            <button class="button-secondary" type="button" :disabled="deposit.status !== 'DRAFT' || economyStore.saving" @click="cancelDeposit">임시저장 취소</button>
            <button type="button" :disabled="!canSaveDeposit || economyStore.saving" @click="saveDeposit">잠시 저장</button>
            <button type="button" :disabled="deposit.status !== 'DRAFT' || economyStore.saving" @click="confirmDeposit">예금 확정</button>
          </div>
          <p v-if="overview.depositBalance > 0" class="finance-confirmed">✓ 지금까지 {{ formatBibim(overview.depositBalance) }}을 예금했어요. 남은 잔액으로 추가 예금할 수 있어요.</p>
        </div>
        <section class="finance-history-card" aria-labelledby="deposit-history-title">
          <div class="finance-history-card__header">
            <div><p class="eyebrow">확정 내역</p><h2 id="deposit-history-title">내가 맡긴 예금</h2></div>
            <strong>{{ economyStore.depositHistory.length }}건</strong>
          </div>
          <p v-if="economyStore.depositHistory.length === 0" class="finance-history-card__empty">아직 확정한 예금이 없어요.</p>
          <div v-else class="finance-history-card__scroll" tabindex="0" aria-label="예금 확정 내역 목록">
            <ol class="finance-history-list">
              <li v-for="item in economyStore.depositHistory" :key="item.depositId">
                <div class="finance-history-list__title">
                  <strong>{{ formatBibim(item.principal) }} 예금</strong>
                  <time :datetime="item.confirmedAt">{{ formatPurchasedAt(item.confirmedAt) }}</time>
                </div>
                <dl>
                  <div><dt>약속 이자율</dt><dd>{{ formatRate(item.interestRateBps) }}</dd></div>
                  <div><dt>예상 이자</dt><dd>+{{ formatBibim(item.expectedInterest) }}</dd></div>
                  <div><dt>만기 예상</dt><dd>{{ formatBibim(item.expectedMaturityAmount) }}</dd></div>
                </dl>
              </li>
            </ol>
          </div>
          <section v-if="economyStore.depositHistory.length > 0" class="finance-history-summary" aria-labelledby="deposit-summary-title">
            <div class="finance-history-summary__header">
              <div><p class="eyebrow">누적 합계</p><h3 id="deposit-summary-title">예금 전체 보기</h3></div>
              <span>확정 금액 기준</span>
            </div>
            <dl class="finance-history-summary__totals">
              <div><dt>총예금액</dt><dd>{{ formatBibim(depositHistorySummary.principal) }}</dd></div>
              <div><dt>예상 이자</dt><dd>+{{ formatBibim(depositHistorySummary.expectedInterest) }}</dd></div>
              <div><dt>만기 예상</dt><dd>{{ formatBibim(depositHistorySummary.expectedMaturityAmount) }}</dd></div>
            </dl>
          </section>
        </section>
      </section>

      <section v-else-if="activeView === 'INVESTMENT'" class="finance-activity-panel investment-screen">
        <header class="investment-screen__intro">
          <div>
            <span class="investment-screen__title-icon" aria-hidden="true">📈</span>
            <div><h2>투자 은행</h2><p>다양한 회사에 투자해 보고, 미래 가치를 예측하며 똑똑한 투자자가 되어 봐요!</p></div>
          </div>
          <aside class="investment-screen__balance"><span>보유 투자금</span><strong>🪙 {{ formatBibim(overview.walletBalance) }}</strong></aside>
        </header>
        <div class="investment-screen__section-title"><span aria-hidden="true">🌱</span><strong>투자 가능한 상품</strong><small>ⓘ</small></div>
        <div class="investment-asset-grid">
          <article
            v-for="asset in investment.assets"
            :key="asset.assetId"
            class="investment-asset-card"
            @click="openInvestmentDetail(asset)"
          >
            <span class="investment-asset-card__icon" aria-hidden="true">{{ investmentEmoji(asset) }}</span>
            <div class="investment-asset-card__header">
              <span :class="`risk-badge risk-badge--${asset.riskLevel.toLowerCase()}`">위험 {{ riskLabel(asset.riskLevel) }}</span>
              <button
                class="investment-asset-card__detail-trigger"
                type="button"
                :aria-label="`${asset.name} 기업 정보 보기`"
                @click.stop="openInvestmentDetail(asset)"
              >투자 이야기 자세히 보기</button>
            </div>
            <h3>{{ asset.name }}</h3>
            <p>{{ asset.description }}</p>
            <small><strong>예상 결과</strong> {{ formatRate(asset.minReturnBps) }} ~ {{ formatRate(asset.maxReturnBps) }}</small>
            <span :id="`investment-${asset.assetId}-label`" class="amount-stepper-label">투자 금액</span>
            <div class="amount-spinner" role="group" :aria-labelledby="`investment-${asset.assetId}-label`" @click.stop @keydown.stop>
              <input
                :id="`investment-${asset.assetId}-input`"
                class="amount-spinner__input"
                type="number"
                inputmode="numeric"
                min="0"
                :step="investmentUnit"
                :value="investmentAmount(asset.assetId)"
                :aria-label="`${asset.name} 투자 금액 직접 입력`"
                :disabled="economyStore.saving"
                @change="setInvestmentAmount(asset.assetId, ($event.target as HTMLInputElement).value)"
                @blur="setInvestmentAmount(asset.assetId, ($event.target as HTMLInputElement).value)"
              >
              <span class="amount-spinner__unit">비빔</span>
              <div class="amount-spinner__buttons" aria-hidden="false">
                <button
                  type="button"
                  :aria-label="`${asset.name} 투자 금액 10비빔 늘리기`"
                  :disabled="!canIncreaseInvestment() || economyStore.saving"
                  @click="changeInvestmentAmount(asset.assetId, 1)"
                ><span aria-hidden="true">+10</span></button>
                <button
                  type="button"
                  :aria-label="`${asset.name} 투자 금액 10비빔 줄이기`"
                  :disabled="investmentAmount(asset.assetId) === 0 || economyStore.saving"
                  @click="changeInvestmentAmount(asset.assetId, -1)"
                ><span aria-hidden="true">−10</span></button>
              </div>
            </div>
          </article>
        </div>
        <div class="investment-total-card investment-screen__checkout">
          <div><span>총투자금</span><strong>{{ formatBibim(investmentTotal) }}</strong></div>
          <div><span>투자 후 지갑</span><strong>{{ formatBibim(overview.walletBalance - investmentTotal) }}</strong></div>
          <div class="finance-actions">
            <button class="button-secondary" type="button" :disabled="investment.status !== 'DRAFT' || economyStore.saving" @click="cancelInvestment">임시저장 취소</button>
            <button type="button" :disabled="!canSaveInvestment || economyStore.saving" @click="saveInvestment">잠시 저장</button>
            <button type="button" :disabled="investment.status !== 'DRAFT' || economyStore.saving" @click="confirmInvestment">투자 확정</button>
          </div>
          <p v-if="overview.investmentBalance > 0" class="finance-confirmed">✓ 지금까지 {{ formatBibim(overview.investmentBalance) }}을 투자했어요. 남은 잔액으로 추가 투자할 수 있어요.</p>
        </div>
        <p class="investment-screen__note">💡 예상 결과는 과거 데이터와 시장 상황을 바탕으로 한 예측 범위예요.</p>
        <section class="finance-history-card investment-screen__history" aria-labelledby="investment-history-title">
          <div class="finance-history-card__header">
            <div><p class="eyebrow">확정 내역</p><h2 id="investment-history-title">내가 선택한 투자처</h2></div>
            <strong>{{ economyStore.investmentHistory.length }}건</strong>
          </div>
          <p v-if="economyStore.investmentHistory.length === 0" class="finance-history-card__empty">아직 확정한 투자가 없어요.</p>
          <div v-else class="finance-history-card__scroll" tabindex="0" aria-label="투자 확정 내역 목록">
            <ol class="finance-history-list">
              <li v-for="item in economyStore.investmentHistory" :key="item.orderId">
                <div class="finance-history-list__title">
                  <strong>총 {{ formatBibim(item.totalAmount) }}</strong>
                  <time :datetime="item.confirmedAt">{{ formatPurchasedAt(item.confirmedAt) }}</time>
                </div>
                <ul class="investment-history-allocations">
                  <li v-for="allocation in item.allocations" :key="allocation.assetId">
                    <span :class="`risk-badge risk-badge--${allocation.riskLevel.toLowerCase()}`">{{ riskLabel(allocation.riskLevel) }}</span>
                    <strong>{{ allocation.assetName }}</strong>
                    <span>{{ formatBibim(allocation.amount) }}</span>
                  </li>
                </ul>
              </li>
            </ol>
          </div>
          <section v-if="economyStore.investmentHistory.length > 0" class="finance-history-summary" aria-labelledby="investment-summary-title">
            <div class="finance-history-summary__header">
              <div><p class="eyebrow">누적 합계</p><h3 id="investment-summary-title">종목별 투자금</h3></div>
              <span>확정 금액 기준</span>
            </div>
            <ul class="investment-history-summary-list">
              <li v-for="asset in investmentHistorySummary.assets" :key="asset.assetId">
                <span :class="`risk-badge risk-badge--${asset.riskLevel.toLowerCase()}`">{{ riskLabel(asset.riskLevel) }}</span>
                <strong>{{ asset.assetName }}</strong>
                <span>{{ formatBibim(asset.amount) }}</span>
              </li>
            </ul>
            <div class="investment-history-summary-total"><span>전체 투자금</span><strong>{{ formatBibim(investmentHistorySummary.totalAmount) }}</strong></div>
          </section>
        </section>
      </section>

      <section v-else-if="activeView === 'KNOWLEDGE'" class="learning-review" aria-labelledby="learning-review-title">
        <div><p class="eyebrow">자유 복습</p><h2 id="learning-review-title">금융지식 다시 보기</h2><p>궁금한 주제를 골라 내 속도로 다시 읽어 보세요.</p></div>
        <p v-if="reviewError" class="form-error" role="alert">{{ reviewError }}</p>
        <div class="learning-topic-grid">
          <button v-for="topic in learningStore.topics" :key="topic.topicId" type="button" :disabled="reviewLoading" @click="openReview(topic.topicId)"><strong>{{ topic.title }}</strong><span>{{ topic.description }}</span></button>
        </div>
        <article v-if="learningStore.selectedTopic && reviewPage" class="learning-review-card">
          <header><div><p class="eyebrow">{{ learningStore.selectedTopic.title }}</p><h3>{{ reviewPage.title }}</h3></div><button class="button-secondary" type="button" @click="closeReview">닫기</button></header>
          <p>{{ reviewPage.content }}</p><ul v-if="reviewPage.keyPoints.length"><li v-for="point in reviewPage.keyPoints" :key="point">{{ point }}</li></ul>
          <p v-if="reviewPage.example" class="learning-review-card__example"><strong>📌 예시</strong> {{ reviewPage.example }}</p>
          <footer><button class="button-secondary" type="button" :disabled="reviewPageIndex === 0" @click="reviewPageIndex -= 1">← 이전</button><span>{{ reviewPageIndex + 1 }} / {{ learningStore.selectedTopic.pages.length }}</span><button type="button" :disabled="reviewPageIndex === learningStore.selectedTopic.pages.length - 1" @click="reviewPageIndex += 1">다음 →</button></footer>
        </article>
      </section>

      <section v-else-if="activeView === 'WALLET'" class="wallet-panel">
        <div class="economy-balance-grid">
          <article><span>사용 가능 잔액</span><strong>{{ formatBibim(overview.walletBalance) }}</strong></article>
          <article><span>예금 원금</span><strong>{{ formatBibim(overview.depositBalance) }}</strong></article>
          <article><span>투자 원금</span><strong>{{ formatBibim(overview.investmentBalance) }}</strong></article>
          <article><span>총자산</span><strong>{{ formatBibim(overview.totalAssets) }}</strong></article>
        </div>
        <div class="transaction-list">
          <h2>내 거래 내역</h2>
          <p v-if="economyStore.transactions.length === 0">아직 거래 내역이 없습니다.</p>
          <article v-for="transaction in economyStore.transactions" :key="transaction.transactionId">
            <span aria-hidden="true">{{ transactionIcon(transaction.transactionType) }}</span>
            <div><strong>{{ transaction.description }}</strong><small>{{ formatPurchasedAt(transaction.createdAt) }}</small></div>
            <strong :class="{ 'is-positive': transaction.walletAmount > 0 }">{{ transaction.walletAmount > 0 ? '+' : '' }}{{ formatBibim(transaction.walletAmount) }}</strong>
          </article>
        </div>
      </section>
    </template>

    <div v-if="selectedProduct && catalog" class="modal-backdrop">
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="purchase-title">
        <p class="eyebrow">상품 구매 확인</p><h2 id="purchase-title">{{ selectedProduct.name }} 상품을 구매할까요?</h2>
        <div class="shop-quantity"><button type="button" aria-label="수량 줄이기" :disabled="quantity <= 1" @click="changeQuantity(-1)">−</button><strong>{{ quantity }}개</strong><button type="button" aria-label="수량 늘리기" :disabled="quantity >= selectedProduct.currentStock" @click="changeQuantity(1)">+</button></div>
        <p class="confirm-modal__warning">결제 금액 {{ formatBibim(totalPrice) }} · 결제 후 {{ formatBibim(catalog.walletBalance - totalPrice) }}</p>
        <p>구매한 상품은 환불하거나 교환할 수 없어요. 마지막으로 수량과 금액을 확인해 주세요.</p>
        <div class="confirm-modal__actions"><button class="button-secondary" type="button" :disabled="shopStore.purchasing" @click="closePurchase">취소</button><button type="button" :disabled="shopStore.purchasing || totalPrice > catalog.walletBalance" @click="confirmPurchase">{{ shopStore.purchasing ? '결제 중…' : '구매 확정' }}</button></div>
      </section>
    </div>

    <div v-if="selectedInvestmentAsset" class="modal-backdrop investment-detail-backdrop" @click.self="closeInvestmentDetail">
      <section
        class="investment-detail-modal"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`investment-detail-${selectedInvestmentAsset.assetId}`"
      >
        <header>
          <span class="investment-detail-modal__icon" aria-hidden="true">{{ investmentEmoji(selectedInvestmentAsset) }}</span>
          <div>
            <h2 :id="`investment-detail-${selectedInvestmentAsset.assetId}`">{{ selectedInvestmentAsset.name }}</h2>
          </div>
          <button class="investment-detail-modal__close" type="button" aria-label="기업 정보 닫기" @click="closeInvestmentDetail">×</button>
        </header>
        <div class="investment-detail-modal__meta">
          <span :class="`risk-badge risk-badge--${selectedInvestmentAsset.riskLevel.toLowerCase()}`">위험 {{ riskLabel(selectedInvestmentAsset.riskLevel) }}</span>
          <strong>예상 결과 {{ formatRate(selectedInvestmentAsset.minReturnBps) }} ~ {{ formatRate(selectedInvestmentAsset.maxReturnBps) }}</strong>
        </div>
        <p class="investment-detail-modal__summary">{{ selectedInvestmentAsset.description }}</p>
        <section class="investment-detail-modal__story">
          <h3>📘 투자 이야기</h3>
          <p class="investment-detail-modal__content">{{ selectedInvestmentAsset.companyInfo }}</p>
          <h3>📊 근거가 되는 정보</h3>
          <ul>
            <li>예상 수익 범위: {{ formatRate(selectedInvestmentAsset.minReturnBps) }} ~ {{ formatRate(selectedInvestmentAsset.maxReturnBps) }}</li>
            <li>위험 수준: {{ riskLabel(selectedInvestmentAsset.riskLevel) }}</li>
            <li>{{ selectedInvestmentAsset.description }}</li>
          </ul>
        </section>
        <footer class="investment-detail-modal__actions">
          <button class="button-secondary" type="button" @click="closeInvestmentDetail">닫기</button>
          <button type="button" :disabled="!canIncreaseInvestment() && investmentAmount(selectedInvestmentAsset.assetId) === 0" @click="chooseInvestmentFromDetail">이 상품에 투자하기</button>
        </footer>
      </section>
    </div>
  </div>
</template>
