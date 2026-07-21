<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import type { ShopProduct } from '../../../features/shop/api/shop'
import { getApiErrorCode, getApiErrorMessage } from '../../../shared/api/errors'
import { useEconomyStore } from '../../../stores/economy'
import { useRoomSessionStore } from '../../../stores/roomSession'
import { useShopStore } from '../../../stores/shop'

type View = 'SHOP' | 'KNOWLEDGE' | 'WALLET'

const shopStore = useShopStore()
const economyStore = useEconomyStore()
const roomSessionStore = useRoomSessionStore()
const activeView = ref<View>('SHOP')
const selectedProduct = ref<ShopProduct | null>(null)
const quantity = ref(1)
const loading = ref(true)
const errorMessage = ref('')
const noticeMessage = ref('')
const soldOutMessage = ref('')
const failedProductImageIds = ref(new Set<string>())
const SHOP_SYNC_INTERVAL_MS = 2_000
const PURCHASE_NOTICE_DURATION_MS = 3_000
let shopSyncTimer: ReturnType<typeof setInterval> | undefined
let shopSyncPromise: Promise<void> | null = null
let noticeTimer: ReturnType<typeof setTimeout> | undefined

const catalog = computed(() => shopStore.studentCatalog)
const overview = computed(() => economyStore.overview)
const totalPrice = computed(() => (selectedProduct.value?.price ?? 0) * quantity.value)
const maxPurchasableQuantity = computed(() => {
  const product = selectedProduct.value
  if (!product) return 0
  const walletBalance = catalog.value?.walletBalance ?? 0
  const walletLimit = product.price > 0
    ? Math.floor(walletBalance / product.price)
    : product.currentStock
  return Math.max(0, Math.min(product.currentStock, walletLimit))
})

onMounted(async () => {
  await refresh()
  if (activeView.value === 'SHOP') startShopSync()
})

async function refresh() {
  loading.value = true
  errorMessage.value = ''
  try {
    await Promise.all([
      shopStore.refreshStudentCatalog(),
      shopStore.refreshPurchaseHistory(),
      economyStore.refreshOverview(),
      economyStore.refreshTransactions(),
    ])
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '최종 상점 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  stopShopSync()
  clearNoticeTimer()
  shopStore.clearStudentShop()
  economyStore.clear()
})

watch(activeView, (view) => {
  if (view === 'SHOP') {
    startShopSync()
  } else {
    stopShopSync()
  }
})

async function syncShopAvailability() {
  if (shopSyncPromise) return shopSyncPromise
  shopSyncPromise = Promise.all([
    shopStore.refreshStudentCatalog(),
    economyStore.refreshOverview(),
  ]).then(() => undefined)
  try {
    await shopSyncPromise
  } finally {
    shopSyncPromise = null
  }
}

function startShopSync() {
  stopShopSync()
  shopSyncTimer = setInterval(() => {
    void syncShopAvailability().catch(() => undefined)
  }, SHOP_SYNC_INTERVAL_MS)
}

function stopShopSync() {
  if (shopSyncTimer !== undefined) {
    clearInterval(shopSyncTimer)
    shopSyncTimer = undefined
  }
}

function clearNoticeTimer() {
  if (noticeTimer !== undefined) {
    clearTimeout(noticeTimer)
    noticeTimer = undefined
  }
}

function showShopNotice(message: string) {
  clearNoticeTimer()
  noticeMessage.value = message
  noticeTimer = setTimeout(() => {
    noticeMessage.value = ''
    noticeTimer = undefined
  }, PURCHASE_NOTICE_DURATION_MS)
}

async function openPurchase(product: ShopProduct) {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await syncShopAvailability()
    const latestProduct = catalog.value?.products.find((item) => item.productId === product.productId)
    if (!latestProduct || latestProduct.status !== 'ON_SALE' || latestProduct.currentStock <= 0) {
      soldOutMessage.value = '다른 친구가 먼저 구매해 상품이 품절되었어요. 최신 재고를 확인해 주세요.'
      return
    }
    if (!canBuy(latestProduct)) {
      showShopNotice('사용 가능한 돈이 바뀌어 지금은 구매할 수 없어요.')
      return
    }
    selectedProduct.value = latestProduct
    quantity.value = 1
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '구매 가능한 상품을 다시 확인하지 못했습니다.')
  }
}

function closePurchase() {
  selectedProduct.value = null
  quantity.value = 1
}

function changeQuantity(amount: number) {
  const maximum = maxPurchasableQuantity.value
  if (!selectedProduct.value || maximum < 1) return
  quantity.value = Math.min(maximum, Math.max(1, quantity.value + amount))
}

async function confirmPurchase() {
  if (!selectedProduct.value) return
  try {
    await syncShopAvailability()
    const latestProduct = catalog.value?.products.find(
      (item) => item.productId === selectedProduct.value?.productId,
    )
    const latestWalletBalance = catalog.value?.walletBalance ?? 0
    if (!latestProduct || latestProduct.status !== 'ON_SALE' || latestProduct.currentStock < quantity.value) {
      soldOutMessage.value = '다른 친구가 먼저 구매해 상품이 품절되었어요. 최신 재고를 확인해 주세요.'
      closePurchase()
      return
    }
    if (latestProduct.price * quantity.value > latestWalletBalance) {
      showShopNotice('사용 가능한 돈이 바뀌어 구매할 수 없어요.')
      closePurchase()
      return
    }
    selectedProduct.value = latestProduct
    const result = await shopStore.buyProduct(selectedProduct.value.productId, quantity.value)
    if (!result) return
    showShopNotice(result.replayed
      ? '이미 완료된 구매 결과를 불러왔습니다.'
      : `${result.productName} ${result.quantity}개를 구매했습니다.`)
    closePurchase()
    await Promise.all([
      economyStore.refreshOverview(),
      economyStore.refreshTransactions(),
      roomSessionStore.refreshStudent(),
    ])
  } catch (error) {
    if (getApiErrorCode(error) === 'OUT_OF_STOCK') {
      soldOutMessage.value = '다른 친구가 먼저 구매해 상품이 품절됐어요. 최신 재고를 확인해 주세요.'
      closePurchase()
      await shopStore.refreshStudentCatalog()
      return
    }
    errorMessage.value = getApiErrorMessage(error, '상품을 구매하지 못했습니다.')
  }
}

function canBuy(product: ShopProduct) {
  return product.status === 'ON_SALE'
    && product.currentStock > 0
    && product.price <= (catalog.value?.walletBalance ?? 0)
}

function formatBibim(amount: number) {
  const normalized = Object.is(amount, -0) ? 0 : amount
  return `${normalized.toLocaleString('ko-KR')}비빔`
}

function markProductImageFailed(productId: string) {
  failedProductImageIds.value = new Set([...failedProductImageIds.value, productId])
}

function formatPurchasedAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="day2-final-shop-view">
    <p v-if="loading" class="state-message">최종 상점을 준비하고 있습니다.</p>
    <div v-else-if="errorMessage || !catalog || !overview" class="quiz-load-error" role="alert">
      <strong>최종 상점을 불러오지 못했어요.</strong>
      <p>{{ errorMessage || '필요한 상점 정보가 없습니다.' }}</p>
      <button type="button" @click="refresh">다시 불러오기</button>
    </div>
    <template v-else-if="catalog && overview">
      <header class="economy-header">
        <div><p class="eyebrow">2일차 최종 상점</p><h1>물가가 오른 뒤 무엇을 살 수 있을까요?</h1></div>
        <div class="shop-wallet"><span>사용 가능</span><strong>{{ formatBibim(overview.walletBalance) }}</strong></div>
      </header>
      <nav class="economy-module-tabs" aria-label="2일차 이용 메뉴">
        <button type="button" :class="{ 'is-active': activeView === 'SHOP' }" @click="activeView = 'SHOP'">상점</button>
        <button type="button" :class="{ 'is-active': activeView === 'KNOWLEDGE' }" @click="activeView = 'KNOWLEDGE'">금융지식</button>
        <button type="button" :class="{ 'is-active': activeView === 'WALLET' }" @click="activeView = 'WALLET'">지갑</button>
      </nav>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

      <div v-if="activeView === 'SHOP'">
        <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>
        <section class="shop-product-grid" aria-label="물가상승 적용 상품">
        <article v-for="product in catalog.products" :key="product.productId" class="shop-product-card">
          <img v-if="product.imageUrl && !failedProductImageIds.has(product.productId)" class="shop-product-card__image" :src="product.imageUrl" :alt="`${product.name} 상품 이미지`" @error="markProductImageFailed(product.productId)">
          <span v-else class="shop-product-card__emoji" aria-hidden="true">🛍️</span>
          <span class="shop-product-card__stock">재고 {{ product.currentStock }}개</span>
          <h2>{{ product.name }}</h2>
          <p>{{ product.description }}</p>
          <div class="inflated-price">
            <del v-if="product.originalPrice !== product.price">{{ formatBibim(product.originalPrice) }}</del>
            <strong>{{ formatBibim(product.price) }}</strong>
            <span v-if="product.originalPrice !== product.price">물가상승 반영</span>
          </div>
          <button type="button" :disabled="!canBuy(product) || shopStore.purchasing" @click="openPurchase(product)">
            {{ product.status === 'SOLD_OUT' ? '품절' : product.price > catalog.walletBalance ? '잔액 부족' : '구매하기' }}
          </button>
        </article>
        </section>
      </div>

      <section v-else-if="activeView === 'KNOWLEDGE'" class="day2-final-knowledge">
        <h2>오늘 배운 내용을 다시 확인해요</h2>
        <ul>
          <li><strong>금융세</strong><span>예금 이자나 투자 수익처럼 금융 활동으로 번 돈의 일부를 함께 모으는 세금이에요. 모인 금융세는 우리 반 공동기금에 더해져요.</span></li>
          <li><strong>인플레이션</strong><span>물건 가격이 전반적으로 오르면 같은 돈으로 살 수 있는 양이 줄어요. 그래서 가격뿐 아니라 내 돈의 구매력도 함께 비교해야 합니다.</span></li>
        </ul>
      </section>

      <section v-else class="wallet-panel">
        <div class="economy-balance-grid">
          <article><span>현재 지갑</span><strong>{{ formatBibim(overview.walletBalance) }}</strong></article>
          <article><span>총 구매</span><strong>{{ overview.purchaseCount }}건</strong></article>
        </div>
        <div class="transaction-list">
          <h2>최근 거래</h2>
          <article v-for="transaction in economyStore.transactions" :key="transaction.transactionId">
            <div><strong>{{ transaction.description }}</strong><small>{{ formatPurchasedAt(transaction.createdAt) }}</small></div>
            <strong :class="{ 'is-positive': transaction.walletAmount > 0 }">
              {{ transaction.walletAmount > 0 ? '+' : '' }}{{ formatBibim(transaction.walletAmount) }}
            </strong>
          </article>
        </div>
      </section>
    </template>

    <div v-if="selectedProduct && catalog" class="modal-backdrop">
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="day2-purchase-title">
        <p class="eyebrow">최종 구매 확인</p>
        <h2 id="day2-purchase-title">{{ selectedProduct.name }}을 구매할까요?</h2>
        <div class="shop-quantity">
          <button type="button" aria-label="수량 줄이기" :disabled="quantity <= 1" @click="changeQuantity(-1)">−</button>
          <strong>{{ quantity }}개</strong>
          <button type="button" aria-label="수량 늘리기" :disabled="quantity >= maxPurchasableQuantity" @click="changeQuantity(1)">+</button>
        </div>
        <p class="confirm-modal__warning">결제 {{ formatBibim(totalPrice) }} · 남은 돈 {{ formatBibim(catalog.walletBalance - totalPrice) }}</p>
        <div class="confirm-modal__actions">
          <button class="button-secondary" type="button" @click="closePurchase">취소</button>
          <button type="button" :disabled="shopStore.purchasing || totalPrice > catalog.walletBalance" @click="confirmPurchase">구매 확정</button>
        </div>
      </section>
    </div>

    <div v-if="soldOutMessage" class="modal-backdrop sold-out-backdrop">
      <section class="confirm-modal sold-out-modal" role="alertdialog" aria-modal="true" aria-labelledby="sold-out-title">
        <span class="sold-out-modal__icon" aria-hidden="true">🛒</span>
        <p class="eyebrow">재고 안내</p>
        <h2 id="sold-out-title">상품이 방금 품절됐어요</h2>
        <p>{{ soldOutMessage }}</p>
        <button type="button" @click="soldOutMessage = ''">확인</button>
      </section>
    </div>
  </div>
</template>
