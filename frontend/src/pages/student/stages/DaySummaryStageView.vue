<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { getApiErrorMessage } from '../../../shared/api/errors'
import { useDay1Store } from '../../../stores/day1'
import { useRoomSessionStore } from '../../../stores/roomSession'

const day1Store = useDay1Store()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const errorMessage = ref('')
const summary = computed(() => day1Store.studentSummary)

onMounted(() => void refresh())

watch(
  () => roomSessionStore.studentState?.syncVersion,
  (next, previous) => {
    if (previous !== undefined && next !== previous) void refresh()
  },
)

async function refresh() {
  try {
    errorMessage.value = ''
    await day1Store.refreshStudentSummary()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '1일차 결과를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <div class="day1-summary-view">
    <p v-if="loading" class="state-message">1일차 활동을 정리하고 있습니다.</p>
    <div v-else-if="errorMessage" class="quiz-load-error" role="alert">
      <strong>1일차 결과를 불러오지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
      <button type="button" @click="refresh">다시 불러오기</button>
    </div>
    <template v-else-if="summary">
      <header class="day1-result-header">
        <div class="stage-view__icon stage-view__icon--purple" aria-hidden="true">✓</div>
        <div>
          <p class="eyebrow">{{ summary.roomStage === 'DAY1_CLOSED' ? '1일차 마감 완료' : '1일차 요약' }}</p>
          <h1>{{ summary.nickname }}님의 경제활동 결과</h1>
          <p>{{ summary.roomStage === 'DAY1_CLOSED' ? '확정된 결과가 안전하게 저장되었습니다.' : '선생님이 마감하기 전 오늘의 선택을 확인해 보세요.' }}</p>
        </div>
      </header>

      <section v-if="summary.roomStage === 'DAY1_CLOSED'" class="day1-closed-banner" role="status">
        <span aria-hidden="true">🌙</span>
        <div><h2>1일차 활동이 모두 끝났어요.</h2><p>오늘의 지갑·예금·투자·구매 결과가 저장됐습니다. 다음 수업에서 이어서 만나요.</p></div>
      </section>

      <section class="day1-assets-grid" aria-label="1일차 자산 요약">
        <article><span>사용 가능 잔액</span><strong>{{ formatBibim(summary.walletBalance) }}</strong></article>
        <article><span>예금 원금</span><strong>{{ formatBibim(summary.depositPrincipal) }}</strong><small>예상 이자 {{ formatBibim(summary.expectedDepositInterest) }}</small></article>
        <article><span>투자 원금</span><strong>{{ formatBibim(summary.investmentPrincipal) }}</strong></article>
        <article><span>현재 총자산</span><strong>{{ formatBibim(summary.totalAssets) }}</strong></article>
      </section>

      <section v-if="summary.investments.length" class="day1-result-section">
        <div><p class="eyebrow">투자 배분</p><h2>내가 선택한 투자처</h2></div>
        <ul>
          <li v-for="item in summary.investments" :key="item.assetId"><span>{{ item.assetName }} · {{ item.riskLevel }}</span><strong>{{ formatBibim(item.amount) }}</strong></li>
        </ul>
      </section>

      <section v-if="summary.purchases.length" class="day1-result-section">
        <div><p class="eyebrow">소비 결과</p><h2>내가 구매한 상품</h2></div>
        <ul>
          <li v-for="item in summary.purchases" :key="item.purchaseId"><span>{{ item.productName }} · {{ item.quantity }}개</span><strong>{{ formatBibim(item.totalPrice) }}</strong></li>
        </ul>
        <p class="day1-purchase-total">총소비 {{ formatBibim(summary.purchaseTotal) }}</p>
      </section>
    </template>
  </div>
</template>
