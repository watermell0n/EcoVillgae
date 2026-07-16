<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

import type { ShopProduct } from '../../../features/shop/api/shop'
import { getApiErrorMessage } from '../../../shared/api/errors'
import { useLearningStore } from '../../../stores/learning'
import { useRoomSessionStore } from '../../../stores/roomSession'
import { useShopStore } from '../../../stores/shop'

const shopStore = useShopStore()
const learningStore = useLearningStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const errorMessage = ref('')
const noticeMessage = ref('')
const selectedProduct = ref<ShopProduct | null>(null)
const quantity = ref(1)
const reviewError = ref('')
const reviewLoading = ref(false)
const reviewPageIndex = ref(0)
const catalog = computed(() => shopStore.studentCatalog)
const totalPrice = computed(() => (selectedProduct.value?.price ?? 0) * quantity.value)
const reviewPage = computed(() => learningStore.selectedTopic?.pages[reviewPageIndex.value])

onMounted(async () => {
  try {
    await Promise.all([
      shopStore.refreshStudentCatalog(),
      shopStore.refreshPurchaseHistory(),
    ])
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '상점 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
  try {
    await learningStore.refreshTopics()
  } catch (error) {
    reviewError.value = getApiErrorMessage(error, '복습 주제를 불러오지 못했습니다.')
  }
})

onUnmounted(() => {
  shopStore.clearStudentShop()
  learningStore.clearReview()
})

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
    await roomSessionStore.refreshStudent()
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

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}

function formatPurchasedAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="shop-stage-view">
    <p v-if="loading" class="state-message">상점 문을 열고 있습니다.</p>
    <div v-else-if="errorMessage && !catalog" class="quiz-load-error" role="alert">
      <strong>상점을 불러오지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
    </div>
    <template v-else-if="catalog">
      <header class="shop-header">
        <div>
          <p class="eyebrow">자유 경제활동 · 상점</p>
          <h1>필요한 상품을 골라 보세요.</h1>
          <p>가격과 남은 재고를 확인한 뒤 신중하게 구매해요.</p>
        </div>
        <div class="shop-wallet" aria-label="현재 지갑 잔액">
          <span>내 지갑</span>
          <strong>{{ formatBibim(catalog.walletBalance) }}</strong>
        </div>
      </header>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

      <section class="shop-product-grid" aria-label="판매 상품">
        <article
          v-for="product in catalog.products"
          :key="product.productId"
          class="shop-product-card"
          :class="{ 'is-sold-out': product.status === 'SOLD_OUT' }"
        >
          <span class="shop-product-card__emoji" aria-hidden="true">{{ productEmoji(product) }}</span>
          <div>
            <span class="shop-product-card__stock">
              {{ product.status === 'SOLD_OUT' ? '품절' : `재고 ${product.currentStock}개` }}
            </span>
            <h2>{{ product.name }}</h2>
            <p>{{ product.description }}</p>
          </div>
          <footer>
            <strong>{{ formatBibim(product.price) }}</strong>
            <button
              type="button"
              :disabled="!canBuy(product) || shopStore.purchasing"
              @click="openPurchase(product)"
            >
              {{ product.status === 'SOLD_OUT'
                ? '품절'
                : product.price > catalog.walletBalance ? '잔액 부족' : '구매하기' }}
            </button>
          </footer>
        </article>
      </section>

      <section class="shop-history" aria-labelledby="shop-history-title">
        <div>
          <p class="eyebrow">구매 내역</p>
          <h2 id="shop-history-title">내가 산 상품</h2>
        </div>
        <p v-if="shopStore.purchaseHistory.length === 0" class="shop-history__empty">
          아직 구매한 상품이 없어요.
        </p>
        <ul v-else>
          <li v-for="purchase in shopStore.purchaseHistory" :key="purchase.purchaseId">
            <div>
              <strong>{{ purchase.productName }}</strong>
              <span>{{ formatPurchasedAt(purchase.purchasedAt) }} · {{ purchase.quantity }}개</span>
            </div>
            <strong>−{{ formatBibim(purchase.totalPrice) }}</strong>
          </li>
        </ul>
      </section>

      <section class="learning-review" aria-labelledby="learning-review-title">
        <div>
          <p class="eyebrow">자유 복습</p>
          <h2 id="learning-review-title">금융지식 다시 보기</h2>
          <p>궁금한 주제를 골라 내 속도로 다시 읽어 보세요.</p>
        </div>
        <p v-if="reviewError" class="form-error" role="alert">{{ reviewError }}</p>
        <div class="learning-topic-grid">
          <button
            v-for="topic in learningStore.topics"
            :key="topic.topicId"
            type="button"
            :disabled="reviewLoading"
            @click="openReview(topic.topicId)"
          >
            <strong>{{ topic.title }}</strong>
            <span>{{ topic.description }}</span>
          </button>
        </div>
        <article v-if="learningStore.selectedTopic && reviewPage" class="learning-review-card">
          <header>
            <div>
              <p class="eyebrow">{{ learningStore.selectedTopic.title }}</p>
              <h3>{{ reviewPage.title }}</h3>
            </div>
            <button class="button-secondary" type="button" @click="closeReview">닫기</button>
          </header>
          <p>{{ reviewPage.content }}</p>
          <ul>
            <li v-for="point in reviewPage.keyPoints" :key="point">{{ point }}</li>
          </ul>
          <footer>
            <button
              class="button-secondary"
              type="button"
              :disabled="reviewPageIndex === 0"
              @click="reviewPageIndex -= 1"
            >← 이전</button>
            <span>{{ reviewPageIndex + 1 }} / {{ learningStore.selectedTopic.pages.length }}</span>
            <button
              type="button"
              :disabled="reviewPageIndex === learningStore.selectedTopic.pages.length - 1"
              @click="reviewPageIndex += 1"
            >다음 →</button>
          </footer>
        </article>
      </section>
    </template>

    <div v-if="selectedProduct && catalog" class="modal-backdrop">
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="purchase-title">
        <p class="eyebrow">상품 구매 확인</p>
        <h2 id="purchase-title">{{ selectedProduct.name }} 상품을 구매할까요?</h2>
        <div class="shop-quantity">
          <button type="button" aria-label="수량 줄이기" :disabled="quantity <= 1" @click="changeQuantity(-1)">−</button>
          <strong>{{ quantity }}개</strong>
          <button
            type="button"
            aria-label="수량 늘리기"
            :disabled="quantity >= selectedProduct.currentStock"
            @click="changeQuantity(1)"
          >+</button>
        </div>
        <p class="confirm-modal__warning">
          결제 금액 {{ formatBibim(totalPrice) }} · 결제 후 {{ formatBibim(catalog.walletBalance - totalPrice) }}
        </p>
        <p>구매한 상품은 환불하거나 교환할 수 없어요. 마지막으로 수량과 금액을 확인해 주세요.</p>
        <div class="confirm-modal__actions">
          <button class="button-secondary" type="button" :disabled="shopStore.purchasing" @click="closePurchase">취소</button>
          <button
            type="button"
            :disabled="shopStore.purchasing || totalPrice > catalog.walletBalance"
            @click="confirmPurchase"
          >
            {{ shopStore.purchasing ? '결제 중…' : '구매 확정' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
