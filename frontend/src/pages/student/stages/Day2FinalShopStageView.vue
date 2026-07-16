<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

import type { ShopProduct } from '../../../features/shop/api/shop'
import { getApiErrorMessage } from '../../../shared/api/errors'
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

const catalog = computed(() => shopStore.studentCatalog)
const overview = computed(() => economyStore.overview)
const totalPrice = computed(() => (selectedProduct.value?.price ?? 0) * quantity.value)

onMounted(async () => {
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
})

onUnmounted(() => {
  shopStore.clearStudentShop()
  economyStore.clear()
})

function openPurchase(product: ShopProduct) {
  selectedProduct.value = product
  quantity.value = 1
}

function closePurchase() {
  selectedProduct.value = null
  quantity.value = 1
}

async function confirmPurchase() {
  if (!selectedProduct.value) return
  try {
    const result = await shopStore.buyProduct(selectedProduct.value.productId, quantity.value)
    if (!result) return
    noticeMessage.value = result.replayed
      ? '이미 완료된 구매 결과를 불러왔습니다.'
      : `${result.productName} ${result.quantity}개를 구매했습니다.`
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
  <div class="day2-final-shop-view">
    <p v-if="loading" class="state-message">최종 상점을 준비하고 있습니다.</p>
    <p v-else-if="errorMessage && !catalog" class="form-error" role="alert">{{ errorMessage }}</p>
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
      <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

      <section v-if="activeView === 'SHOP'" class="shop-product-grid" aria-label="물가상승 적용 상품">
        <article v-for="product in catalog.products" :key="product.productId" class="shop-product-card">
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

      <section v-else-if="activeView === 'KNOWLEDGE'" class="day2-final-knowledge">
        <h2>오늘 배운 내용을 다시 확인해요</h2>
        <ul>
          <li><strong>금융세</strong><span>금융 이익의 일부를 공동체를 위해 냅니다.</span></li>
          <li><strong>분산투자</strong><span>투자처를 나누어 한 곳의 손실 위험을 줄입니다.</span></li>
          <li><strong>인플레이션</strong><span>물가가 오르면 같은 돈의 구매력이 줄어듭니다.</span></li>
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
          <button type="button" :disabled="quantity <= 1" @click="quantity -= 1">−</button>
          <strong>{{ quantity }}개</strong>
          <button type="button" :disabled="quantity >= selectedProduct.currentStock" @click="quantity += 1">+</button>
        </div>
        <p class="confirm-modal__warning">결제 {{ formatBibim(totalPrice) }} · 남은 돈 {{ formatBibim(catalog.walletBalance - totalPrice) }}</p>
        <div class="confirm-modal__actions">
          <button class="button-secondary" type="button" @click="closePurchase">취소</button>
          <button type="button" :disabled="shopStore.purchasing || totalPrice > catalog.walletBalance" @click="confirmPurchase">구매 확정</button>
        </div>
      </section>
    </div>
  </div>
</template>
